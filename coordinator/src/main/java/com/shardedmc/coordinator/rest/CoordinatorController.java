package com.shardedmc.coordinator.rest;

import com.shardedmc.coordinator.ChunkManager;
import com.shardedmc.coordinator.PlayerRoutingService;
import com.shardedmc.coordinator.ShardRegistry;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CoordinatorController {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorController.class);
    
    private final ShardRegistry shardRegistry;
    private final ChunkManager chunkManager;
    private final PlayerRoutingService playerRouting;
    
    public CoordinatorController(ShardRegistry shardRegistry, ChunkManager chunkManager,
                                  PlayerRoutingService playerRouting) {
        this.shardRegistry = shardRegistry;
        this.chunkManager = chunkManager;
        this.playerRouting = playerRouting;
    }
    
    public void registerRoutes(Javalin app) {
        app.get("/api/v1/shards", this::getShards);
        app.get("/api/v1/shards/{shardId}", this::getShard);
        app.get("/api/v1/players/{playerId}/shard", this::getPlayerShard);
        app.get("/api/v1/world/chunks/{x}/{z}/owner", this::getChunkOwner);
        app.get("/api/v1/health", this::healthCheck);
    }
    
    private void getShards(Context ctx) {
        var shards = shardRegistry.getAllShards().stream()
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("shardId", s.shardId());
                    map.put("address", s.address());
                    map.put("capacity", s.capacity());
                    map.put("playerCount", s.playerCount());
                    map.put("load", s.load());
                    map.put("status", s.status());
                    map.put("utilization", s.utilization());
                    return map;
                })
                .collect(Collectors.toList());
        
        ctx.json(shards);
    }
    
    private void getShard(Context ctx) {
        String shardId = ctx.pathParam("shardId");
        var shardOpt = shardRegistry.getShard(shardId);
        
        if (shardOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "Shard not found"));
            return;
        }
        
        var shard = shardOpt.get();
        Map<String, Object> map = new HashMap<>();
        map.put("shardId", shard.shardId());
        map.put("address", shard.address());
        map.put("capacity", shard.capacity());
        map.put("playerCount", shard.playerCount());
        map.put("load", shard.load());
        map.put("status", shard.status());
        map.put("regions", chunkManager.getOwnedChunks(shardId));
        
        ctx.json(map);
    }
    
    private void getPlayerShard(Context ctx) {
        String playerId = ctx.pathParam("playerId");
        playerRouting.getPlayerShard(playerId)
                .thenAccept(shardOpt -> {
                    if (shardOpt.isEmpty()) {
                        ctx.status(404).json(Map.of("error", "Player not found"));
                        return;
                    }
                    
                    var shard = shardOpt.get();
                    Map<String, Object> map = new HashMap<>();
                    map.put("shardId", shard.shardId());
                    map.put("address", shard.address());
                    
                    ctx.json(map);
                });
    }
    
    private void getChunkOwner(Context ctx) {
        int x = Integer.parseInt(ctx.pathParam("x"));
        int z = Integer.parseInt(ctx.pathParam("z"));
        
        chunkManager.getShardForChunk(x, z)
                .thenAccept(ownerOpt -> {
                    if (ownerOpt.isEmpty()) {
                        ctx.status(404).json(Map.of("error", "Chunk not assigned"));
                        return;
                    }
                    
                    ctx.json(Map.of("shardId", ownerOpt.get()));
                });
    }
    
    private void healthCheck(Context ctx) {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("shards", shardRegistry.getAllShards().size());
        health.put("healthyShards", shardRegistry.getHealthyShards().size());
        
        ctx.json(health);
    }
}
