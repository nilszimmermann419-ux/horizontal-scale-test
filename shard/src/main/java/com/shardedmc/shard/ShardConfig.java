package com.shardedmc.shard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ShardConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShardConfig.class);
    private static final String CONFIG_FILE = "config.properties";
    
    private final Properties properties;

    public ShardConfig() {
        this.properties = new Properties();
        loadConfig();
        validateConfig();
    }

    private void validateConfig() {
        // Validate port ranges (1-65535)
        int shardPort = getShardPort();
        if (shardPort < 1 || shardPort > 65535) {
            throw new IllegalArgumentException("shard.port must be between 1 and 65535, got: " + shardPort);
        }

        int coordinatorPort = getCoordinatorPort();
        if (coordinatorPort < 1 || coordinatorPort > 65535) {
            throw new IllegalArgumentException("coordinator.port must be between 1 and 65535, got: " + coordinatorPort);
        }

        int natsPort = getNatsPort();
        if (natsPort < 1 || natsPort > 65535) {
            throw new IllegalArgumentException("nats.port must be between 1 and 65535, got: " + natsPort);
        }

        int redisPort = getRedisPort();
        if (redisPort < 1 || redisPort > 65535) {
            throw new IllegalArgumentException("redis.port must be between 1 and 65535, got: " + redisPort);
        }

        int minioPort = getMinioPort();
        if (minioPort < 1 || minioPort > 65535) {
            throw new IllegalArgumentException("minio.port must be between 1 and 65535, got: " + minioPort);
        }

        // Validate region size (power of 2, <= 16)
        int regionSize = getRegionSize();
        if (regionSize < 1 || regionSize > 16 || !isPowerOfTwo(regionSize)) {
            throw new IllegalArgumentException("shard.region_size must be a power of 2 and <= 16, got: " + regionSize);
        }

        // Validate max players (> 0)
        int maxPlayers = getMaxPlayers();
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("shard.max_players must be greater than 0, got: " + maxPlayers);
        }

        // Validate coordinator host (not empty)
        String coordinatorHost = getCoordinatorHost();
        if (coordinatorHost == null || coordinatorHost.trim().isEmpty()) {
            throw new IllegalArgumentException("coordinator.host cannot be empty");
        }
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private void loadConfig() {
        // First, try to load from file
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                properties.load(is);
                LOGGER.info("Loaded configuration from {}", CONFIG_FILE);
            } else {
                LOGGER.warn("Configuration file {} not found, using defaults and environment variables", CONFIG_FILE);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load configuration file", e);
        }

        // Environment variables override file properties
        overrideWithEnvVars();
    }

    private void overrideWithEnvVars() {
        // Shard settings
        overrideProperty("shard.id", "SHARD_ID");
        overrideProperty("shard.port", "SHARD_PORT");
        overrideProperty("shard.region_size", "SHARD_REGION_SIZE");
        overrideProperty("shard.max_players", "SHARD_MAX_PLAYERS");

        // Coordinator settings
        overrideProperty("coordinator.host", "COORDINATOR_HOST");
        overrideProperty("coordinator.port", "COORDINATOR_PORT");

        // NATS settings
        overrideProperty("nats.host", "NATS_HOST");
        overrideProperty("nats.port", "NATS_PORT");

        // Redis settings
        overrideProperty("redis.host", "REDIS_HOST");
        overrideProperty("redis.port", "REDIS_PORT");

        // MinIO settings
        overrideProperty("minio.host", "MINIO_HOST");
        overrideProperty("minio.port", "MINIO_PORT");
        overrideProperty("minio.access_key", "MINIO_ACCESS_KEY");
        overrideProperty("minio.secret_key", "MINIO_SECRET_KEY");
        overrideProperty("minio.bucket", "MINIO_BUCKET");

        // World settings
        overrideProperty("world.seed", "WORLD_SEED");
        overrideProperty("world.name", "WORLD_NAME");
        overrideProperty("world.difficulty", "WORLD_DIFFICULTY");
        overrideProperty("world.gamemode", "WORLD_GAMEMODE");

        // Performance settings
        overrideProperty("chunk.load_threads", "CHUNK_LOAD_THREADS");
        overrideProperty("lighting.threads", "LIGHTING_THREADS");
        overrideProperty("event.publish_threads", "EVENT_PUBLISH_THREADS");
    }

    private void overrideProperty(String propertyKey, String envVar) {
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            properties.setProperty(propertyKey, envValue);
            LOGGER.debug("Overriding {} with environment variable {}", propertyKey, envVar);
        }
    }

    private String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private int getInt(String key, int defaultValue) {
        try {
            String value = properties.getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid integer value for {}, using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    private long getLong(String key, long defaultValue) {
        try {
            String value = properties.getProperty(key);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid long value for {}, using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    // Shard settings
    public String getShardId() {
        return getString("shard.id", "shard-" + System.currentTimeMillis() % 10000);
    }

    public int getShardPort() {
        return getInt("shard.port", 25566);
    }

    public int getRegionSize() {
        return getInt("shard.region_size", 4);
    }

    public int getMaxPlayers() {
        return getInt("shard.max_players", 2000);
    }

    // Coordinator settings
    public String getCoordinatorHost() {
        return getString("coordinator.host", "localhost");
    }

    public int getCoordinatorPort() {
        return getInt("coordinator.port", 50051);
    }

    // NATS settings
    public String getNatsHost() {
        return getString("nats.host", "localhost");
    }

    public int getNatsPort() {
        return getInt("nats.port", 4222);
    }

    public String getNatsUrl() {
        return String.format("nats://%s:%d", getNatsHost(), getNatsPort());
    }

    // Redis settings
    public String getRedisHost() {
        return getString("redis.host", "localhost");
    }

    public int getRedisPort() {
        return getInt("redis.port", 6379);
    }

    public String getRedisUrl() {
        return String.format("redis://%s:%d", getRedisHost(), getRedisPort());
    }

    // MinIO settings
    public String getMinioHost() {
        return getString("minio.host", "localhost");
    }

    public int getMinioPort() {
        return getInt("minio.port", 9000);
    }

    public String getMinioAccessKey() {
        return getString("minio.access_key", "minioadmin");
    }

    public String getMinioSecretKey() {
        return getString("minio.secret_key", "minioadmin");
    }

    public String getMinioBucket() {
        return getString("minio.bucket", "shardedmc-world");
    }

    public String getMinioUrl() {
        return String.format("http://%s:%d", getMinioHost(), getMinioPort());
    }

    // World settings
    public long getWorldSeed() {
        return getLong("world.seed", 12345L);
    }

    public String getWorldName() {
        return getString("world.name", "world");
    }

    public String getWorldDifficulty() {
        return getString("world.difficulty", "normal");
    }

    public String getWorldGamemode() {
        return getString("world.gamemode", "survival");
    }

    // Performance settings
    public int getChunkLoadThreads() {
        return getInt("chunk.load_threads", 4);
    }

    public int getLightingThreads() {
        return getInt("lighting.threads", 2);
    }

    public int getEventPublishThreads() {
        return getInt("event.publish_threads", 2);
    }

    /**
     * Get raw property value
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Check if a property exists
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
}
