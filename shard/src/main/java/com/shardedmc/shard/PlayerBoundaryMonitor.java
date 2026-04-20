package com.shardedmc.shard;

import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shared.RedisClient;
import com.shardedmc.shared.Vec3d;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.EventNode;
import net.minestom.server.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors player chunk boundaries and handles seamless transfers between shards.
 * Based on MultiPaper's player handoff mechanism:
 * 1. Detect when player crosses into another shard's chunk
 * 2. Save complete player state to Redis
 * 3. Notify target shard via Redis pub/sub
 * 4. Disconnect player
 * 5. Player reconnects to target shard, which loads state from Redis
 */
public class PlayerBoundaryMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PlayerBoundaryMonitor.class);
    private static final int BUFFER_CHUNKS = 2; // Start transfer 2 chunks before boundary
    private static final String TRANSFER_CHANNEL = "shard:transfers";
    private static final long TRANSFER_TIMEOUT_MS = 30000; // 30 seconds
    
    private final ShardCoordinatorClient coordinatorClient;
    private final RedisClient redisClient;
    private final String shardId;
    private final Map<UUID, ChunkPos> lastChunkPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingTransfers = new ConcurrentHashMap<>();
    
    public PlayerBoundaryMonitor(ShardCoordinatorClient coordinatorClient, RedisClient redisClient, String shardId) {
        this.coordinatorClient = coordinatorClient;
        this.redisClient = redisClient;
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
        
        // Check chunk ownership
        coordinatorClient.requestChunkLoad(shardId, newChunk.x(), newChunk.z())
                .thenAccept(response -> {
                    if (response.getSuccess() && !response.getOwnerShardId().equals(shardId)) {
                        // This chunk belongs to another shard - initiate transfer
                        logger.info("Player {} crossing into shard {} territory at chunk {}", 
                                player.getUsername(), response.getOwnerShardId(), newChunk);
                        initiatePlayerTransfer(player, response.getOwnerShardId(), newChunk);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error checking chunk ownership for player {}", player.getUsername(), ex);
                    return null;
                });
    }
    
    private void initiatePlayerTransfer(Player player, String targetShardId, ChunkPos targetChunk) {
        if (pendingTransfers.putIfAbsent(player.getUuid(), true) != null) {
            return; // Already transferring
        }
        
        logger.info("Initiating player transfer: {} from {} to {} (chunk: {})", 
                player.getUsername(), shardId, targetShardId, targetChunk);
        
        try {
            // 1. Serialize complete player state
            PlayerTransferState transferState = serializePlayerState(player, targetShardId);
            
            // 2. Save to Redis with expiration
            String transferKey = "transfer:" + player.getUuid().toString();
            redisClient.setex(transferKey, TRANSFER_TIMEOUT_MS / 1000, transferState.toJson());
            
            // 3. Update player routing in coordinator
            coordinatorClient.requestPlayerTransfer(
                            player.getUuid().toString(),
                            shardId,
                            targetShardId,
                            null)
                    .thenAccept(response -> {
                        if (response.getAccepted()) {
                            // 4. Notify target shard via Redis pub/sub
                            notifyTargetShard(player, targetShardId, transferState);
                            
                            // 5. Execute transfer
                            executeTransfer(player, targetShardId, targetChunk);
                        } else {
                            logger.warn("Player transfer rejected: {}", response.getMessage());
                            cleanupTransfer(player.getUuid());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("Player transfer failed for {}", player.getUsername(), ex);
                        cleanupTransfer(player.getUuid());
                        return null;
                    });
            
        } catch (Exception e) {
            logger.error("Error initiating transfer for {}", player.getUsername(), e);
            cleanupTransfer(player.getUuid());
        }
    }
    
    private void notifyTargetShard(Player player, String targetShardId, PlayerTransferState transferState) {
        String message = String.format("{\"type\":\"player_incoming\",\"player_uuid\":\"%s\",\"target_shard\":\"%s\",\"from_shard\":\"%s\",\"timestamp\":%d}",
                player.getUuid().toString(), targetShardId, shardId, System.currentTimeMillis());
        redisClient.publish(TRANSFER_CHANNEL, message);
        logger.debug("Notified shard {} about incoming player {}", targetShardId, player.getUsername());
    }
    
    private void executeTransfer(Player player, String targetShardId, ChunkPos targetChunk) {
        try {
            // Save final position and state
            PlayerTransferState finalState = serializePlayerState(player, targetShardId);
            String transferKey = "transfer:" + player.getUuid().toString();
            redisClient.setex(transferKey, TRANSFER_TIMEOUT_MS / 1000, finalState.toJson());
            
            // Confirm transfer with coordinator
            coordinatorClient.confirmPlayerTransfer(
                            player.getUuid().toString(),
                            shardId,
                            targetShardId,
                            true)
                    .thenAccept(confirmation -> {
                        logger.info("Transfer confirmed for {}, disconnecting...", player.getUsername());
                        
                        // Disconnect with transfer message
                        player.kick(net.kyori.adventure.text.Component.text(
                                "Transferring to another world region...\nPlease reconnect."));
                        
                        cleanupTransfer(player.getUuid());
                    });
            
        } catch (Exception e) {
            logger.error("Error during player transfer execution", e);
            cleanupTransfer(player.getUuid());
        }
    }
    
    /**
     * Serialize complete player state for transfer
     */
    private PlayerTransferState serializePlayerState(Player player, String targetShardId) {
        List<ItemData> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItemStack(i);
            if (!item.isAir()) {
                inventory.add(new ItemData(i, item.material().name(), item.amount()));
            }
        }
        
        return new PlayerTransferState(
                player.getUuid().toString(),
                player.getUsername(),
                new Vec3d(player.getPosition().x(), player.getPosition().y(), player.getPosition().z()),
                player.getHealth(),
                player.getFood(),
                player.getFoodSaturation(),
                inventory,
                targetShardId,
                System.currentTimeMillis()
        );
    }
    
    /**
     * Check if there's a pending transfer for a player (called on login)
     */
    public static Optional<PlayerTransferState> checkPendingTransfer(RedisClient redis, UUID playerUuid) {
        String transferKey = "transfer:" + playerUuid.toString();
        String data = redis.get(transferKey);
        if (data != null) {
            // Delete immediately to prevent reuse
            redis.del(transferKey);
            return Optional.of(PlayerTransferState.fromJson(data));
        }
        return Optional.empty();
    }
    
    private void cleanupTransfer(UUID playerUuid) {
        pendingTransfers.remove(playerUuid);
        lastChunkPositions.remove(playerUuid);
    }
    
    public void removePlayer(UUID playerUuid) {
        lastChunkPositions.remove(playerUuid);
        pendingTransfers.remove(playerUuid);
    }
    
    public void start() {
        logger.info("PlayerBoundaryMonitor started for shard {}", shardId);
    }
    
    public void stop() {
        pendingTransfers.clear();
        lastChunkPositions.clear();
    }
    
    /**
     * Data class for player transfer state
     */
    public static class PlayerTransferState {
        private final String uuid;
        private final String username;
        private final Vec3d position;
        private final float health;
        private final float food;
        private final float saturation;
        private final List<ItemData> inventory;
        private final String targetShardId;
        private final long timestamp;
        
        public PlayerTransferState(String uuid, String username, Vec3d position, 
                                   float health, float food, float saturation,
                                   List<ItemData> inventory, String targetShardId, long timestamp) {
            this.uuid = uuid;
            this.username = username;
            this.position = position;
            this.health = health;
            this.food = food;
            this.saturation = saturation;
            this.inventory = inventory;
            this.targetShardId = targetShardId;
            this.timestamp = timestamp;
        }
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"uuid\":\"").append(uuid).append("\",");
            sb.append("\"username\":\"").append(username).append("\",");
            sb.append("\"position\":{");
            sb.append("\"x\":").append(position.x()).append(",");
            sb.append("\"y\":").append(position.y()).append(",");
            sb.append("\"z\":").append(position.z()).append("},");
            sb.append("\"health\":").append(health).append(",");
            sb.append("\"food\":").append(food).append(",");
            sb.append("\"saturation\":").append(saturation).append(",");
            sb.append("\"inventory\":[");
            for (int i = 0; i < inventory.size(); i++) {
                if (i > 0) sb.append(",");
                ItemData item = inventory.get(i);
                sb.append("{");
                sb.append("\"slot\":").append(item.slot()).append(",");
                sb.append("\"material\":\"").append(item.material()).append("\",");
                sb.append("\"amount\":").append(item.amount());
                sb.append("}");
            }
            sb.append("],");
            sb.append("\"target_shard\":\"").append(targetShardId).append("\",");
            sb.append("\"timestamp\":").append(timestamp);
            sb.append("}");
            return sb.toString();
        }
        
        public static PlayerTransferState fromJson(String json) {
            // Simple JSON parsing - in production use a proper JSON library
            try {
                String uuid = extractString(json, "uuid");
                String username = extractString(json, "username");
                
                double x = extractDouble(json, "\"x\":");
                double y = extractDouble(json, "\"y\":");
                double z = extractDouble(json, "\"z\":");
                Vec3d position = new Vec3d(x, y, z);
                
                float health = extractFloat(json, "\"health\":");
                float food = extractFloat(json, "\"food\":");
                float saturation = extractFloat(json, "\"saturation\":");
                
                String targetShard = extractString(json, "target_shard");
                long timestamp = extractLong(json, "\"timestamp\":");
                
                // Parse inventory (simplified)
                List<ItemData> inventory = new ArrayList<>();
                
                return new PlayerTransferState(uuid, username, position, health, food, 
                        saturation, inventory, targetShard, timestamp);
            } catch (Exception e) {
                logger.error("Failed to parse transfer state: {}", json, e);
                return null;
            }
        }
        
        private static String extractString(String json, String key) {
            int start = json.indexOf("\"" + key + "\":\"");
            if (start == -1) return "";
            start += key.length() + 4;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        
        private static double extractDouble(String json, String key) {
            int start = json.indexOf(key);
            if (start == -1) return 0;
            start += key.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return Double.parseDouble(json.substring(start, end).trim());
        }
        
        private static float extractFloat(String json, String key) {
            return (float) extractDouble(json, key);
        }
        
        private static long extractLong(String json, String key) {
            int start = json.indexOf(key);
            if (start == -1) return 0;
            start += key.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return Long.parseLong(json.substring(start, end).trim());
        }
        
        // Getters
        public String uuid() { return uuid; }
        public String username() { return username; }
        public Vec3d position() { return position; }
        public float health() { return health; }
        public float food() { return food; }
        public float saturation() { return saturation; }
        public List<ItemData> inventory() { return inventory; }
        public String targetShardId() { return targetShardId; }
        public long timestamp() { return timestamp; }
    }
    
    public record ItemData(int slot, String material, int amount) {}
}
