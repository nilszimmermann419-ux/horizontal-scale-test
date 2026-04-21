package com.shardedmc.shared.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShardConfig {
    
    @JsonProperty("shard_id")
    private String shardId = "shard-" + java.util.UUID.randomUUID().toString();
    
    @JsonProperty("server_host")
    private String serverHost = "0.0.0.0";
    
    @JsonProperty("server_port")
    private int serverPort = 25565;
    
    @JsonProperty("max_players")
    private int maxPlayers = 100;
    
    @JsonProperty("view_distance")
    private int viewDistance = 10;
    
    @JsonProperty("simulation_distance")
    private int simulationDistance = 8;
    
    @JsonProperty("chunk_load_distance")
    private int chunkLoadDistance = 8;
    
    @JsonProperty("coordinator_host")
    private String coordinatorHost = "localhost";
    
    @JsonProperty("coordinator_port")
    private int coordinatorPort = 50051;
    
    @JsonProperty("coordinator_retry_attempts")
    private int coordinatorRetryAttempts = 5;
    
    @JsonProperty("coordinator_retry_delay_ms")
    private long coordinatorRetryDelayMs = 1000;
    
    @JsonProperty("redis_host")
    private String redisHost = "localhost";
    
    @JsonProperty("redis_port")
    private int redisPort = 6379;
    
    @JsonProperty("redis_password")
    private String redisPassword = "";
    
    @JsonProperty("redis_database")
    private int redisDatabase = 0;
    
    @JsonProperty("heartbeat_interval_ms")
    private long heartbeatIntervalMs = 3000;
    
    @JsonProperty("boundary_check_interval_ms")
    private long boundaryCheckIntervalMs = 500;
    
    @JsonProperty("player_state_sync_interval_ms")
    private long playerStateSyncIntervalMs = 100;
    
    @JsonProperty("entity_sync_interval_ms")
    private long entitySyncIntervalMs = 50;
    
    @JsonProperty("metrics_enabled")
    private boolean metricsEnabled = true;
    
    @JsonProperty("metrics_port")
    private int metricsPort = 9091;
    
    @JsonProperty("log_level")
    private String logLevel = "INFO";
    
    @JsonProperty("tls_enabled")
    private boolean tlsEnabled = false;
    
    @JsonProperty("tls_trust_cert_path")
    private String tlsTrustCertPath = "";
    
    @JsonProperty("plugins_directory")
    private String pluginsDirectory = "plugins";
    
    @JsonProperty("world_directory")
    private String worldDirectory = "world";
    
    @JsonProperty("online_mode")
    private boolean onlineMode = false;
    
    @JsonProperty("world_border_radius")
    private int worldBorderRadius = 0;
    
    @JsonProperty("enable_auto_save")
    private boolean enableAutoSave = true;
    
    @JsonProperty("auto_save_interval_ms")
    private long autoSaveIntervalMs = 300000;
    
    @JsonProperty("connection_timeout_ms")
    private long connectionTimeoutMs = 30000;
    
    @JsonProperty("read_timeout_ms")
    private long readTimeoutMs = 30000;
    
    @JsonProperty("write_timeout_ms")
    private long writeTimeoutMs = 30000;
    
    // Getters and setters
    public String getShardId() { return shardId; }
    public void setShardId(String shardId) { this.shardId = shardId; }
    
    public String getServerHost() { return serverHost; }
    public void setServerHost(String serverHost) { this.serverHost = serverHost; }
    
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    
    public int getViewDistance() { return viewDistance; }
    public void setViewDistance(int viewDistance) { this.viewDistance = viewDistance; }
    
    public int getSimulationDistance() { return simulationDistance; }
    public void setSimulationDistance(int simulationDistance) { this.simulationDistance = simulationDistance; }
    
    public int getChunkLoadDistance() { return chunkLoadDistance; }
    public void setChunkLoadDistance(int chunkLoadDistance) { this.chunkLoadDistance = chunkLoadDistance; }
    
    public String getCoordinatorHost() { return coordinatorHost; }
    public void setCoordinatorHost(String coordinatorHost) { this.coordinatorHost = coordinatorHost; }
    
    public int getCoordinatorPort() { return coordinatorPort; }
    public void setCoordinatorPort(int coordinatorPort) { this.coordinatorPort = coordinatorPort; }
    
    public int getCoordinatorRetryAttempts() { return coordinatorRetryAttempts; }
    public void setCoordinatorRetryAttempts(int coordinatorRetryAttempts) { this.coordinatorRetryAttempts = coordinatorRetryAttempts; }
    
    public long getCoordinatorRetryDelayMs() { return coordinatorRetryDelayMs; }
    public void setCoordinatorRetryDelayMs(long coordinatorRetryDelayMs) { this.coordinatorRetryDelayMs = coordinatorRetryDelayMs; }
    
    public String getRedisHost() { return redisHost; }
    public void setRedisHost(String redisHost) { this.redisHost = redisHost; }
    
    public int getRedisPort() { return redisPort; }
    public void setRedisPort(int redisPort) { this.redisPort = redisPort; }
    
    public String getRedisPassword() { return redisPassword; }
    public void setRedisPassword(String redisPassword) { this.redisPassword = redisPassword; }
    
    public int getRedisDatabase() { return redisDatabase; }
    public void setRedisDatabase(int redisDatabase) { this.redisDatabase = redisDatabase; }
    
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    
    public long getBoundaryCheckIntervalMs() { return boundaryCheckIntervalMs; }
    public void setBoundaryCheckIntervalMs(long boundaryCheckIntervalMs) { this.boundaryCheckIntervalMs = boundaryCheckIntervalMs; }
    
    public long getPlayerStateSyncIntervalMs() { return playerStateSyncIntervalMs; }
    public void setPlayerStateSyncIntervalMs(long playerStateSyncIntervalMs) { this.playerStateSyncIntervalMs = playerStateSyncIntervalMs; }
    
    public long getEntitySyncIntervalMs() { return entitySyncIntervalMs; }
    public void setEntitySyncIntervalMs(long entitySyncIntervalMs) { this.entitySyncIntervalMs = entitySyncIntervalMs; }
    
    public boolean isMetricsEnabled() { return metricsEnabled; }
    public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }
    
    public int getMetricsPort() { return metricsPort; }
    public void setMetricsPort(int metricsPort) { this.metricsPort = metricsPort; }
    
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    
    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    
    public String getTlsTrustCertPath() { return tlsTrustCertPath; }
    public void setTlsTrustCertPath(String tlsTrustCertPath) { this.tlsTrustCertPath = tlsTrustCertPath; }
    
    public String getPluginsDirectory() { return pluginsDirectory; }
    public void setPluginsDirectory(String pluginsDirectory) { this.pluginsDirectory = pluginsDirectory; }
    
    public String getWorldDirectory() { return worldDirectory; }
    public void setWorldDirectory(String worldDirectory) { this.worldDirectory = worldDirectory; }
    
    public boolean isEnableAutoSave() { return enableAutoSave; }
    public void setEnableAutoSave(boolean enableAutoSave) { this.enableAutoSave = enableAutoSave; }
    
    public long getAutoSaveIntervalMs() { return autoSaveIntervalMs; }
    public void setAutoSaveIntervalMs(long autoSaveIntervalMs) { this.autoSaveIntervalMs = autoSaveIntervalMs; }
    
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    
    public long getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    
    public String getHost() { return serverHost; }
    
    public int getPort() { return serverPort; }
    
    public String getCoordinatorAddress() {
        return coordinatorHost + ":" + coordinatorPort;
    }
    
    public String getWorldPath() { return worldDirectory; }
    
    public boolean isOnlineMode() { return onlineMode; }
    public void setOnlineMode(boolean onlineMode) { this.onlineMode = onlineMode; }
    
    public int getWorldBorderRadius() { return worldBorderRadius; }
    public void setWorldBorderRadius(int worldBorderRadius) { this.worldBorderRadius = worldBorderRadius; }

    public void validate() {
        if (shardId == null || shardId.isEmpty()) {
            throw new IllegalArgumentException("shardId must not be empty");
        }
        if (serverHost == null || serverHost.isEmpty()) {
            throw new IllegalArgumentException("serverHost must not be empty");
        }
        if (serverPort <= 0) {
            throw new IllegalArgumentException("serverPort must be positive");
        }
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("maxPlayers must be positive");
        }
        if (viewDistance <= 0 || simulationDistance <= 0 || chunkLoadDistance <= 0) {
            throw new IllegalArgumentException("distances must be positive");
        }
        if (coordinatorHost == null || coordinatorHost.isEmpty()) {
            throw new IllegalArgumentException("coordinatorHost must not be empty");
        }
        if (coordinatorPort <= 0) {
            throw new IllegalArgumentException("coordinatorPort must be positive");
        }
        if (redisHost == null || redisHost.isEmpty()) {
            throw new IllegalArgumentException("redisHost must not be empty");
        }
        if (redisPort <= 0) {
            throw new IllegalArgumentException("redisPort must be positive");
        }
        if (heartbeatIntervalMs <= 0 || boundaryCheckIntervalMs <= 0 || playerStateSyncIntervalMs <= 0 || entitySyncIntervalMs <= 0) {
            throw new IllegalArgumentException("intervals must be positive");
        }
    }
}