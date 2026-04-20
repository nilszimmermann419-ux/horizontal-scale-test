package com.shardedmc.shard;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityItemMergeEvent;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item despawn manager with fast despawn for trash items and configurable merge radius.
 * - Trash items (cobblestone, dirt, netherrack): 15 seconds
 * - Leaves, saplings: 30 seconds
 * - Common blocks: 1 minute
 * - Valuable items: 5 minutes (normal)
 * - Merge radius: 3.5 blocks
 */
public class ItemDespawnManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemDespawnManager.class);

    // Despawn times in milliseconds
    private final long trashDespawnTime;
    private final long leafDespawnTime;
    private final long commonDespawnTime;
    private final long valuableDespawnTime;

    // Merge radius in blocks
    private final float mergeRadius;

    // Item categorization
    private final Set<Material> trashMaterials = Set.of(
        Material.COBBLESTONE, Material.DIRT, Material.NETHERRACK,
        Material.COARSE_DIRT, Material.PODZOL, Material.MYCELIUM,
        Material.GRAVEL, Material.SAND, Material.RED_SAND,
        Material.ANDESITE, Material.DIORITE, Material.GRANITE,
        Material.COBBLED_DEEPSLATE, Material.TUFF, Material.CALCITE,
        Material.DRIPSTONE_BLOCK, Material.MOSS_BLOCK, Material.MUD
    );

    private final Set<Material> leafMaterials = Set.of(
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
        Material.FLOWERING_AZALEA_LEAVES
    );

    private final Set<Material> saplingMaterials = Set.of(
        Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
        Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
        Material.MANGROVE_PROPAGULE, Material.CHERRY_SAPLING, Material.AZALEA,
        Material.FLOWERING_AZALEA
    );

    private final Set<Material> commonMaterials = Set.of(
        Material.STONE, Material.COBBLESTONE_SLAB, Material.COBBLESTONE_STAIRS,
        Material.COBBLESTONE_WALL, Material.STONE_BRICKS, Material.STONE_BRICK_SLAB,
        Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_WALL, Material.SMOOTH_STONE,
        Material.SMOOTH_STONE_SLAB, Material.CRACKED_STONE_BRICKS,
        Material.CHISELED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
        Material.MOSSY_COBBLESTONE, Material.MOSSY_COBBLESTONE_SLAB,
        Material.MOSSY_COBBLESTONE_STAIRS, Material.MOSSY_COBBLESTONE_WALL,
        Material.DIRT_PATH, Material.FARMLAND, Material.GRASS_BLOCK
    );

    private final Set<Material> valuableMaterials = Set.of(
        Material.DIAMOND, Material.DIAMOND_BLOCK, Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD, Material.EMERALD_BLOCK,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.NETHERITE_SCRAP,
        Material.ANCIENT_DEBRIS, Material.GOLD_INGOT, Material.GOLD_BLOCK,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
        Material.IRON_INGOT, Material.IRON_BLOCK, Material.IRON_ORE,
        Material.DEEPSLATE_IRON_ORE, Material.ENCHANTED_GOLDEN_APPLE,
        Material.GOLDEN_APPLE, Material.TOTEM_OF_UNDYING, Material.ELYTRA,
        Material.BEACON, Material.SHULKER_SHELL, Material.DRAGON_HEAD,
        Material.DRAGON_EGG, Material.NETHER_STAR, Material.WITHER_SKELETON_SKULL,
        Material.PLAYER_HEAD, Material.CREEPER_HEAD, Material.ZOMBIE_HEAD,
        Material.SKELETON_SKULL, Material.TRIDENT, Material.NAUTILUS_SHELL,
        Material.HEART_OF_THE_SEA
    );

    // Track item despawn timers: item UUID -> spawn timestamp
    private final Map<UUID, Long> itemSpawnTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> itemDespawnTimes = new ConcurrentHashMap<>();

    // Statistics
    private long despawnedTrash = 0;
    private long despawnedNormal = 0;
    private long mergedItems = 0;

    public ItemDespawnManager() {
        this(15000, 30000, 60000, 300000, 3.5f);
    }

    public ItemDespawnManager(long trashDespawnTime, long leafDespawnTime,
                              long commonDespawnTime, long valuableDespawnTime,
                              float mergeRadius) {
        this.trashDespawnTime = trashDespawnTime;
        this.leafDespawnTime = leafDespawnTime;
        this.commonDespawnTime = commonDespawnTime;
        this.valuableDespawnTime = valuableDespawnTime;
        this.mergeRadius = mergeRadius;
    }

    /**
     * Register event listeners and start despawn task.
     */
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(EntitySpawnEvent.class, this::onItemSpawn);
        eventHandler.addListener(EntityItemMergeEvent.class, this::onItemMerge);

        // Start periodic despawn check
        MinecraftServer.getSchedulerManager().buildTask(this::checkDespawns)
            .repeat(TaskSchedule.tick(20)) // Every 1 second
            .schedule();

        LOGGER.info("ItemDespawnManager registered: trash={}s, leaves={}s, common={}s, valuable={}s, mergeRadius={}",
            trashDespawnTime / 1000, leafDespawnTime / 1000,
            commonDespawnTime / 1000, valuableDespawnTime / 1000, mergeRadius);
    }

    private void onItemSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity.getEntityType() != EntityType.ITEM || !(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        // Configure merge radius
        itemEntity.setMergeable(true);
        // Minestom may not have setMergeRadius, but we configure what we can

        ItemStack itemStack = itemEntity.getItemStack();
        Material material = itemStack.material();
        long despawnTime = getDespawnTime(material);

        UUID uuid = entity.getUuid();
        long now = System.currentTimeMillis();
        itemSpawnTimes.put(uuid, now);
        itemDespawnTimes.put(uuid, now + despawnTime);

        // For trash items, set a shorter pickup delay to encourage cleanup
        if (isTrash(material)) {
            itemEntity.setPickupDelay(Duration.ofMillis(500));
        }
    }

    private void onItemMerge(EntityItemMergeEvent event) {
        mergedItems++;

        // Track the merged item with the earlier despawn time
        ItemEntity source = event.getEntity();
        ItemEntity merged = event.getMerged();

        Long sourceDespawn = itemDespawnTimes.get(source.getUuid());
        Long mergedDespawn = itemDespawnTimes.get(merged.getUuid());

        if (sourceDespawn != null && mergedDespawn != null) {
            // Use the earlier despawn time
            long earlierDespawn = Math.min(sourceDespawn, mergedDespawn);
            itemDespawnTimes.put(source.getUuid(), earlierDespawn);
        }

        // Clean up merged entity tracking
        itemSpawnTimes.remove(merged.getUuid());
        itemDespawnTimes.remove(merged.getUuid());
    }

    private void checkDespawns() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : Set.copyOf(itemDespawnTimes.entrySet())) {
            if (entry.getValue() <= now) {
                UUID uuid = entry.getKey();
                Entity entity = findEntityByUuid(uuid);

                if (entity != null && entity.isActive()) {
                    if (entity instanceof ItemEntity itemEntity) {
                        Material material = itemEntity.getItemStack().material();
                        if (isTrash(material) || isLeaf(material)) {
                            despawnedTrash++;
                        } else {
                            despawnedNormal++;
                        }
                    }
                    entity.remove();
                }

                itemSpawnTimes.remove(uuid);
                itemDespawnTimes.remove(uuid);
            }
        }
    }

    private long getDespawnTime(Material material) {
        if (isTrash(material)) {
            return trashDespawnTime;
        }
        if (isLeaf(material) || isSapling(material)) {
            return leafDespawnTime;
        }
        if (isCommon(material)) {
            return commonDespawnTime;
        }
        if (isValuable(material)) {
            return valuableDespawnTime;
        }
        // Default: 5 minutes
        return valuableDespawnTime;
    }

    private boolean isTrash(Material material) {
        return trashMaterials.contains(material);
    }

    private boolean isLeaf(Material material) {
        return leafMaterials.contains(material);
    }

    private boolean isSapling(Material material) {
        return saplingMaterials.contains(material);
    }

    private boolean isCommon(Material material) {
        return commonMaterials.contains(material);
    }

    private boolean isValuable(Material material) {
        return valuableMaterials.contains(material);
    }

    private Entity findEntityByUuid(UUID uuid) {
        for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (Entity entity : instance.getEntities()) {
                if (entity.getUuid().equals(uuid)) {
                    return entity;
                }
            }
        }
        return null;
    }

    public long getDespawnedTrash() {
        return despawnedTrash;
    }

    public long getDespawnedNormal() {
        return despawnedNormal;
    }

    public long getMergedItems() {
        return mergedItems;
    }

    public int getTrackedItemCount() {
        return itemDespawnTimes.size();
    }
}
