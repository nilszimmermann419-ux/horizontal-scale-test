package com.shardedmc.shared.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CoordinatorConfig {
    
    @JsonProperty("grpc_port")
    private int grpcPort = 50051;
    
    @JsonProperty("rest_port")
    private int restPort = 8080;
    
    @JsonProperty("redis_host")
    private String redisHost = "localhost";
    
    @JsonProperty("redis_port")
    private int redisPort = 6379;
    
    @JsonProperty("redis_password")
    private String redisPassword = "";
    
    @JsonProperty("redis_database")
    private int redisDatabase = 0;
    
    @JsonProperty("redis_pool_max_active")
    private int redisPoolMaxActive = 50;
    
    @JsonProperty("redis_pool_max_idle")
    private int redisPoolMaxIdle = 20;
    
    @JsonProperty("heartbeat_timeout_ms")
    private long heartbeatTimeoutMs = 10000;
    
    @JsonProperty("heartbeat_interval_ms")
    private long heartbeatIntervalMs = 3000;
    
    @JsonProperty("chunk_rebalance_interval_ms")
    private long chunkRebalanceIntervalMs = 30000;
    
    @JsonProperty("max_shards")
    private int maxShards = 100;
    
    @JsonProperty("default_shard_capacity")
    private int defaultShardCapacity = 100;
    
    @JsonProperty("metrics_enabled")
    private boolean metricsEnabled = true;
    
    @JsonProperty("metrics_port")
    private int metricsPort = 9090;
    
    @JsonProperty("log_level")
    private String logLevel = "INFO";
    
    @JsonProperty("tls_enabled")
    private boolean tlsEnabled = false;
    
    @JsonProperty("tls_cert_path")
    private String tlsCertPath = "";
    
    @JsonProperty("tls_key_path")
    private String tlsKeyPath = "";
    
    // Getters and setters
    public int getGrpcPort() { return grpcPort; }
    public void setGrpcPort(int grpcPort) { this.grpcPort = grpcPort; }
    
    public int getRestPort() { return restPort; }
    public void setRestPort(int restPort) { this.restPort = restPort; }
    
    public String getRedisHost() { return redisHost; }
    public void setRedisHost(String redisHost) { this.redisHost = redisHost; }
    
    public int getRedisPort() { return redisPort; }
    public void setRedisPort(int redisPort) { this.redisPort = redisPort; }
    
    public String getRedisPassword() { return redisPassword; }
    public void setRedisPassword(String redisPassword) { this.redisPassword = redisPassword; }
    
    public int getRedisDatabase() { return redisDatabase; }
    public void setRedisDatabase(int redisDatabase) { this.redisDatabase = redisDatabase; }
    
    public int getRedisPoolMaxActive() { return redisPoolMaxActive; }
    public void setRedisPoolMaxActive(int redisPoolMaxActive) { this.redisPoolMaxActive = redisPoolMaxActive; }
    
    public int getRedisPoolMaxIdle() { return redisPoolMaxIdle; }
    public void setRedisPoolMaxIdle(int redisPoolMaxIdle) { this.redisPoolMaxIdle = redisPoolMaxIdle; }
    
    public long getHeartbeatTimeoutMs() { return heartbeatTimeoutMs; }
    public void setHeartbeatTimeoutMs(long heartbeatTimeoutMs) { this.heartbeatTimeoutMs = heartbeatTimeoutMs; }
    
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    
    public long getChunkRebalanceIntervalMs() { return chunkRebalanceIntervalMs; }
    public void setChunkRebalanceIntervalMs(long chunkRebalanceIntervalMs) { this.chunkRebalanceIntervalMs = chunkRebalanceIntervalMs; }
    
    public int getMaxShards() { return maxShards; }
    public void setMaxShards(int maxShards) { this.maxShards = maxShards; }
    
    public int getDefaultShardCapacity() { return defaultShardCapacity; }
    public void setDefaultShardCapacity(int defaultShardCapacity) { this.defaultShardCapacity = defaultShardCapacity; }
    
    public boolean isMetricsEnabled() { return metricsEnabled; }
    public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }
    
    public int getMetricsPort() { return metricsPort; }
    public void setMetricsPort(int metricsPort) { this.metricsPort = metricsPort; }
    
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    
    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    
    public String getTlsCertPath() { return tlsCertPath; }
    public void setTlsCertPath(String tlsCertPath) { this.tlsCertPath = tlsCertPath; }
    
    public String getTlsKeyPath() { return tlsKeyPath; }
    public void setTlsKeyPath(String tlsKeyPath) { this.tlsKeyPath = tlsKeyPath; }
}