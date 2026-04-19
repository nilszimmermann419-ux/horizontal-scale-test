package com.shardedmc.shard;

import com.shardedmc.api.ShardedPlugin;
import com.shardedmc.plugin.ShardedPluginManager;
import com.shardedmc.shared.config.ConfigLoader;
import com.shardedmc.shared.config.ShardConfig;
import com.shardedmc.shared.health.HealthCheck;
import com.shardedmc.shared.logging.StructuredLogger;
import com.shardedmc.shared.metrics.MetricsCollector;
import com.shardedmc.shared.resilience.CircuitBreaker;
import com.shardedmc.shared.resilience.RetryPolicy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.anvil.AnvilLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProductionShardServer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionShardServer.class);
    
    private final ShardConfig config;
    private final ConfigLoader configLoader;
    private final StructuredLogger structuredLogger;
    private final MetricsCollector metrics;
    private final HealthCheck healthCheck;
    private final CircuitBreaker coordinatorCircuitBreaker;
    private final RetryPolicy coordinatorRetryPolicy;
    
    private final MinecraftServer minecraftServer;
    private final InstanceManager instanceManager;
    private InstanceContainer instance;
    private final ShardCoordinatorClient coordinatorClient;
    private final ShardHeartbeatService heartbeatService;
    private final PlayerBoundaryMonitor boundaryMonitor;
    private final CrossShardEventHandler eventHandler;
    private final ShardedPluginManager pluginLoader;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<UUID, Long> playerJoinTimes = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    
    public ProductionShardServer() {
        this.configLoader = new ConfigLoader("config/shard");
        this.config = configLoader.load("shard", ShardConfig.class);
        this.structuredLogger = new StructuredLogger(ProductionShardServer.class);
        this.structuredLogger.addDefaultContext("service", "shard");
        this.structuredLogger.addDefaultContext("shard_id", config.getShardId());
        this.metrics = new MetricsCollector("shard", config.getShardId());
        this.healthCheck = new HealthCheck();
        
        this.coordinatorCircuitBreaker = new CircuitBreaker("coordinator", 5, Duration.ofSeconds(30), 3);
        this.coordinatorRetryPolicy = new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(5), 2.0);
        
        this.minecraftServer = MinecraftServer.init();
        this.instanceManager = MinecraftServer.getInstanceManager();
        this.coordinatorClient = new ShardCoordinatorClient(config.getCoordinatorAddress());
        this.heartbeatService = new ShardHeartbeatService(coordinatorClient, config.getShardId(), config.getMaxPlayers());
        this.boundaryMonitor = new PlayerBoundaryMonitor(coordinatorClient, config.getShardId());
        this.eventHandler = new CrossShardEventHandler(null, instance);
        this.pluginLoader = new ShardedPluginManager(getClass().getClassLoader());
        
        setupHealthChecks();
        setupEventHandlers();
        
        structuredLogger.info("ProductionShardServer initialized", 
            Map.of("shard_id", config.getShardId(),
                   "port", String.valueOf(config.getPort()),
                   "coordinator", config.getCoordinatorAddress()));
    }
    
    private void setupHealthChecks() {
        healthCheck.registerIndicator("minestom", () -> running 
            ? HealthCheck.HealthStatus.HEALTHY 
            : HealthCheck.HealthStatus.UNHEALTHY);
        
        healthCheck.registerIndicator("coordinator", () -> {
            if (coordinatorCircuitBreaker.isOpen()) {
                return HealthCheck.HealthStatus.DEGRADED;
            }
            return coordinatorClient.isConnected() 
                ? HealthCheck.HealthStatus.HEALTHY 
                : HealthCheck.HealthStatus.DEGRADED;
        });
        
        healthCheck.registerIndicator("memory", () -> {
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long maxMemory = Runtime.getRuntime().maxMemory();
            double usage = (double) usedMemory / maxMemory;
            if (usage > 0.9) return HealthCheck.HealthStatus.UNHEALTHY;
            if (usage > 0.75) return HealthCheck.HealthStatus.DEGRADED;
            return HealthCheck.HealthStatus.HEALTHY;
        });
    }
    
    private void setupEventHandlers() {
        GlobalEventHandler globalEvent = MinecraftServer.getGlobalEventHandler();
        
        globalEvent.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            UUID playerId = event.getPlayer().getUuid();
            playerJoinTimes.put(playerId, System.currentTimeMillis());
            
            structuredLogger.logPlayerEvent(playerId.toString(), "player_login",
                "Player logged in", Map.of("name", event.getPlayer().getUsername()));
            
            metrics.incrementCounter("player.login");
            
            // Set spawn instance
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(new Pos(0, 100, 0));
        });
        
        // Register cross-shard event handlers
        eventHandler.startListening();
    }
    
    public void start() {
        structuredLogger.logEvent("server_starting", "Starting production shard server", null);
        
        // Setup world
        setupWorld();
        
        // Connect to coordinator
        connectToCoordinator();
        
        // Start heartbeat service
        heartbeatService.start();
        
        // Start boundary monitoring
        boundaryMonitor.registerEvents(MinecraftServer.getGlobalEventHandler());
        
        // Load plugins
        loadPlugins();
        
        // Start Minestom server
        minecraftServer.start("0.0.0.0", config.getPort());
        running = true;
        
        // Start health checks
        healthCheck.start(10);
        
        // Start periodic tasks
        scheduler.scheduleAtFixedRate(this::reportMetrics, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupStaleData, 1, 1, TimeUnit.MINUTES);
        
        structuredLogger.logEvent("server_started", "Production shard server fully started", 
            Map.of("port", String.valueOf(config.getPort())));
    }
    
    private void setupWorld() {
        String worldPath = config.getWorldDirectory();
        File worldDir = new File(worldPath);
        
        if (worldDir.exists() && worldDir.isDirectory()) {
            instance = instanceManager.createInstanceContainer(new AnvilLoader(worldPath));
            structuredLogger.info("Loaded world from " + worldPath);
        } else {
            instance = instanceManager.createInstanceContainer();
            structuredLogger.info("Created new empty world");
        }
        
        instance.setChunkSupplier(LightingChunk::new);
    }
    
    private void connectToCoordinator() {
        try {
            coordinatorClient.connect(config.getShardId(), config.getHost(), config.getPort());
            structuredLogger.info("Connected to coordinator");
            coordinatorCircuitBreaker.recordSuccess();
        } catch (Exception e) {
            LOGGER.error("Failed to connect to coordinator", e);
            coordinatorCircuitBreaker.recordFailure();
            // Continue anyway - will retry via heartbeat
        }
    }
    
    private void loadPlugins() {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }
        
        try {
            pluginLoader.loadPlugins(pluginsDir);
            structuredLogger.info("Plugins loaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load plugins", e);
        }
    }
    
    private com.shardedmc.api.ShardedPluginContext createPluginContext(ShardedPlugin plugin) {
        return new com.shardedmc.shard.api.ShardedPluginContextImpl(
            instance, coordinatorClient, config.getShardId(), 
            java.nio.file.Path.of("plugins", plugin.getInfo().name()), pluginLoader);
    }
    
    private void reportMetrics() {
        int playerCount = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
        metrics.recordGauge("players.online", playerCount);
        metrics.recordMemoryUsage();
        
        // Report to coordinator
        try {
            coordinatorClient.updatePlayerCount(playerCount);
        } catch (Exception e) {
            LOGGER.debug("Failed to report player count to coordinator", e);
        }
    }
    
    private void cleanupStaleData() {
        long now = System.currentTimeMillis();
        var onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers();
        playerJoinTimes.entrySet().removeIf(entry -> {
            // Remove entries for players who disconnected
            return onlinePlayers.stream().noneMatch(p -> p.getUuid().equals(entry.getKey()));
        });
    }
    
    public void stop() {
        structuredLogger.logEvent("server_stopping", "Stopping production shard server", null);
        running = false;
        
        healthCheck.stop();
        scheduler.shutdown();
        heartbeatService.stop();
        boundaryMonitor.stop();
        
        // Disconnect from coordinator
        try {
            coordinatorClient.disconnect();
        } catch (Exception e) {
            LOGGER.debug("Error disconnecting from coordinator", e);
        }
        
        // Stop Minestom
        MinecraftServer.stopCleanly();
        
        metrics.shutdown();
        structuredLogger.logEvent("server_stopped", "Production shard server stopped", null);
    }
    
    // Getters
    public ShardConfig getConfig() { return config; }
    public MinecraftServer getMinecraftServer() { return minecraftServer; }
    public InstanceContainer getInstance() { return instance; }
    public ShardCoordinatorClient getCoordinatorClient() { return coordinatorClient; }
    public MetricsCollector getMetrics() { return metrics; }
    public HealthCheck getHealthCheck() { return healthCheck; }
    public StructuredLogger getStructuredLogger() { return structuredLogger; }
    public boolean isRunning() { return running; }
    
    public static void main(String[] args) {
        ProductionShardServer server = new ProductionShardServer();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        
        server.start();
    }
}