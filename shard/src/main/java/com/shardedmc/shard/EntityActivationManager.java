package com.shardedmc.shard;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.entity.EntityDespawnEvent;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity activation range manager.
 * Entities outside activation range skip AI ticks, saving 50%+ entity CPU.
 */
public class EntityActivationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityActivationManager.class);

    // Activation ranges in blocks
    private final int animalRange;
    private final int monsterRange;
    private final int villagerRange;
    private final int miscRange;
    private final int waterRange;

    // Check interval in ticks
    private static final int CHECK_INTERVAL = 20; // Every 1 second

    // Entity categorization
    private final Set<EntityType> animalTypes = Set.of(
        EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
        EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
        EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.WOLF, EntityType.CAT,
        EntityType.OCELOT, EntityType.FOX, EntityType.PANDA, EntityType.BEE,
        EntityType.GOAT, EntityType.AXOLOTL, EntityType.TURTLE, EntityType.DOLPHIN,
        EntityType.MOOSHROOM, EntityType.POLAR_BEAR
    );

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

    private final Set<EntityType> villagerTypes = Set.of(
        EntityType.VILLAGER, EntityType.WANDERING_TRADER
    );

    private final Set<EntityType> waterTypes = Set.of(
        EntityType.SQUID, EntityType.COD, EntityType.SALMON,
        EntityType.TROPICAL_FISH, EntityType.PUFFERFISH, EntityType.GLOW_SQUID,
        EntityType.DOLPHIN, EntityType.TURTLE
    );

    private final Set<EntityType> miscTypes = Set.of(
        EntityType.ITEM, EntityType.EXPERIENCE_ORB, EntityType.ARROW,
        EntityType.SPECTRAL_ARROW, EntityType.TRIDENT, EntityType.SNOWBALL,
        EntityType.EGG, EntityType.ENDER_PEARL,
        EntityType.EXPERIENCE_BOTTLE, EntityType.FIREBALL, EntityType.SMALL_FIREBALL,
        EntityType.DRAGON_FIREBALL, EntityType.WITHER_SKULL, EntityType.FIREWORK_ROCKET
    );

    // Track active entities
    private final Map<UUID, Boolean> entityActive = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Entity> entityCache = new ConcurrentHashMap<>();

    // Performance tracking
    private long skippedTicks = 0;
    private long totalChecks = 0;

    public EntityActivationManager() {
        this(16, 24, 16, 8, 8);
    }

    public EntityActivationManager(int animalRange, int monsterRange, int villagerRange,
                                   int miscRange, int waterRange) {
        this.animalRange = animalRange;
        this.monsterRange = monsterRange;
        this.villagerRange = villagerRange;
        this.miscRange = miscRange;
        this.waterRange = waterRange;
    }

    /**
     * Register event listeners and start periodic activation checks.
     */
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(EntitySpawnEvent.class, this::onEntitySpawn);
        eventHandler.addListener(EntityDespawnEvent.class, this::onEntityDespawn);

        // Start periodic activation range checks
        MinecraftServer.getSchedulerManager().buildTask(this::checkActivationRanges)
            .repeat(TaskSchedule.tick(CHECK_INTERVAL))
            .schedule();

        LOGGER.info("EntityActivationManager registered with ranges: animals={}, monsters={}, villagers={}, misc={}, water={}",
            animalRange, monsterRange, villagerRange, miscRange, waterRange);
    }

    private void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        EntityType type = entity.getEntityType();

        // Only track relevant entity types
        if (type == EntityType.PLAYER) {
            return;
        }

        if (isTrackedType(type)) {
            trackedEntities.add(entity.getUuid());
            entityCache.put(entity.getUuid(), entity);
            // Newly spawned entities are active by default
            entityActive.put(entity.getUuid(), true);
        }
    }

    private void onEntityDespawn(EntityDespawnEvent event) {
        Entity entity = event.getEntity();
        trackedEntities.remove(entity.getUuid());
        entityActive.remove(entity.getUuid());
        entityCache.remove(entity.getUuid());
    }

    /**
     * Check activation ranges for all tracked entities.
     * Runs every CHECK_INTERVAL ticks.
     */
    private void checkActivationRanges() {
        var players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (players.isEmpty()) {
            // No players online, deactivate all entities
            for (UUID uuid : trackedEntities) {
                entityActive.put(uuid, false);
            }
            return;
        }

        for (UUID uuid : trackedEntities) {
            Entity entity = findEntityByUuid(uuid);
            if (entity == null || !entity.isActive()) {
                entityActive.remove(uuid);
                continue;
            }

            int range = getActivationRange(entity.getEntityType());
            boolean active = isNearAnyPlayer(entity, players, range);
            entityActive.put(uuid, active);
        }
    }

    /**
     * Check if an entity should tick its AI this frame.
     * Call this from entity tick handlers or AI systems.
     */
    public boolean shouldTickAI(Entity entity) {
        if (entity == null || entity.getEntityType() == EntityType.PLAYER) {
            return true;
        }

        if (!isTrackedType(entity.getEntityType())) {
            return true;
        }

        Boolean active = entityActive.get(entity.getUuid());
        totalChecks++;

        if (Boolean.FALSE.equals(active)) {
            skippedTicks++;
            return false;
        }

        return true;
    }

    /**
     * Check if entity is near any player within the specified range.
     */
    private boolean isNearAnyPlayer(Entity entity, java.util.Collection<Player> players, int range) {
        double rangeSq = (double) range * range;
        var entityPos = entity.getPosition();

        for (Player player : players) {
            if (player.getInstance() != entity.getInstance()) {
                continue;
            }
            double distSq = player.getPosition().distanceSquared(entityPos);
            if (distSq <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private int getActivationRange(EntityType type) {
        if (animalTypes.contains(type)) return animalRange;
        if (monsterTypes.contains(type)) return monsterRange;
        if (villagerTypes.contains(type)) return villagerRange;
        if (waterTypes.contains(type)) return waterRange;
        if (miscTypes.contains(type)) return miscRange;
        return monsterRange; // Default to monster range for unknown types
    }

    private boolean isTrackedType(EntityType type) {
        return animalTypes.contains(type) || monsterTypes.contains(type)
            || villagerTypes.contains(type) || waterTypes.contains(type)
            || miscTypes.contains(type);
    }

    private Entity findEntityByUuid(UUID uuid) {
        Entity cached = entityCache.get(uuid);
        if (cached != null && cached.isActive()) {
            return cached;
        }
        // Fallback: search across all instances
        entityCache.remove(uuid);
        for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (Entity entity : instance.getEntities()) {
                if (entity.getUuid().equals(uuid)) {
                    entityCache.put(uuid, entity);
                    return entity;
                }
            }
        }
        return null;
    }

    public long getSkippedTicks() {
        return skippedTicks;
    }

    public long getTotalChecks() {
        return totalChecks;
    }

    public double getSkipRate() {
        if (totalChecks == 0) return 0.0;
        return (double) skippedTicks / totalChecks;
    }

    public boolean isActive(UUID entityUuid) {
        return Boolean.TRUE.equals(entityActive.get(entityUuid));
    }
}
