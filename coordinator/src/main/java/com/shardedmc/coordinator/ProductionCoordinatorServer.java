package com.shardedmc.coordinator;

import com.shardedmc.shared.config.ConfigLoader;
import com.shardedmc.shared.config.CoordinatorConfig;
import com.shardedmc.shared.health.HealthCheck;
import com.shardedmc.shared.logging.StructuredLogger;
import com.shardedmc.shared.metrics.MetricsCollector;
import com.shardedmc.shared.resilience.CircuitBreaker;
import com.shardedmc.shared.resilience.RetryPolicy;
import com.shardedmc.shared.security.SecurityManager;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProductionCoordinatorServer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionCoordinatorServer.class);
    
    private final CoordinatorConfig config;
    private final ConfigLoader configLoader;
    private final StructuredLogger structuredLogger;
    private final MetricsCollector metrics;
    private final HealthCheck healthCheck;
    private final SecurityManager securityManager;
    private final CircuitBreaker redisCircuitBreaker;
    private final RetryPolicy redisRetryPolicy;
    
    private Server grpcServer;
    private Javalin restServer;
    private Javalin metricsServer;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    private final Map<String, ShardInfo> registeredShards = new ConcurrentHashMap<>();
    private final Map<String, ChunkAllocation> chunkAllocations = new ConcurrentHashMap<>();
    
    public ProductionCoordinatorServer() {
        this.configLoader = new ConfigLoader("config/coordinator");
        this.config = configLoader.load("coordinator", CoordinatorConfig.class);
        this.structuredLogger = new StructuredLogger(ProductionCoordinatorServer.class);
        this.structuredLogger.addDefaultContext("service", "coordinator");
        this.metrics = new MetricsCollector("coordinator", "main");
        this.healthCheck = new HealthCheck();
        this.securityManager = new SecurityManager();
        
        this.redisCircuitBreaker = new CircuitBreaker("redis", 5, Duration.ofSeconds(30), 3);
        this.redisRetryPolicy = new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(5), 2.0);
        
        setupHealthChecks();
        
        structuredLogger.info("ProductionCoordinatorServer initialized", 
            Map.of("grpc_port", String.valueOf(config.getGrpcPort()),
                   "rest_port", String.valueOf(config.getRestPort())));
    }
    
    private void setupHealthChecks() {
        healthCheck.registerIndicator("grpc", () -> grpcServer != null && !grpcServer.isShutdown() 
            ? HealthCheck.HealthStatus.HEALTHY 
            : HealthCheck.HealthStatus.UNHEALTHY);
        
        healthCheck.registerIndicator("rest", () -> restServer != null 
            ? HealthCheck.HealthStatus.HEALTHY 
            : HealthCheck.HealthStatus.UNHEALTHY);
        
        healthCheck.registerIndicator("shards", () -> {
            long healthyShards = registeredShards.values().stream()
                .filter(s -> s.isHealthy()).count();
            return healthyShards > 0 ? HealthCheck.HealthStatus.HEALTHY : HealthCheck.HealthStatus.DEGRADED;
        });
    }
    
    public void start() throws IOException {
        structuredLogger.logEvent("server_starting", "Starting production coordinator server", null);
        
        // Start gRPC server
        grpcServer = ServerBuilder.forPort(config.getGrpcPort())
            .addService(new ProductionCoordinatorService(this))
            .build()
            .start();
        
        structuredLogger.info("gRPC server started on port " + config.getGrpcPort());
        metrics.incrementCounter("server.start", "type", "grpc");
        
        // Start REST API with Javalin
        restServer = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(config.getRestPort());
        setupRestRoutes(restServer);
        
        structuredLogger.info("REST server started on port " + config.getRestPort());
        metrics.incrementCounter("server.start", "type", "rest");
        
        // Start metrics endpoint
        if (config.isMetricsEnabled()) {
            metricsServer = Javalin.create(config -> {
                config.showJavalinBanner = false;
            }).start(config.getMetricsPort());
            
            metricsServer.get("/metrics", ctx -> {
                ctx.contentType("text/plain")
                   .result(metrics.scrape());
            });
            metricsServer.get("/health", ctx -> {
                HealthCheck.HealthReport report = healthCheck.generateReport();
                int statusCode = report.isHealthy() ? 200 : 503;
                ctx.status(statusCode)
                   .contentType("application/json")
                   .result(generateHealthJson(report));
            });
            
            structuredLogger.info("Metrics server started on port " + config.getMetricsPort());
        }
        
        // Start health checks
        healthCheck.start(10);
        
        // Start periodic tasks
        scheduler.scheduleAtFixedRate(this::cleanupExpiredTokens, 1, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::rebalanceChunks, 
            config.getChunkRebalanceIntervalMs(), 
            config.getChunkRebalanceIntervalMs(), 
            TimeUnit.MILLISECONDS);
        
        // JVM metrics
        scheduler.scheduleAtFixedRate(metrics::recordMemoryUsage, 10, 10, TimeUnit.SECONDS);
        
        structuredLogger.logEvent("server_started", "Production coordinator server fully started", null);
    }
    
    private void setupRestRoutes(Javalin app) {
        // Shard management
        app.get("/api/v1/shards", ctx -> {
            metrics.incrementCounter("http.request", "method", "GET", "path", "/shards");
            ctx.contentType("application/json")
               .result(generateShardsJson());
        });
        
        app.get("/api/v1/shards/{id}", ctx -> {
            String shardId = ctx.pathParam("id");
            ShardInfo shard = registeredShards.get(shardId);
            if (shard != null) {
                ctx.contentType("application/json")
                   .result(generateShardJson(shard));
            } else {
                ctx.status(404).result("{\"error\":\"Shard not found\"}");
            }
        });
        
        // Chunk allocation
        app.get("/api/v1/chunks/{x}/{z}", ctx -> {
            int x = Integer.parseInt(ctx.pathParam("x"));
            int z = Integer.parseInt(ctx.pathParam("z"));
            String shardId = getShardForChunk(x, z);
            ctx.contentType("application/json")
               .result("{\"chunk_x\":" + x + ",\"chunk_z\":" + z + ",\"shard_id\":\"" + shardId + "\"}");
        });
        
        // Player management
        app.post("/api/v1/players/{id}/migrate", ctx -> {
            String playerId = ctx.pathParam("id");
            String targetShard = ctx.bodyAsClass(Map.class).get("target_shard").toString();
            ctx.contentType("application/json")
               .result("{\"player_id\":\"" + playerId + "\",\"status\":\"migrating\",\"target_shard\":\"" + targetShard + "\"}");
        });
    }
    
    private String generateHealthJson(HealthCheck.HealthReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"").append(report.getOverallStatus().name().toLowerCase()).append("\",");
        sb.append("\"timestamp\":\"").append(report.getCheckTime()).append("\",");
        sb.append("\"indicators\":{");
        
        boolean first = true;
        for (Map.Entry<String, HealthCheck.HealthIndicator> entry : report.getIndicators().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"");
            sb.append(entry.getValue().getLastStatus().name().toLowerCase()).append("\"");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }
    
    private String generateShardsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"count\":").append(registeredShards.size()).append(",");
        sb.append("\"shards\":[");
        
        boolean first = true;
        for (ShardInfo shard : registeredShards.values()) {
            if (!first) sb.append(",");
            sb.append(generateShardJson(shard));
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }
    
    private String generateShardJson(ShardInfo shard) {
        return "{\"id\":\"" + shard.getId() + "\"," +
               "\"host\":\"" + shard.getHost() + "\"," +
               "\"port\":" + shard.getPort() + "," +
               "\"healthy\":" + shard.isHealthy() + "," +
               "\"player_count\":" + shard.getPlayerCount() + "," +
               "\"chunk_count\":" + shard.getChunkCount() + "," +
               "\"last_heartbeat\":\"" + shard.getLastHeartbeat() + "\"}";
    }
    
    private String getShardForChunk(int x, int z) {
        String key = x + "," + z;
        ChunkAllocation allocation = chunkAllocations.get(key);
        return allocation != null ? allocation.getShardId() : "unknown";
    }
    
    private void cleanupExpiredTokens() {
        securityManager.cleanupExpiredTokens();
    }
    
    private void rebalanceChunks() {
        structuredLogger.debug("Running chunk rebalancing");
        // TODO: Implement chunk rebalancing logic
    }
    
    public void stop() {
        structuredLogger.logEvent("server_stopping", "Stopping production coordinator server", null);
        
        healthCheck.stop();
        scheduler.shutdown();
        
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        if (restServer != null) {
            restServer.stop();
        }
        if (metricsServer != null) {
            metricsServer.stop();
        }
        
        metrics.shutdown();
        structuredLogger.logEvent("server_stopped", "Production coordinator server stopped", null);
    }
    
    public void awaitTermination() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }
    
    // Getters for service
    public CoordinatorConfig getConfig() { return config; }
    public MetricsCollector getMetrics() { return metrics; }
    public HealthCheck getHealthCheck() { return healthCheck; }
    public SecurityManager getSecurityManager() { return securityManager; }
    public Map<String, ShardInfo> getRegisteredShards() { return registeredShards; }
    public Map<String, ChunkAllocation> getChunkAllocations() { return chunkAllocations; }
    public StructuredLogger getStructuredLogger() { return structuredLogger; }
    public CircuitBreaker getRedisCircuitBreaker() { return redisCircuitBreaker; }
    public RetryPolicy getRedisRetryPolicy() { return redisRetryPolicy; }
    
    public static void main(String[] args) {
        ProductionCoordinatorServer server = new ProductionCoordinatorServer();
        try {
            server.start();
            server.awaitTermination();
        } catch (Exception e) {
            LOGGER.error("Failed to start coordinator", e);
            System.exit(1);
        }
    }
    
    // Inner classes for data
    public static class ShardInfo {
        private final String id;
        private final String host;
        private final int port;
        private volatile boolean healthy;
        private volatile int playerCount;
        private volatile int chunkCount;
        private volatile long lastHeartbeat;
        
        public ShardInfo(String id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.healthy = true;
        }
        
        public String getId() { return id; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int count) { this.playerCount = count; }
        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int count) { this.chunkCount = count; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(long time) { this.lastHeartbeat = time; }
    }
    
    public static class ChunkAllocation {
        private final int chunkX;
        private final int chunkZ;
        private volatile String shardId;
        
        public ChunkAllocation(int chunkX, int chunkZ, String shardId) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.shardId = shardId;
        }
        
        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
        public String getShardId() { return shardId; }
        public void setShardId(String shardId) { this.shardId = shardId; }
    }
}