package com.shardedmc.shard.storage;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChunkStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkStorage.class);
    private static final String BUCKET_NAME = "shardedmc-chunks";
    private static final String CHUNK_PREFIX = "chunk/";
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    // L1: Memory cache
    private final ConcurrentHashMap<Long, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    // L2: Redis
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, byte[]> redisConnection;
    private final RedisAsyncCommands<String, byte[]> redisAsync;

    // L3: MinIO
    private final MinioClient minioClient;

    public ChunkStorage(String redisUrl, String minioUrl, String minioAccessKey, String minioSecretKey) {
        // Initialize Redis
        this.redisClient = RedisClient.create(redisUrl);
        this.redisConnection = redisClient.connect(new io.lettuce.core.codec.ByteArrayCodec());
        this.redisAsync = redisConnection.async();

        // Initialize MinIO
        this.minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(minioAccessKey, minioSecretKey)
                .build();

        // Ensure bucket exists
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
                LOGGER.info("Created MinIO bucket: {}", BUCKET_NAME);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize MinIO bucket", e);
        }

        // Start cache cleanup task
        startCacheCleanup();
    }

    public CompletableFuture<byte[]> loadChunk(int chunkX, int chunkZ) {
        long key = packChunkCoord(chunkX, chunkZ);

        // L1: Check memory cache
        CacheEntry cached = memoryCache.get(key);
        if (cached != null && !cached.isExpired()) {
            LOGGER.debug("Chunk {}, {} found in memory cache", chunkX, chunkZ);
            return CompletableFuture.completedFuture(cached.data);
        }

        // L2: Check Redis
        String redisKey = getRedisKey(chunkX, chunkZ);
        RedisFuture<byte[]> redisFuture = redisAsync.get(redisKey);

        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = redisFuture.get(5, TimeUnit.SECONDS);
                if (data != null && data.length > 0) {
                    LOGGER.debug("Chunk {}, {} found in Redis cache", chunkX, chunkZ);
                    memoryCache.put(key, new CacheEntry(data));
                    return data;
                }
            } catch (Exception e) {
                LOGGER.warn("Redis read failed for chunk {}, {}", chunkX, chunkZ, e);
            }

            // L3: Load from MinIO
            try {
                String objectName = getMinioObjectName(chunkX, chunkZ);
                var response = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(objectName)
                        .build());

                byte[] data = response.readAllBytes();
                response.close();

                if (data.length > 0) {
                    LOGGER.debug("Chunk {}, {} loaded from MinIO", chunkX, chunkZ);

                    // Cache in L1 and L2
                    memoryCache.put(key, new CacheEntry(data));
                    redisAsync.setex(redisKey, CACHE_TTL_SECONDS, data);

                    return data;
                }
            } catch (ErrorResponseException e) {
                if (e.errorResponse().code().equals("NoSuchKey")) {
                    LOGGER.debug("Chunk {}, {} not found in MinIO (new chunk)", chunkX, chunkZ);
                } else {
                    LOGGER.error("MinIO read error for chunk {}, {}", chunkX, chunkZ, e);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load chunk {}, {} from MinIO", chunkX, chunkZ, e);
            }

            return null;
        });
    }

    public CompletableFuture<Void> saveChunk(int chunkX, int chunkZ, byte[] data) {
        if (data == null || data.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        long key = packChunkCoord(chunkX, chunkZ);
        String redisKey = getRedisKey(chunkX, chunkZ);
        String objectName = getMinioObjectName(chunkX, chunkZ);

        return CompletableFuture.runAsync(() -> {
            try {
                // Update L1 cache
                memoryCache.put(key, new CacheEntry(data));

                // Update L2 cache (Redis)
                redisAsync.setex(redisKey, CACHE_TTL_SECONDS, data).get(5, TimeUnit.SECONDS);

                // Update L3 storage (MinIO)
                ByteArrayInputStream stream = new ByteArrayInputStream(data);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(objectName)
                        .stream(stream, data.length, -1)
                        .contentType("application/octet-stream")
                        .build());

                LOGGER.debug("Chunk {}, {} saved to all storage tiers", chunkX, chunkZ);
            } catch (Exception e) {
                LOGGER.error("Failed to save chunk {}, {}", chunkX, chunkZ, e);
                throw new RuntimeException("Failed to save chunk", e);
            }
        });
    }

    public boolean isCached(int chunkX, int chunkZ) {
        long key = packChunkCoord(chunkX, chunkZ);
        CacheEntry entry = memoryCache.get(key);
        return entry != null && !entry.isExpired();
    }

    public void invalidateCache(int chunkX, int chunkZ) {
        long key = packChunkCoord(chunkX, chunkZ);
        memoryCache.remove(key);

        String redisKey = getRedisKey(chunkX, chunkZ);
        redisAsync.del(redisKey);
    }

    private String getRedisKey(int chunkX, int chunkZ) {
        return CHUNK_PREFIX + chunkX + "/" + chunkZ;
    }

    private String getMinioObjectName(int chunkX, int chunkZ) {
        return "world/overworld/" + chunkX + "/" + chunkZ + ".bin";
    }

    private long packChunkCoord(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private void startCacheCleanup() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    Thread.sleep(60000); // Run every minute
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "chunk-cache-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void cleanupExpiredEntries() {
        int removed = 0;
        long now = System.currentTimeMillis();
        for (var it = memoryCache.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.debug("Cleaned up {} expired cache entries", removed);
        }
    }

    public void shutdown() {
        LOGGER.info("Shutting down chunk storage");
        redisConnection.close();
        redisClient.shutdown();
    }

    private static class CacheEntry {
        final byte[] data;
        final long timestamp;

        CacheEntry(byte[] data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.SECONDS.toMillis(CACHE_TTL_SECONDS);
        }
    }
}
