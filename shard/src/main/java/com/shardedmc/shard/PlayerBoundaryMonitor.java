package com.shardedmc.shard;

import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shared.Vec3d;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.EventNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerBoundaryMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PlayerBoundaryMonitor.class);
    private static final int BUFFER_CHUNKS = 3; // Start pre-loading 3 chunks before boundary
    
    private final ShardCoordinatorClient coordinatorClient;
    private final String shardId;
    private final Map<UUID, ChunkPos> lastChunkPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingTransfers = new ConcurrentHashMap<>();
    
    public PlayerBoundaryMonitor(ShardCoordinatorClient coordinatorClient, String shardId) {
        this.coordinatorClient = coordinatorClient;
        this.shardId = shardId;
    }
    
    public void registerEvents(EventNode<net.minestom.server.event.Event> eventNode) {
        eventNode.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Vec3d newPos = new Vec3d(event.getNewPosition().x(), 
                                      event.getNewPosition().y(), 
                                      event.getNewPosition().z());
            
            ChunkPos newChunk = ChunkPos.fromBlockPos(newPos.toBlockPos());
            ChunkPos lastChunk = lastChunkPositions.get(player.getUuid());
            
            if (lastChunk == null || !lastChunk.equals(newChunk)) {
                lastChunkPositions.put(player.getUuid(), newChunk);
                handleChunkChange(player, newChunk, lastChunk);
            }
        });
    }
    
    private void handleChunkChange(Player player, ChunkPos newChunk, ChunkPos oldChunk) {
        if (pendingTransfers.containsKey(player.getUuid())) {
            return; // Transfer already in progress
        }
        
        // Check if we're near a boundary
        coordinatorClient.requestChunkLoad(shardId, newChunk.x(), newChunk.z())
                .thenAccept(response -> {
                    if (response.getSuccess() && !response.getOwnerShardId().equals(shardId)) {
                        // This chunk belongs to another shard - initiate transfer
                        initiatePlayerTransfer(player, response.getOwnerShardId());
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error checking chunk ownership", ex);
                    return null;
                });
    }
    
    private void initiatePlayerTransfer(Player player, String targetShardId) {
        if (pendingTransfers.putIfAbsent(player.getUuid(), true) != null) {
            return; // Already transferring
        }
        
        logger.info("Initiating player transfer: {} from {} to {}", 
                player.getUsername(), shardId, targetShardId);
        
        // Serialize player state
        byte[] playerData = EntityStateSerializer.serializePlayer(player);
        
        // Request transfer approval from coordinator
        coordinatorClient.requestPlayerTransfer(
                        player.getUuid().toString(),
                        shardId,
                        targetShardId,
                        null) // PlayerState - simplified for now
                .thenAccept(response -> {
                    if (response.getAccepted()) {
                        executeTransfer(player, targetShardId, playerData);
                    } else {
                        logger.warn("Player transfer rejected: {}", response.getMessage());
                        pendingTransfers.remove(player.getUuid());
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Player transfer failed", ex);
                    pendingTransfers.remove(player.getUuid());
                    return null;
                });
    }
    
    private void executeTransfer(Player player, String targetShardId, byte[] playerData) {
        try {
            // Freeze player
            player.setInvisible(true);
            
            // TODO: Send player data to target shard
            // For now, we'll disconnect and let them reconnect
            
            // Confirm transfer
            coordinatorClient.confirmPlayerTransfer(
                            player.getUuid().toString(),
                            shardId,
                            targetShardId,
                            true)
                    .thenAccept(confirmation -> {
                        logger.info("Player transfer confirmed: {}", player.getUsername());
                        
                        // Kick player with transfer message
                        player.kick(net.kyori.adventure.text.Component.text(
                                "Transferring to another server..."));
                        
                        pendingTransfers.remove(player.getUuid());
                    });
            
        } catch (Exception e) {
            logger.error("Error during player transfer", e);
            pendingTransfers.remove(player.getUuid());
            player.setInvisible(false);
        }
    }
    
    public void removePlayer(UUID playerUuid) {
        lastChunkPositions.remove(playerUuid);
        pendingTransfers.remove(playerUuid);
    }
    
    public void start() {
    }
    
    public void stop() {
    }
}
