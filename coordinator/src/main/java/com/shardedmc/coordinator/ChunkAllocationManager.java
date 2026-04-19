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

public class ChunkAllocationManager {
    private static final Logger logger = LoggerFactory.getLogger(ChunkAllocationManager.class);
    private static final int REGION_SIZE = 16; // 16x16 chunk regions
    
    private final RedisClient redis;
    private final ShardRegistry shardRegistry;
    private final Map<ChunkPos, String> chunkAssignments = new ConcurrentHashMap<>();
    
    public ChunkAllocationManager(RedisClient redis, ShardRegistry shardRegistry) {
        this.redis = redis;
        this.shardRegistry = shardRegistry;
    }
    
    public List<ChunkPos> allocateRegionsForShard(String shardId, int regionCount) {
        List<ShardRegistry.ShardInfo> healthyShards = shardRegistry.getHealthyShards();
        if (healthyShards.isEmpty()) {
            return List.of();
        }
        
        List<ChunkPos> assigned = new ArrayList<>();
        // Simple round-robin for now
        int startX = 0, startZ = 0;
        
        for (int i = 0; i < regionCount; i++) {
            ChunkPos region = new ChunkPos(startX + i, startZ);
            chunkAssignments.put(region, shardId);
            assigned.add(region);
            
            // Store in Redis
            Map<String, String> chunkData = new HashMap<>();
            chunkData.put("ownerShard", shardId);
            chunkData.put("entityCount", "0");
            chunkData.put("lastUpdate", String.valueOf(System.currentTimeMillis()));
            redis.hsetAsync(RedisSchema.chunkKey(region.x(), region.z()), chunkData);
        }
        
        logger.info("Allocated {} regions to shard {}", assigned.size(), shardId);
        return assigned;
    }
    
    public CompletableFuture<Optional<String>> getShardForChunk(int x, int z) {
        // Check local cache first
        ChunkPos region = getRegionForChunk(x, z);
        String shardId = chunkAssignments.get(region);
        if (shardId != null) {
            return CompletableFuture.completedFuture(Optional.of(shardId));
        }
        
        // Check Redis
        return redis.hgetAsync(RedisSchema.chunkKey(x, z), "ownerShard")
                .thenApply(result -> {
                    if (result != null) {
                        chunkAssignments.put(region, result);
                    }
                    return Optional.ofNullable(result);
                });
    }
    
    public ChunkPos getRegionForChunk(int x, int z) {
        // Convert chunk coordinates to region coordinates
        int regionX = Math.floorDiv(x, REGION_SIZE);
        int regionZ = Math.floorDiv(z, REGION_SIZE);
        return new ChunkPos(regionX, regionZ);
    }
    
    public List<ChunkPos> getRegionsForShard(String shardId) {
        return chunkAssignments.entrySet().stream()
                .filter(e -> e.getValue().equals(shardId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    public void rebalance() {
        List<ShardRegistry.ShardInfo> shards = shardRegistry.getHealthyShards();
        if (shards.size() < 2) return;
        
        // Find overloaded and underloaded shards
        Optional<ShardRegistry.ShardInfo> overloaded = shards.stream()
                .filter(s -> s.utilization() > 0.8)
                .max(Comparator.comparingDouble(ShardRegistry.ShardInfo::utilization));
        
        Optional<ShardRegistry.ShardInfo> underloaded = shards.stream()
                .filter(s -> s.utilization() < 0.3)
                .min(Comparator.comparingDouble(ShardRegistry.ShardInfo::utilization));
        
        if (overloaded.isPresent() && underloaded.isPresent()) {
            logger.info("Rebalancing: moving chunks from {} to {}", 
                    overloaded.get().shardId(), underloaded.get().shardId());
            // Implementation: migrate lowest-activity regions
        }
    }
    
    public CompletableFuture<Boolean> transferChunkOwnership(int x, int z, String newShardId) {
        ChunkPos region = getRegionForChunk(x, z);
        String oldShardId = chunkAssignments.get(region);
        
        if (oldShardId == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        chunkAssignments.put(region, newShardId);
        return redis.hsetAsync(RedisSchema.chunkKey(x, z), "ownerShard", newShardId)
                .thenApply(r -> true);
    }
}
