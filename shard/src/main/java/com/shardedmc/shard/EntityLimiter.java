package com.shardedmc.shard;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable entity spawn limiter per chunk per player.
 * Tracks entity counts per chunk and cancels spawns when limits are exceeded.
 */
public class EntityLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityLimiter.class);

    // Default spawn limits per player
    private final int monsterLimit;
    private final int animalLimit;
    private final int waterLimit;
    private final int ambientLimit;

    // Entity type categorization
    private final Set<EntityType> monsterTypes = Set.of(
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
        EntityType.CREEPER, EntityType.ENDERMAN, EntityType.WITCH,
        EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY,
        EntityType.PHANTOM, EntityType.GHAST, EntityType.BLAZE,
        EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.SILVERFISH,
        EntityType.ENDERMITE, EntityType.VEX, EntityType.EVOKER,
        EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.RAVAGER,
        EntityType.HOGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.ZOGLIN, EntityType.ZOMBIE_VILLAGER
    );

    private final Set<EntityType> animalTypes = Set.of(
        EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
        EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
        EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.WOLF, EntityType.CAT,
        EntityType.OCELOT, EntityType.FOX, EntityType.PANDA, EntityType.BEE,
        EntityType.GOAT, EntityType.AXOLOTL, EntityType.TURTLE, EntityType.DOLPHIN,
        EntityType.MOOSHROOM, EntityType.POLAR_BEAR
    );

    private final Set<EntityType> waterTypes = Set.of(
        EntityType.SQUID, EntityType.COD, EntityType.SALMON,
        EntityType.TROPICAL_FISH, EntityType.PUFFERFISH, EntityType.GLOW_SQUID
    );

    private final Set<EntityType> ambientTypes = Set.of(
        EntityType.BAT
    );

    // Thread-safe per-chunk entity counts: chunkKey -> EntityCounts
    private final Map<Long, EntityCounts> chunkEntityCounts = new ConcurrentHashMap<>();

    public EntityLimiter() {
        this(20, 5, 2, 1);
    }

    public EntityLimiter(int monsterLimit, int animalLimit, int waterLimit, int ambientLimit) {
        this.monsterLimit = monsterLimit;
        this.animalLimit = animalLimit;
        this.waterLimit = waterLimit;
        this.ambientLimit = ambientLimit;
    }

    /**
     * Register event listeners with the global event handler.
     */
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(EntitySpawnEvent.class, this::onEntitySpawn);
        eventHandler.addListener(EntityDespawnEvent.class, event -> onEntityRemoved(event.getEntity()));
        LOGGER.info("EntityLimiter registered with limits: monsters={}, animals={}, water={}, ambient={}",
            monsterLimit, animalLimit, waterLimit, ambientLimit);
    }

    private void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        EntityType type = entity.getEntityType();

        // Skip players, items, and other non-living entities
        if (type == EntityType.PLAYER || type == EntityType.ITEM) {
            return;
        }

        // Only apply limits to categorized mob types
        if (!isMonitoredType(type)) {
            return;
        }

        // Get chunk coordinates
        int chunkX = entity.getPosition().chunkX();
        int chunkZ = entity.getPosition().chunkZ();
        long chunkKey = getChunkKey(chunkX, chunkZ);

        // Check limits
        EntityCounts counts = chunkEntityCounts.computeIfAbsent(chunkKey, k -> new EntityCounts());

        int currentCount = counts.getCount(type);
        int limit = getLimit(type);
        int playerCount = getOnlinePlayerCount();
        int effectiveLimit = limit * Math.max(playerCount, 1);

        if (currentCount >= effectiveLimit) {
            entity.remove();
            LOGGER.debug("Cancelled spawn of {} in chunk {}, {} (count={}/{})",
                type.name(), chunkX, chunkZ, currentCount, effectiveLimit);
            return;
        }

        // Increment count
        counts.increment(type);
    }

    /**
     * Decrement entity count when entity is removed.
     * Call this from EntityDespawnEvent or when manually removing entities.
     */
    public void onEntityRemoved(Entity entity) {
        EntityType type = entity.getEntityType();
        if (!isMonitoredType(type)) {
            return;
        }

        int chunkX = entity.getPosition().chunkX();
        int chunkZ = entity.getPosition().chunkZ();
        long chunkKey = getChunkKey(chunkX, chunkZ);

        EntityCounts counts = chunkEntityCounts.get(chunkKey);
        if (counts != null) {
            counts.decrement(type);
        }
    }

    private boolean isMonitoredType(EntityType type) {
        return monsterTypes.contains(type) || animalTypes.contains(type)
            || waterTypes.contains(type) || ambientTypes.contains(type);
    }

    private int getLimit(EntityType type) {
        if (monsterTypes.contains(type)) return monsterLimit;
        if (animalTypes.contains(type)) return animalLimit;
        if (waterTypes.contains(type)) return waterLimit;
        if (ambientTypes.contains(type)) return ambientLimit;
        return Integer.MAX_VALUE;
    }

    private int getOnlinePlayerCount() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().size();
    }

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Get current entity counts for a chunk.
     */
    public EntityCounts getChunkCounts(int chunkX, int chunkZ) {
        return chunkEntityCounts.getOrDefault(getChunkKey(chunkX, chunkZ), new EntityCounts());
    }

    /**
     * Thread-safe entity count tracker per chunk.
     */
    public static class EntityCounts {
        private final Map<EntityType, Integer> counts = new ConcurrentHashMap<>();

        public int getCount(EntityType type) {
            return counts.getOrDefault(type, 0);
        }

        public int getTotalMonsters() {
            return counts.entrySet().stream()
                .filter(e -> isMonster(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        }

        public int getTotalAnimals() {
            return counts.entrySet().stream()
                .filter(e -> isAnimal(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        }

        public int getTotalWater() {
            return counts.entrySet().stream()
                .filter(e -> isWater(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        }

        public int getTotalAmbient() {
            return counts.entrySet().stream()
                .filter(e -> isAmbient(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        }

        public void increment(EntityType type) {
            counts.merge(type, 1, Integer::sum);
        }

        public void decrement(EntityType type) {
            counts.merge(type, -1, (old, delta) -> {
                int result = old + delta;
                return result > 0 ? result : null;
            });
        }

        private static final Set<EntityType> MONSTER_TYPES = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.ENDERMAN, EntityType.WITCH
        );
        private static final Set<EntityType> ANIMAL_TYPES = Set.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN
        );
        private static final Set<EntityType> WATER_TYPES_SET = Set.of(
            EntityType.SQUID, EntityType.COD, EntityType.SALMON
        );
        private static final Set<EntityType> AMBIENT_TYPES = Set.of(
            EntityType.BAT
        );

        private static boolean isMonster(EntityType type) {
            return MONSTER_TYPES.contains(type);
        }

        private static boolean isAnimal(EntityType type) {
            return ANIMAL_TYPES.contains(type);
        }

        private static boolean isWater(EntityType type) {
            return WATER_TYPES_SET.contains(type);
        }

        private static boolean isAmbient(EntityType type) {
            return AMBIENT_TYPES.contains(type);
        }
    }
}
