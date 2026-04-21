package com.shardedmc.coordinator;

import com.shardedmc.shared.RedisClient;
import com.shardedmc.shared.RedisSchema;
import com.shardedmc.shared.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ShardRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ShardRegistry.class);
    
    private final RedisClient redis;
    private final Map<String, ShardInfo> shards = new ConcurrentHashMap<>();
    
    public ShardRegistry(RedisClient redis) {
        this.redis = redis;
    }
    
    public CompletableFuture<Void> registerShard(String shardId, String address, int port, int capacity) {
        ShardInfo info = new ShardInfo(shardId, address, port, capacity, 0, 0.0, "healthy");
        shards.put(shardId, info);
        logger.info("Registered shard: {} at {}:{}", shardId, address, port);
        
        if (redis != null) {
            Map<String, String> data = new HashMap<>();
            data.put("address", address + ":" + port);
            data.put("capacity", String.valueOf(capacity));
            data.put("playerCount", "0");
            data.put("load", "0.0");
            data.put("status", "healthy");
            data.put("lastHeartbeat", String.valueOf(System.currentTimeMillis()));
            data.put("regions", "");
            
            return redis.hsetAsync(RedisSchema.shardKey(shardId), data)
                    .thenAccept(result -> {});
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> updateHeartbeat(String shardId, double load, int playerCount, List<ChunkPos> regions) {
        ShardInfo info = shards.get(shardId);
        if (info != null) {
            info = new ShardInfo(shardId, info.address(), info.port(), info.capacity(), 
                    playerCount, load, info.status());
            shards.put(shardId, info);
        }
        
        if (redis != null) {
            Map<String, String> data = new HashMap<>();
            data.put("load", String.valueOf(load));
            data.put("playerCount", String.valueOf(playerCount));
            data.put("lastHeartbeat", String.valueOf(System.currentTimeMillis()));
            data.put("regions", regions.stream()
                    .map(r -> r.x() + "," + r.z())
                    .collect(Collectors.joining(";")));
            
            return redis.hsetAsync(RedisSchema.shardKey(shardId), data)
                    .thenAccept(result -> {});
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    public void markShardUnhealthy(String shardId) {
        shards.compute(shardId, (id, info) -> {
            if (info != null) {
                return new ShardInfo(id, info.address(), info.port(), info.capacity(),
                        info.playerCount(), info.load(), "unhealthy");
            }
            return null;
        });
        
        if (redis != null) {
            redis.hsetAsync(RedisSchema.shardKey(shardId), "status", "unhealthy");
        }
        logger.warn("Marked shard as unhealthy: {}", shardId);
    }
    
    public void removeShard(String shardId) {
        shards.remove(shardId);
        if (redis != null) {
            redis.delAsync(RedisSchema.shardKey(shardId));
        }
        logger.info("Removed shard: {}", shardId);
    }
    
    public Optional<ShardInfo> getShard(String shardId) {
        return Optional.ofNullable(shards.get(shardId));
    }
    
    public List<ShardInfo> getHealthyShards() {
        return shards.values().stream()
                .filter(s -> "healthy".equals(s.status()))
                .collect(Collectors.toList());
    }
    
    public List<ShardInfo> getAllShards() {
        return new ArrayList<>(shards.values());
    }
    
    public boolean isShardHealthy(String shardId) {
        ShardInfo info = shards.get(shardId);
        return info != null && "healthy".equals(info.status());
    }
    
    public record ShardInfo(String shardId, String address, int port, int capacity,
                            int playerCount, double load, String status) {
        
        public double utilization() {
            return capacity > 0 ? (double) playerCount / capacity : 0.0;
        }
        
        public boolean isFull() {
            return playerCount >= capacity;
        }
        
        public boolean hasCapacity() {
            return playerCount < capacity;
        }
    }
}
