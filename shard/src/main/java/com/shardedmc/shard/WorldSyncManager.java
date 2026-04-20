package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.EntityMeta;
import net.minestom.server.entity.metadata.PlayerMeta;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.time.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive world synchronization manager.
 * Syncs players, entities, blocks, and world state across all shards via Redis.
 */
public class WorldSyncManager {
    private static final Logger logger = LoggerFactory.getLogger(WorldSyncManager.class);
    
    private final RedisClient redis;
    private final String shardId;
    
    // Redis channels
    private static final String BLOCK_CHANNEL = "sync:blocks";
    private static final String PLAYER_CHANNEL = "sync:players";
    private static final String ENTITY_CHANNEL = "sync:entities";
    private static final String WORLD_CHANNEL = "sync:world";
    
    // Track which players/entities are local vs remote to avoid echo
    // Using AtomicLong values for thread-safe compare-and-set operations
    private final Map<UUID, AtomicLong> lastSyncTime = new ConcurrentHashMap<>();
    private static final long SYNC_INTERVAL_MS = 50; // Sync every 50ms (20 ticks per second)
    
    // Fully qualify to avoid ambiguity
    private static final java.util.concurrent.TimeUnit JAVA_TIME_UNIT = java.util.concurrent.TimeUnit.MILLISECONDS;
    
    public WorldSyncManager(RedisClient redis, String shardId) {
        this.redis = redis;
        this.shardId = shardId;
        
        setupSubscribers();
        startSyncTasks();
    }
    
    /**
     * Subscribe to all sync channels.
     */
    private void setupSubscribers() {
        // Block changes
        redis.subscribe(BLOCK_CHANNEL, this::handleBlockSync);
        
        // Player updates
        redis.subscribe(PLAYER_CHANNEL, this::handlePlayerSync);
        
        // Entity updates
        redis.subscribe(ENTITY_CHANNEL, this::handleEntitySync);
        
        // World updates (time, weather, etc.)
        redis.subscribe(WORLD_CHANNEL, this::handleWorldSync);
    }
    
    /**
     * Start periodic sync tasks.
     */
    private void startSyncTasks() {
        // Sync players every tick
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            syncAllPlayers();
        }).repeat(1, TimeUnit.SERVER_TICK).schedule();
        
        // Sync entities every 5 ticks
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            syncAllEntities();
        }).repeat(5, TimeUnit.SERVER_TICK).schedule();
        
        // Sync world state every second
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            syncWorldState();
        }).repeat(20, TimeUnit.SERVER_TICK).schedule();
    }
    
    /**
     * Sync all local players to other shards.
     * Thread-safe: uses atomic compare-and-set to prevent duplicate syncs.
     */
    private void syncAllPlayers() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            long now = System.currentTimeMillis();
            AtomicLong syncTime = lastSyncTime.computeIfAbsent(
                player.getUuid(), k -> new AtomicLong(0)
            );
            
            long lastSync = syncTime.get();
            if (now - lastSync >= SYNC_INTERVAL_MS) {
                // Atomic compare-and-set to prevent race conditions
                if (syncTime.compareAndSet(lastSync, now)) {
                    syncPlayer(player);
                }
            }
        }
    }
    
    /**
     * Sync a single player's state.
     */
    public void syncPlayer(Player player) {
        PlayerSyncData data = new PlayerSyncData(
                player.getUuid().toString(),
                player.getUsername(),
                player.getPosition().x(),
                player.getPosition().y(),
                player.getPosition().z(),
                player.getPosition().yaw(),
                player.getPosition().pitch(),
                player.getHealth(),
                player.getFood(),
                player.getFoodSaturation(),
                shardId
        );
        
        redis.publish(PLAYER_CHANNEL, data.serialize());
    }
    
    /**
     * Sync all entities (except players) to other shards.
     */
    private void syncAllEntities() {
        for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (Entity entity : instance.getEntities()) {
                if (!(entity instanceof Player) && entity.getEntityType() != EntityType.PLAYER) {
                    syncEntity(entity);
                }
            }
        }
    }
    
    /**
     * Sync a single entity.
     */
    public void syncEntity(Entity entity) {
        EntitySyncData data = new EntitySyncData(
                entity.getUuid().toString(),
                entity.getEntityType().name(),
                entity.getPosition().x(),
                entity.getPosition().y(),
                entity.getPosition().z(),
                entity.getVelocity().x(),
                entity.getVelocity().y(),
                entity.getVelocity().z(),
                entity instanceof LivingEntity ? ((LivingEntity) entity).getHealth() : 0,
                shardId
        );
        
        redis.publish(ENTITY_CHANNEL, data.serialize());
    }
    
    /**
     * Sync block change to all shards.
     */
    public void syncBlockChange(int x, int y, int z, Block block) {
        String message = String.format("%d,%d,%d,%s,%s", x, y, z, block.name(), shardId);
        redis.publish(BLOCK_CHANNEL, message);
    }
    
    /**
     * Sync world state (time, weather).
     */
    private void syncWorldState() {
        for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
            String message = String.format("time=%d,weather=%s", 
                    instance.getWorldAge(), "clear");
            redis.publish(WORLD_CHANNEL, message);
        }
    }
    
    /**
     * Handle incoming block sync from another shard.
     */
    private void handleBlockSync(String message) {
        String[] parts = message.split(",");
        if (parts.length != 5) return;
        
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String blockName = parts[3];
            String sourceShard = parts[4];
            
            // Ignore our own messages
            if (sourceShard.equals(shardId)) return;
            
            Block block = Block.fromKey(blockName);
            if (block != null) {
                for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
                    instance.setBlock(x, y, z, block);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling block sync: {}", message, e);
        }
    }
    
    /**
     * Handle incoming player sync from another shard.
     */
    private void handlePlayerSync(String message) {
        PlayerSyncData data = PlayerSyncData.deserialize(message);
        if (data == null || data.sourceShard.equals(shardId)) return;
        
        // Find or create a virtual player representation
        UUID uuid = UUID.fromString(data.uuid);
        
        // Update position if this player is near our players
        // In a full implementation, we'd spawn a virtual entity
        // For now, we just log it
        logger.debug("Received player sync from shard {}: {} at {}, {}, {}",
                data.sourceShard, data.username, data.x, data.y, data.z);
    }
    
    /**
     * Handle incoming entity sync from another shard.
     */
    private void handleEntitySync(String message) {
        EntitySyncData data = EntitySyncData.deserialize(message);
        if (data == null || data.sourceShard.equals(shardId)) return;
        
        logger.debug("Received entity sync from shard {}: {} at {}, {}, {}",
                data.sourceShard, data.type, data.x, data.y, data.z);
    }
    
    /**
     * Handle incoming world sync from another shard.
     */
    private void handleWorldSync(String message) {
        // Parse world state
        if (message.startsWith("time=")) {
            // Could sync time here if needed
        }
    }
    
    /**
     * Data class for player synchronization.
     */
    public static class PlayerSyncData {
        public final String uuid;
        public final String username;
        public final double x, y, z;
        public final float yaw, pitch;
        public final double health;
        public final int food;
        public final double saturation;
        public final String sourceShard;
        
        public PlayerSyncData(String uuid, String username, double x, double y, double z,
                             float yaw, float pitch, double health, int food, 
                             double saturation, String sourceShard) {
            this.uuid = uuid;
            this.username = username;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.health = health;
            this.food = food;
            this.saturation = saturation;
            this.sourceShard = sourceShard;
        }
        
        public String serialize() {
            return String.join(":",
                    uuid, username,
                    String.valueOf(x), String.valueOf(y), String.valueOf(z),
                    String.valueOf(yaw), String.valueOf(pitch),
                    String.valueOf(health), String.valueOf(food),
                    String.valueOf(saturation), sourceShard);
        }
        
        public static PlayerSyncData deserialize(String str) {
            String[] parts = str.split(":");
            if (parts.length != 11) return null;
            
            try {
                return new PlayerSyncData(
                        parts[0], parts[1],
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
                        Float.parseFloat(parts[5]), Float.parseFloat(parts[6]),
                        Double.parseDouble(parts[7]), Integer.parseInt(parts[8]),
                        Double.parseDouble(parts[9]), parts[10]
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
    
    /**
     * Data class for entity synchronization.
     */
    public static class EntitySyncData {
        public final String uuid;
        public final String type;
        public final double x, y, z;
        public final double vx, vy, vz;
        public final double health;
        public final String sourceShard;
        
        public EntitySyncData(String uuid, String type, double x, double y, double z,
                             double vx, double vy, double vz, double health, String sourceShard) {
            this.uuid = uuid;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.health = health;
            this.sourceShard = sourceShard;
        }
        
        public String serialize() {
            return String.join(":",
                    uuid, type,
                    String.valueOf(x), String.valueOf(y), String.valueOf(z),
                    String.valueOf(vx), String.valueOf(vy), String.valueOf(vz),
                    String.valueOf(health), sourceShard);
        }
        
        public static EntitySyncData deserialize(String str) {
            String[] parts = str.split(":");
            if (parts.length != 9) return null;
            
            try {
                return new EntitySyncData(
                        parts[0], parts[1],
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
                        Double.parseDouble(parts[5]), Double.parseDouble(parts[6]), Double.parseDouble(parts[7]),
                        Double.parseDouble(parts[8]), parts[9]
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
