package com.shardedmc.shard.vanilla;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.ai.EntityAIGroupBuilder;
import net.minestom.server.entity.ai.goal.MeleeAttackGoal;
import net.minestom.server.entity.ai.goal.RandomStrollGoal;
import net.minestom.server.entity.ai.goal.FollowTargetGoal;
import net.minestom.server.entity.ai.goal.RandomLookAroundGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChunkLoadEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entity AI System for managing mob spawning and behaviors.
 * Implements natural spawning rules, AI for hostile/passive mobs, and villagers.
 */
public class EntityAI {
    private static final Logger logger = LoggerFactory.getLogger(EntityAI.class);
    
    // Mob caps per chunk
    private static final int HOSTILE_CAP_PER_CHUNK = 8;
    private static final int PASSIVE_CAP_PER_CHUNK = 4;
    private static final int VILLAGER_CAP_PER_CHUNK = 2;
    private static final int WATER_CAP_PER_CHUNK = 4;
    private static final int AMBIENT_CAP_PER_CHUNK = 8;
    
    // Global caps per instance
    private static final int GLOBAL_HOSTILE_CAP = 70;
    private static final int GLOBAL_PASSIVE_CAP = 10;
    private static final int GLOBAL_VILLAGER_CAP = 10;
    
    // Spawn ranges
    private static final int SPAWN_RADIUS_MIN = 24;
    private static final int SPAWN_RADIUS_MAX = 128;
    private static final int DESPAWN_RADIUS = 128;
    
    // Light level requirements
    private static final int HOSTILE_MAX_LIGHT = 7;
    private static final int PASSIVE_MIN_LIGHT = 9;
    
    // Tick intervals
    private static final long SPAWN_CHECK_INTERVAL = 400; // Every 20 seconds (400 ticks)
    private static final long DESPAWN_CHECK_INTERVAL = 400; // Every 20 seconds
    
    private final Random random = new Random();
    private final Map<UUID, EntityBehavior> behaviors = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnedMob> spawnedMobs = new ConcurrentHashMap<>();
    private final AtomicInteger spawnCounter = new AtomicInteger(0);
    
    // Cached per-chunk mob counts to avoid O(n²) iteration
    private final Map<Long, MobCounts> chunkMobCounts = new ConcurrentHashMap<>();
    // Cached global per-instance mob counts
    private final Map<Instance, MobCounts> globalMobCounts = new ConcurrentHashMap<>();
    
    // Mob spawn types mapping (simplified)
    private final Set<EntityType> hostileTypes = Set.of(
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
        EntityType.CREEPER, EntityType.ENDERMAN, EntityType.WITCH
    );
    private final Set<EntityType> passiveTypes = Set.of(
        EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
        EntityType.RABBIT, EntityType.HORSE
    );
    private final Set<EntityType> waterTypes = Set.of(
        EntityType.SQUID, EntityType.COD, EntityType.SALMON
    );
    private final Set<EntityType> ambientTypes = Set.of(
        EntityType.BAT
    );
    private final Set<EntityType> villagerTypes = Set.of(
        EntityType.VILLAGER
    );
    
    private long tickCounter = 0;
    private boolean enabled = true;
    
    public void register(GlobalEventHandler eventHandler) {
        // Listen for chunk loads to trigger spawning
        eventHandler.addListener(PlayerChunkLoadEvent.class, this::onChunkLoad);
        
        // Start tick task
        MinecraftServer.getSchedulerManager().buildTask(this::tick)
            .repeat(TaskSchedule.tick(1))
            .schedule();
        
        logger.info("Entity AI system registered");
    }
    
    public void tick() {
        tickCounter++;
        
        if (!enabled) return;
        
        // Update entity behaviors
        for (EntityBehavior behavior : behaviors.values()) {
            try {
                behavior.tick();
            } catch (Exception e) {
                logger.error("Error ticking entity behavior", e);
            }
        }
        
        // Periodic spawn check
        if (tickCounter % SPAWN_CHECK_INTERVAL == 0) {
            performNaturalSpawning();
        }
        
        // Periodic despawn check
        if (tickCounter % DESPAWN_CHECK_INTERVAL == 0) {
            performDespawnCheck();
        }
    }
    
    private void onChunkLoad(PlayerChunkLoadEvent event) {
        // Occasionally spawn mobs when players load chunks
        if (random.nextInt(100) < 5) { // 5% chance
            Player player = event.getPlayer();
            Instance instance = player.getInstance();
            if (instance == null) return;
            
            int chunkX = event.getChunkX();
            int chunkZ = event.getChunkZ();
            
            // Try spawning in this chunk
            trySpawnInChunk(instance, chunkX, chunkZ);
        }
    }
    
    /**
     * Perform natural spawning algorithm across all instances
     */
    private void performNaturalSpawning() {
        for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
            // Get all players in this instance
            Collection<Player> players = instance.getPlayers();
            if (players.isEmpty()) continue;
            
            // Count current mobs by category
            MobCounts counts = countMobs(instance);
            
            // Try spawning around each player
            for (Player player : players) {
                if (counts.hostile >= GLOBAL_HOSTILE_CAP && 
                    counts.passive >= GLOBAL_PASSIVE_CAP &&
                    counts.villager >= GLOBAL_VILLAGER_CAP) {
                    break; // All caps reached
                }
                
                trySpawnAroundPlayer(player, instance, counts);
            }
        }
    }
    
    private void trySpawnAroundPlayer(Player player, Instance instance, MobCounts counts) {
        Pos playerPos = player.getPosition();
        int playerChunkX = playerPos.chunkX();
        int playerChunkZ = playerPos.chunkZ();
        
        // Try a few spawn attempts
        for (int attempt = 0; attempt < 12; attempt++) {
            // Pick random chunk around player
            int dx = random.nextInt(17) - 8; // -8 to 8
            int dz = random.nextInt(17) - 8;
            int chunkX = playerChunkX + dx;
            int chunkZ = playerChunkZ + dz;
            
            // Skip if too close
            int distSq = dx * dx + dz * dz;
            if (distSq < (SPAWN_RADIUS_MIN / 16) * (SPAWN_RADIUS_MIN / 16)) continue;
            if (distSq > (SPAWN_RADIUS_MAX / 16) * (SPAWN_RADIUS_MAX / 16)) continue;
            
            Chunk chunk = instance.getChunk(chunkX, chunkZ);
            if (chunk == null) continue;
            
            // Try spawning in this chunk
            if (trySpawnInChunk(instance, chunkX, chunkZ)) {
                break;
            }
        }
    }
    
    private boolean trySpawnInChunk(Instance instance, int chunkX, int chunkZ) {
        MobCounts chunkCounts = countMobsInChunk(instance, chunkX, chunkZ);
        
        // Determine what type to spawn based on caps
        List<EntityType> possibleTypes = new ArrayList<>();
        
        if (chunkCounts.hostile < HOSTILE_CAP_PER_CHUNK) {
            possibleTypes.addAll(hostileTypes);
        }
        if (chunkCounts.passive < PASSIVE_CAP_PER_CHUNK) {
            possibleTypes.addAll(passiveTypes);
        }
        if (chunkCounts.villager < VILLAGER_CAP_PER_CHUNK) {
            possibleTypes.addAll(villagerTypes);
        }
        if (chunkCounts.water < WATER_CAP_PER_CHUNK) {
            possibleTypes.addAll(waterTypes);
        }
        if (chunkCounts.ambient < AMBIENT_CAP_PER_CHUNK) {
            possibleTypes.addAll(ambientTypes);
        }
        
        if (possibleTypes.isEmpty()) return false;
        
        // Pick random type
        EntityType type = possibleTypes.get(random.nextInt(possibleTypes.size()));
        
        // Find valid spawn position
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = chunkX * 16 + random.nextInt(16);
            int z = chunkZ * 16 + random.nextInt(16);
            int y = findValidSpawnY(instance, x, z, type);
            
            if (y > 0) {
                Pos spawnPos = new Pos(x + 0.5, y, z + 0.5);
                
                // Check light levels
                int lightLevel = getLightLevel(instance, x, y, z);
                if (isValidLightForMob(type, lightLevel)) {
                    spawnMob(instance, spawnPos, type);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private int findValidSpawnY(Instance instance, int x, int z, EntityType type) {
        // Find highest solid block
        for (int y = 319; y >= -64; y--) {
            Block block = instance.getBlock(x, y, z);
            Block blockAbove = instance.getBlock(x, y + 1, z);
            Block blockBelow = instance.getBlock(x, y - 1, z);
            
            if (!block.isAir() && blockAbove.isAir()) {
                // Check if there's space for the mob
                if (type == EntityType.BAT) {
                    return y + 1; // Bats spawn in air
                }
                
                if (isWaterMob(type)) {
                    if (blockBelow.isAir()) continue;
                    if (block.name().contains("water")) return y + 1;
                } else {
                    // Ground mob - needs solid ground below
                    if (!blockBelow.isAir() && !blockBelow.name().contains("water") && 
                        !blockBelow.name().contains("lava") && !blockBelow.name().contains("fire")) {
                        return y + 1;
                    }
                }
            }
        }
        return -1;
    }
    
    private boolean isWaterMob(EntityType type) {
        return waterTypes.contains(type);
    }
    
    private int getLightLevel(Instance instance, int x, int y, int z) {
        // Simplified - use sky light or block light
        // In Minestom, we can estimate based on time of day
        long time = instance.getWorldAge() % 24000;
        boolean isDay = time < 12300 || time > 23850;
        
        // Check if sky is visible
        boolean skyVisible = true;
        for (int checkY = y + 1; checkY < 320; checkY++) {
            if (!instance.getBlock(x, checkY, z).isAir()) {
                skyVisible = false;
                break;
            }
        }
        
        if (skyVisible && isDay) {
            return 15;
        } else if (skyVisible) {
            return 4; // Night time ambient
        } else {
            return 0; // Cave
        }
    }
    
    private boolean isValidLightForMob(EntityType type, int lightLevel) {
        if (hostileTypes.contains(type)) {
            return lightLevel <= HOSTILE_MAX_LIGHT;
        } else if (passiveTypes.contains(type) || villagerTypes.contains(type)) {
            return lightLevel >= PASSIVE_MIN_LIGHT;
        }
        return true; // Water and ambient mobs don't care
    }
    
    private static final MobCounts EMPTY_MOBCOUNTS = new MobCounts();
    
    private MobCounts countMobs(Instance instance) {
        return globalMobCounts.getOrDefault(instance, EMPTY_MOBCOUNTS);
    }
    
    private MobCounts countMobsInChunk(Instance instance, int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        return chunkMobCounts.getOrDefault(chunkKey, EMPTY_MOBCOUNTS);
    }
    
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    private void categorizeMob(EntityType type, MobCounts counts) {
        if (hostileTypes.contains(type)) counts.hostile++;
        else if (passiveTypes.contains(type)) counts.passive++;
        else if (villagerTypes.contains(type)) counts.villager++;
        else if (waterTypes.contains(type)) counts.water++;
        else if (ambientTypes.contains(type)) counts.ambient++;
    }
    
    private static class MobCounts {
        int hostile = 0;
        int passive = 0;
        int villager = 0;
        int water = 0;
        int ambient = 0;
    }
    
    /**
     * Despawn mobs that are too far from any player.
     * Optimized to avoid O(n²) entity×player nested loops by using chunk-based spatial checks.
     */
    private void performDespawnCheck() {
        for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
            Collection<Player> players = instance.getPlayers();
            if (players.isEmpty()) {
                // No players - despawn all non-persistent mobs
                for (Entity entity : new ArrayList<>(instance.getEntities())) {
                    if (entity instanceof Player) continue;
                    removeMobIfNotPersistent(entity.getUuid());
                }
                continue;
            }
            
            // Build set of chunk keys within despawn radius of any player
            // This avoids checking every entity against every player
            Set<Long> safeChunks = new HashSet<>();
            int chunkRadius = DESPAWN_RADIUS / 16 + 1;
            
            for (Player player : players) {
                Pos playerPos = player.getPosition();
                int playerChunkX = playerPos.chunkX();
                int playerChunkZ = playerPos.chunkZ();
                
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        // Only include chunks within actual despawn radius
                        double dist = Math.sqrt(dx * dx + dz * dz) * 16;
                        if (dist < DESPAWN_RADIUS + 16) { // +16 for chunk boundary margin
                            safeChunks.add(getChunkKey(playerChunkX + dx, playerChunkZ + dz));
                        }
                    }
                }
            }
            
            // Check each mob - only need to check chunk membership, not distance to each player
            for (Entity entity : new ArrayList<>(instance.getEntities())) {
                if (entity instanceof Player) continue;
                
                Pos entityPos = entity.getPosition();
                long entityChunkKey = getChunkKey(entityPos.chunkX(), entityPos.chunkZ());
                
                if (!safeChunks.contains(entityChunkKey)) {
                    removeMobIfNotPersistent(entity.getUuid());
                }
            }
        }
    }
    
    private void removeMobIfNotPersistent(UUID uuid) {
        EntityBehavior behavior = behaviors.get(uuid);
        if (behavior != null && !behavior.persistent) {
            removeMob(uuid);
        }
    }
    
    public EntityCreature spawnMob(Instance instance, Pos pos, EntityType type) {
        EntityCreature entity = new EntityCreature(type);
        entity.setInstance(instance, pos);
        
        // Set up AI
        setupAI(entity, type);
        
        int id = spawnCounter.incrementAndGet();
        SpawnedMob spawnedMob = new SpawnedMob(id, entity, type, instance, pos);
        spawnedMobs.put(entity.getUuid(), spawnedMob);
        behaviors.put(entity.getUuid(), new EntityBehavior(entity, false));
        
        // Update cached counts
        updateMobCounts(instance, pos.chunkX(), pos.chunkZ(), type, 1);
        
        logger.debug("Spawned {} at {} in {}", type.name(), pos, instance.getUniqueId());
        return entity;
    }
    
    private void updateMobCounts(Instance instance, int chunkX, int chunkZ, EntityType type, int delta) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        // Update per-chunk counts
        chunkMobCounts.computeIfAbsent(chunkKey, k -> new MobCounts());
        chunkMobCounts.compute(chunkKey, (k, counts) -> {
            if (counts == null) counts = new MobCounts();
            updateCountsByType(counts, type, delta);
            return counts;
        });
        
        // Update global counts
        globalMobCounts.computeIfAbsent(instance, k -> new MobCounts());
        globalMobCounts.compute(instance, (k, counts) -> {
            if (counts == null) counts = new MobCounts();
            updateCountsByType(counts, type, delta);
            return counts;
        });
    }
    
    private void updateCountsByType(MobCounts counts, EntityType type, int delta) {
        if (hostileTypes.contains(type)) counts.hostile += delta;
        else if (passiveTypes.contains(type)) counts.passive += delta;
        else if (villagerTypes.contains(type)) counts.villager += delta;
        else if (waterTypes.contains(type)) counts.water += delta;
        else if (ambientTypes.contains(type)) counts.ambient += delta;
    }
    
    public EntityCreature spawnPersistentMob(Instance instance, Pos pos, EntityType type) {
        EntityCreature entity = spawnMob(instance, pos, type);
        if (entity != null) {
            EntityBehavior behavior = behaviors.get(entity.getUuid());
            if (behavior != null) {
                behavior.persistent = true;
            }
        }
        return entity;
    }
    
    private void setupAI(EntityCreature entity, EntityType type) {
        EntityAIGroupBuilder builder = new EntityAIGroupBuilder();
        
        String typeName = type.name().toLowerCase();
        
        switch (typeName) {
            case "zombie":
            case "skeleton":
            case "spider":
            case "creeper":
            case "witch":
            case "enderman":
                // Hostile mobs: wander, look around, attack players, follow target
                builder.addGoalSelector(new RandomStrollGoal(entity, 20));
                builder.addGoalSelector(new RandomLookAroundGoal(entity, 20));
                builder.addGoalSelector(new FollowTargetGoal(entity, java.time.Duration.ofSeconds(5)));
                builder.addGoalSelector(new MeleeAttackGoal(entity, 1.2, java.time.Duration.ofMillis(500)));
                
                // Target players
                builder.addTargetSelector(new ClosestEntityTarget(entity, 16.0, 
                    target -> target instanceof Player));
                break;
                
            case "cow":
            case "pig":
            case "sheep":
            case "chicken":
            case "rabbit":
            case "horse":
                // Passive mobs: wander and look around
                builder.addGoalSelector(new RandomStrollGoal(entity, 20));
                builder.addGoalSelector(new RandomLookAroundGoal(entity, 20));
                break;
                
            case "villager":
                // Villagers: wander slowly, look around
                builder.addGoalSelector(new RandomStrollGoal(entity, 10));
                builder.addGoalSelector(new RandomLookAroundGoal(entity, 30));
                break;
                
            case "squid":
            case "cod":
            case "salmon":
                // Water mobs: wander in water
                builder.addGoalSelector(new RandomStrollGoal(entity, 10));
                break;
                
            default:
                // Default: just wander
                builder.addGoalSelector(new RandomStrollGoal(entity, 20));
                builder.addGoalSelector(new RandomLookAroundGoal(entity, 20));
                break;
        }
        
        entity.getAIGroups().add(builder.build());
    }
    
    public void removeMob(UUID uuid) {
        SpawnedMob spawnedMob = spawnedMobs.remove(uuid);
        if (spawnedMob != null && spawnedMob.entity != null) {
            Pos pos = spawnedMob.entity.getPosition();
            updateMobCounts(spawnedMob.instance, pos.chunkX(), pos.chunkZ(), spawnedMob.type, -1);
            spawnedMob.entity.remove();
        }
        behaviors.remove(uuid);
    }
    
    public void removeAllMobs(Instance instance) {
        List<UUID> toRemove = new ArrayList<>();
        for (SpawnedMob spawnedMob : spawnedMobs.values()) {
            if (spawnedMob.instance.equals(instance)) {
                toRemove.add(spawnedMob.entity.getUuid());
            }
        }
        for (UUID uuid : toRemove) {
            removeMob(uuid);
        }
    }
    
    public int getMobCount() {
        return spawnedMobs.size();
    }
    
    public int getMobCount(Instance instance) {
        return (int) spawnedMobs.values().stream()
            .filter(m -> m.instance.equals(instance))
            .count();
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Entity AI {}", enabled ? "enabled" : "disabled");
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Save mob data to file for persistence
     */
    public void saveMobs(Path file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(spawnedMobs.size());
            
            for (SpawnedMob mob : spawnedMobs.values()) {
                if (!mob.behavior.persistent) continue;
                
                out.writeUTF(mob.type.name());
                out.writeUTF(mob.instance.getUniqueId().toString());
                out.writeDouble(mob.entity.getPosition().x());
                out.writeDouble(mob.entity.getPosition().y());
                out.writeDouble(mob.entity.getPosition().z());
                out.writeFloat(mob.entity.getPosition().yaw());
                out.writeFloat(mob.entity.getPosition().pitch());
                out.writeFloat(mob.entity.getHealth());
            }
        }
    }
    
    /**
     * Load mob data from file
     */
    public void loadMobs(Path file) throws IOException {
        if (!Files.exists(file)) return;
        
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            int count = in.readInt();
            
            for (int i = 0; i < count; i++) {
                try {
                    String typeName = in.readUTF();
                    String instanceId = in.readUTF();
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();
                    float yaw = in.readFloat();
                    float pitch = in.readFloat();
                    float health = in.readFloat();
                    
                    EntityType type = EntityType.fromKey(typeName.toLowerCase());
                    if (type == null) continue;
                    
                    // Find instance
                    Instance instance = MinecraftServer.getInstanceManager().getInstances().stream()
                        .filter(inst -> inst.getUniqueId().toString().equals(instanceId))
                        .findFirst()
                        .orElse(null);
                    
                    if (instance == null) continue;
                    
                    Pos pos = new Pos(x, y, z, yaw, pitch);
                    EntityCreature entity = spawnPersistentMob(instance, pos, type);
                    if (entity != null) {
                        entity.setHealth(health);
                    }
                } catch (Exception e) {
                    logger.error("Error loading mob", e);
                }
            }
        }
    }
    
    private static class SpawnedMob {
        final int id;
        final EntityCreature entity;
        final EntityType type;
        final Instance instance;
        final Pos spawnPos;
        EntityBehavior behavior;
        
        SpawnedMob(int id, EntityCreature entity, EntityType type, Instance instance, Pos spawnPos) {
            this.id = id;
            this.entity = entity;
            this.type = type;
            this.instance = instance;
            this.spawnPos = spawnPos;
        }
    }
    
    private static class EntityBehavior {
        final EntityCreature entity;
        boolean persistent = false;
        long lastTick = 0;
        Vec lastPosition;
        
        EntityBehavior(EntityCreature entity, boolean persistent) {
            this.entity = entity;
            this.persistent = persistent;
            this.lastPosition = entity.getPosition().asVec();
        }
        
        void tick() {
            lastTick++;
            
            // Custom behavior tracking
            if (lastTick % 20 == 0) { // Every second
                Vec currentPos = entity.getPosition().asVec();
                
                // Check if stuck
                if (currentPos.distance(lastPosition) < 0.1 && !entity.isDead()) {
                    // Entity might be stuck, try to find new target
                    // This is handled by Minestom's pathfinding
                }
                
                lastPosition = currentPos;
            }
        }
    }
}
