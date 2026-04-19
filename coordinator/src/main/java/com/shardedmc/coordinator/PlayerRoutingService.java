package com.shardedmc.coordinator;

import com.shardedmc.shared.RedisClient;
import com.shardedmc.shared.RedisSchema;
import com.shardedmc.shared.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerRoutingService {
    private static final Logger logger = LoggerFactory.getLogger(PlayerRoutingService.class);
    
    private final RedisClient redis;
    private final ShardRegistry shardRegistry;
    private final ChunkManager chunkManager;
    
    public PlayerRoutingService(RedisClient redis, ShardRegistry shardRegistry, ChunkManager chunkManager) {
        this.redis = redis;
        this.shardRegistry = shardRegistry;
        this.chunkManager = chunkManager;
    }
    
    public CompletableFuture<Optional<ShardRegistry.ShardInfo>> routePlayer(String playerUuid, int chunkX, int chunkZ) {
        return chunkManager.getShardForChunk(chunkX, chunkZ)
                .thenApply(shardIdOpt -> shardIdOpt.flatMap(shardId -> {
                    ShardRegistry.ShardInfo shard = shardRegistry.getShard(shardId).orElse(null);
                    if (shard != null && shard.hasCapacity()) {
                        return Optional.of(shard);
                    }
                    
                    // Find alternative shard with capacity
                    return shardRegistry.getHealthyShards().stream()
                            .filter(ShardRegistry.ShardInfo::hasCapacity)
                            .min(Comparator.comparingDouble(ShardRegistry.ShardInfo::utilization));
                }));
    }
    
    public CompletableFuture<Optional<ShardRegistry.ShardInfo>> getPlayerShard(String playerUuid) {
        return redis.hgetAsync(RedisSchema.playerKey(playerUuid), "currentShard")
                .thenApply(shardId -> {
                    if (shardId == null) return Optional.<ShardRegistry.ShardInfo>empty();
                    return shardRegistry.getShard(shardId);
                });
    }
    
    public CompletableFuture<Void> updatePlayerLocation(String playerUuid, String shardId, ChunkPos chunk) {
        Map<String, String> data = new HashMap<>();
        data.put("currentShard", shardId);
        data.put("chunkX", String.valueOf(chunk.x()));
        data.put("chunkZ", String.valueOf(chunk.z()));
        data.put("lastSeen", String.valueOf(System.currentTimeMillis()));
        
        return redis.hsetAsync(RedisSchema.playerKey(playerUuid), data)
                .thenAccept(r -> {});
    }
    
    public CompletableFuture<Void> removePlayer(String playerUuid) {
        return redis.delAsync(RedisSchema.playerKey(playerUuid))
                .thenAccept(result -> logger.info("Removed player {} from routing", playerUuid));
    }
}
