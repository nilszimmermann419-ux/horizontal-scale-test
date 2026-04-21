package com.shardedmc.shard;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import com.shardedmc.shared.RedisClient;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Adds vanilla-like survival features to the Minestom server.
 * Handles block breaking with drops, block placing, PvP combat, and health.
 */
public class VanillaFeatures {
    private static final Logger logger = LoggerFactory.getLogger(VanillaFeatures.class);
    
    private final Instance instance;
    private LightingEngine lightingEngine;
    private RedisClient redisClient;
    private final Random random = new Random();
    private static final String BLOCK_UPDATES_CHANNEL = "world:block_updates";
    
    public VanillaFeatures(Instance instance) {
        this.instance = instance;
    }
    
    public VanillaFeatures(Instance instance, RedisClient redisClient) {
        this.instance = instance;
        this.redisClient = redisClient;
    }
    
    public void register(GlobalEventHandler eventHandler) {
        register(eventHandler, null);
    }
    
    public void register(GlobalEventHandler eventHandler, LightingEngine lightingEngine) {
        this.lightingEngine = lightingEngine;
        // Block breaking with drops
        eventHandler.addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);
        
        // Block placing
        eventHandler.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
        
        // PvP combat
        eventHandler.addListener(EntityAttackEvent.class, this::onEntityAttack);
        
        // Item dropping
        eventHandler.addListener(ItemDropEvent.class, event -> {
            // Allow dropping items
            event.setCancelled(false);
        });
        
        // Item pickup
        eventHandler.addListener(PickupItemEvent.class, event -> {
            // Allow picking up items
            event.setCancelled(false);
        });
        
        // Block interaction (chests, doors, etc.)
        eventHandler.addListener(PlayerBlockInteractEvent.class, this::onBlockInteract);
        
        logger.info("Vanilla features registered");
    }
    
    /**
     * Handle block breaking with drops
     */
    private void onBlockBreak(PlayerBlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Pos pos = new Pos(event.getBlockPosition());
        
        // Drop items based on block type
        ItemStack[] drops = getBlockDrops(block);
        for (ItemStack drop : drops) {
            if (drop != null && !drop.isAir()) {
                dropItem(pos.add(0.5, 0.5, 0.5), drop);
            }
        }
        
        // Update lighting
        if (lightingEngine != null) {
            lightingEngine.updateBlockLight(
                    pos.blockX(), pos.blockY(), pos.blockZ(),
                    block, Block.AIR
            );
        }
        
        // Broadcast block removal to other shards
        if (redisClient != null) {
            String message = String.format("%d,%d,%d,%s", 
                pos.blockX(), pos.blockY(), pos.blockZ(), Block.AIR.name());
            redisClient.publish(BLOCK_UPDATES_CHANNEL, message);
        }
        
        logger.debug("Player {} broke {} at {}", player.getUsername(), block.name(), pos);
    }
    
    /**
     * Handle block placing
     */
    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        // Block placing is handled by Minestom by default
        // We just log it for debugging
        logger.debug("Player {} placed {} at {}", 
                event.getPlayer().getUsername(), 
                event.getBlock().name(),
                event.getBlockPosition());
        
        // Update lighting
        if (lightingEngine != null) {
            Pos pos = new Pos(event.getBlockPosition());
            lightingEngine.updateBlockLight(
                    pos.blockX(), pos.blockY(), pos.blockZ(),
                    Block.AIR, event.getBlock()
            );
        }
        
        // Broadcast block placement to other shards
        if (redisClient != null) {
            Pos pos = new Pos(event.getBlockPosition());
            String message = String.format("%d,%d,%d,%s", 
                pos.blockX(), pos.blockY(), pos.blockZ(), event.getBlock().name());
            redisClient.publish(BLOCK_UPDATES_CHANNEL, message);
        }
    }
    
    /**
     * Handle entity attacks (PvP combat)
     */
    private void onEntityAttack(EntityAttackEvent event) {
        Entity attacker = event.getEntity();
        Entity target = event.getTarget();
        
        if (attacker instanceof Player playerAttacker && target instanceof Player playerTarget) {
            // PvP combat
            float damage = calculateMeleeDamage(playerAttacker);
            playerTarget.damage(DamageType.PLAYER_ATTACK, damage);
            
            // Knockback
            Pos attackerPos = playerAttacker.getPosition();
            Pos targetPos = playerTarget.getPosition();
            double dx = targetPos.x() - attackerPos.x();
            double dz = targetPos.z() - attackerPos.z();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 0) {
                dx /= length;
                dz /= length;
                playerTarget.setVelocity(playerTarget.getVelocity().add((float)(dx * 0.4), 0.4f, (float)(dz * 0.4)));
            }
            
            logger.debug("Player {} attacked {} for {} damage", 
                    playerAttacker.getUsername(), 
                    playerTarget.getUsername(),
                    damage);
        }
    }
    
    /**
     * Handle block interactions
     */
    private void onBlockInteract(PlayerBlockInteractEvent event) {
        Block block = event.getBlock();
        
        // Handle crafting table
        if (block.compare(Block.CRAFTING_TABLE)) {
            event.getPlayer().sendMessage(Component.text("Crafting coming soon!"));
        }
        
        // Handle doors
        if (block.name().contains("door") || block.name().contains("trapdoor")) {
            // Toggle door state
            // Minestom handles this automatically for most blocks
        }
    }
    
    /**
     * Calculate melee damage based on held item
     * TODO: Extract to shared utility with CombatSystem to avoid duplication
     */
    private float calculateMeleeDamage(Player player) {
        ItemStack heldItem = player.getItemInMainHand();
        Material material = heldItem.material();
        
        // Base damage
        float damage = 1.0f;
        
        // Tool/weapon damage
        if (material == Material.DIAMOND_SWORD) damage = 7.0f;
        else if (material == Material.IRON_SWORD) damage = 6.0f;
        else if (material == Material.STONE_SWORD) damage = 5.0f;
        else if (material == Material.WOODEN_SWORD) damage = 4.0f;
        else if (material == Material.GOLDEN_SWORD) damage = 4.0f;
        else if (material == Material.NETHERITE_SWORD) damage = 8.0f;
        // Axes
        else if (material == Material.DIAMOND_AXE) damage = 6.0f;
        else if (material == Material.IRON_AXE) damage = 5.0f;
        else if (material == Material.STONE_AXE) damage = 4.0f;
        else if (material == Material.WOODEN_AXE) damage = 3.0f;
        else if (material == Material.GOLDEN_AXE) damage = 3.0f;
        else if (material == Material.NETHERITE_AXE) damage = 7.0f;
        // Pickaxes
        else if (material == Material.DIAMOND_PICKAXE) damage = 5.0f;
        else if (material == Material.IRON_PICKAXE) damage = 4.0f;
        else if (material == Material.STONE_PICKAXE) damage = 3.0f;
        else if (material == Material.WOODEN_PICKAXE) damage = 2.0f;
        else if (material == Material.GOLDEN_PICKAXE) damage = 2.0f;
        else if (material == Material.NETHERITE_PICKAXE) damage = 6.0f;
        // Shovels
        else if (material == Material.DIAMOND_SHOVEL) damage = 4.0f;
        else if (material == Material.IRON_SHOVEL) damage = 3.0f;
        else if (material == Material.STONE_SHOVEL) damage = 2.0f;
        else if (material == Material.WOODEN_SHOVEL) damage = 1.0f;
        else if (material == Material.GOLDEN_SHOVEL) damage = 1.0f;
        else if (material == Material.NETHERITE_SHOVEL) damage = 5.0f;
        // Tridents
        else if (material == Material.TRIDENT) damage = 9.0f;
        
        return damage;
    }
    
    /**
     * Get drops for a broken block
     */
    private ItemStack[] getBlockDrops(Block block) {
        String blockName = block.name();
        
        // Ores
        if (block == Block.COAL_ORE || block == Block.DEEPSLATE_COAL_ORE) {
            return new ItemStack[]{ItemStack.of(Material.COAL, 1 + random.nextInt(2))};
        }
        if (block == Block.IRON_ORE || block == Block.DEEPSLATE_IRON_ORE) {
            return new ItemStack[]{ItemStack.of(Material.RAW_IRON, 1)};
        }
        if (block == Block.GOLD_ORE || block == Block.DEEPSLATE_GOLD_ORE) {
            return new ItemStack[]{ItemStack.of(Material.RAW_GOLD, 1)};
        }
        if (block == Block.DIAMOND_ORE || block == Block.DEEPSLATE_DIAMOND_ORE) {
            return new ItemStack[]{ItemStack.of(Material.DIAMOND, 1)};
        }
        if (block == Block.EMERALD_ORE || block == Block.DEEPSLATE_EMERALD_ORE) {
            return new ItemStack[]{ItemStack.of(Material.EMERALD, 1)};
        }
        if (block == Block.LAPIS_ORE || block == Block.DEEPSLATE_LAPIS_ORE) {
            return new ItemStack[]{ItemStack.of(Material.LAPIS_LAZULI, 4 + random.nextInt(5))};
        }
        if (block == Block.REDSTONE_ORE || block == Block.DEEPSLATE_REDSTONE_ORE) {
            return new ItemStack[]{ItemStack.of(Material.REDSTONE, 4 + random.nextInt(3))};
        }
        if (block == Block.COPPER_ORE || block == Block.DEEPSLATE_COPPER_ORE) {
            return new ItemStack[]{ItemStack.of(Material.RAW_COPPER, 2 + random.nextInt(3))};
        }
        
        // Stone variants
        if (block == Block.STONE) {
            return new ItemStack[]{ItemStack.of(Material.COBBLESTONE, 1)};
        }
        if (block == Block.GRANITE) {
            return new ItemStack[]{ItemStack.of(Material.GRANITE, 1)};
        }
        if (block == Block.DIORITE) {
            return new ItemStack[]{ItemStack.of(Material.DIORITE, 1)};
        }
        if (block == Block.ANDESITE) {
            return new ItemStack[]{ItemStack.of(Material.ANDESITE, 1)};
        }
        
        // Dirt/Grass
        if (block == Block.GRASS_BLOCK) {
            return new ItemStack[]{ItemStack.of(Material.DIRT, 1)};
        }
        if (block == Block.DIRT) {
            return new ItemStack[]{ItemStack.of(Material.DIRT, 1)};
        }
        if (block == Block.COARSE_DIRT) {
            return new ItemStack[]{ItemStack.of(Material.COARSE_DIRT, 1)};
        }
        if (block == Block.PODZOL) {
            return new ItemStack[]{ItemStack.of(Material.PODZOL, 1)};
        }
        
        // Sand
        if (block == Block.SAND) {
            return new ItemStack[]{ItemStack.of(Material.SAND, 1)};
        }
        if (block == Block.RED_SAND) {
            return new ItemStack[]{ItemStack.of(Material.RED_SAND, 1)};
        }
        if (block == Block.GRAVEL) {
            return new ItemStack[]{ItemStack.of(Material.GRAVEL, 1)};
        }
        
        // Wood/Logs
        if (block.name().contains("log") || block.name().contains("stem") || block.name().contains("wood")) {
            return new ItemStack[]{ItemStack.of(getLogDropMaterial(block), 1)};
        }
        
        // Planks
        if (block.name().contains("planks")) {
            return new ItemStack[]{ItemStack.of(getPlankDropMaterial(block), 1)};
        }
        
        // Leaves
        if (block.name().contains("leaves")) {
            // 5% chance to drop sapling
            if (random.nextDouble() < 0.05) {
                return new ItemStack[]{ItemStack.of(getSaplingMaterial(block), 1)};
            }
            // 0.5% chance to drop apple from oak/dark oak
            if ((block == Block.OAK_LEAVES || block == Block.DARK_OAK_LEAVES) && random.nextDouble() < 0.005) {
                return new ItemStack[]{ItemStack.of(Material.APPLE, 1)};
            }
            return new ItemStack[]{};
        }
        
        // Glass - no drops
        if (block.name().contains("glass") && !block.name().contains("pane")) {
            return new ItemStack[]{};
        }
        
        // Default: drop the block itself
        Material dropMaterial = getMaterialFromBlock(block);
        if (dropMaterial != null) {
            return new ItemStack[]{ItemStack.of(dropMaterial, 1)};
        }
        
        return new ItemStack[]{};
    }
    
    /**
     * Drop an item at a position
     */
    private void dropItem(Pos pos, ItemStack itemStack) {
        ItemEntity itemEntity = new ItemEntity(itemStack);
        itemEntity.setInstance(instance, pos);
        itemEntity.setVelocity(new net.minestom.server.coordinate.Vec(
                (random.nextDouble() - 0.5) * 0.2,
                0.2,
                (random.nextDouble() - 0.5) * 0.2
        ));
        itemEntity.setPickupDelay(java.time.Duration.ofSeconds(1));
    }
    
    /**
     * Get log material from block
     */
    private Material getLogDropMaterial(Block block) {
        if (block == Block.OAK_LOG) return Material.OAK_LOG;
        if (block == Block.SPRUCE_LOG) return Material.SPRUCE_LOG;
        if (block == Block.BIRCH_LOG) return Material.BIRCH_LOG;
        if (block == Block.JUNGLE_LOG) return Material.JUNGLE_LOG;
        if (block == Block.ACACIA_LOG) return Material.ACACIA_LOG;
        if (block == Block.DARK_OAK_LOG) return Material.DARK_OAK_LOG;
        if (block == Block.MANGROVE_LOG) return Material.MANGROVE_LOG;
        if (block == Block.CHERRY_LOG) return Material.CHERRY_LOG;
        return Material.OAK_LOG;
    }
    
    /**
     * Get plank material from block
     */
    private Material getPlankDropMaterial(Block block) {
        if (block == Block.OAK_PLANKS) return Material.OAK_PLANKS;
        if (block == Block.SPRUCE_PLANKS) return Material.SPRUCE_PLANKS;
        if (block == Block.BIRCH_PLANKS) return Material.BIRCH_PLANKS;
        if (block == Block.JUNGLE_PLANKS) return Material.JUNGLE_PLANKS;
        if (block == Block.ACACIA_PLANKS) return Material.ACACIA_PLANKS;
        if (block == Block.DARK_OAK_PLANKS) return Material.DARK_OAK_PLANKS;
        return Material.OAK_PLANKS;
    }
    
    /**
     * Get sapling material from leaves
     */
    private Material getSaplingMaterial(Block block) {
        if (block == Block.OAK_LEAVES) return Material.OAK_SAPLING;
        if (block == Block.SPRUCE_LEAVES) return Material.SPRUCE_SAPLING;
        if (block == Block.BIRCH_LEAVES) return Material.BIRCH_SAPLING;
        if (block == Block.JUNGLE_LEAVES) return Material.JUNGLE_SAPLING;
        if (block == Block.ACACIA_LEAVES) return Material.ACACIA_SAPLING;
        if (block == Block.DARK_OAK_LEAVES) return Material.DARK_OAK_SAPLING;
        return Material.OAK_SAPLING;
    }
    
    /**
     * Get material from block for drops
     */
    private Material getMaterialFromBlock(Block block) {
        // Generic drop - just return null for now
        // Most blocks are handled specifically in getBlockDrops()
        return null;
    }
    
    /**
     * Give starter items to a new player
     */
    public static void giveStarterItems(Player player) {
        player.getInventory().addItemStack(ItemStack.of(Material.OAK_LOG, 16));
        player.getInventory().addItemStack(ItemStack.of(Material.OAK_PLANKS, 32));
        player.getInventory().addItemStack(ItemStack.of(Material.CRAFTING_TABLE, 1));
        player.getInventory().addItemStack(ItemStack.of(Material.STICK, 16));
        player.getInventory().addItemStack(ItemStack.of(Material.WOODEN_PICKAXE, 1));
        player.getInventory().addItemStack(ItemStack.of(Material.WOODEN_AXE, 1));
        player.getInventory().addItemStack(ItemStack.of(Material.WOODEN_SWORD, 1));
        player.getInventory().addItemStack(ItemStack.of(Material.BREAD, 16));
        player.getInventory().addItemStack(ItemStack.of(Material.TORCH, 32));
        
        logger.info("Gave starter items to player: {}", player.getUsername());
    }
}
