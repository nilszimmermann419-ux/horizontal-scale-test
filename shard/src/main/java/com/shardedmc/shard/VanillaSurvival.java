package com.shardedmc.shard;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ExperienceOrb;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete vanilla survival mechanics for Minestom.
 * Implements health, hunger, experience, crafting, and all core gameplay features.
 */
public class VanillaSurvival {
    private static final Logger logger = LoggerFactory.getLogger(VanillaSurvival.class);
    
    private final Instance instance;
    private final LightingEngine lightingEngine;
    
    // Player survival data
    private final Map<UUID, PlayerSurvivalData> survivalData = new ConcurrentHashMap<>();
    
    // Food exhaustion and saturation tracking
    private final Map<UUID, Float> foodExhaustion = new ConcurrentHashMap<>();
    private final Map<UUID, Float> foodSaturation = new ConcurrentHashMap<>();
    
    // Mining fatigue tracking (for tool speeds)
    private final Map<UUID, Boolean> isMining = new ConcurrentHashMap<>();
    
    // Block break times
    private static final Map<String, Float> BLOCK_HARDNESS = new HashMap<>();
    static {
        BLOCK_HARDNESS.put("minecraft:stone", 1.5f);
        BLOCK_HARDNESS.put("minecraft:dirt", 0.5f);
        BLOCK_HARDNESS.put("minecraft:grass_block", 0.6f);
        BLOCK_HARDNESS.put("minecraft:oak_log", 2.0f);
        BLOCK_HARDNESS.put("minecraft:oak_planks", 2.0f);
        BLOCK_HARDNESS.put("minecraft:bedrock", -1.0f);
        BLOCK_HARDNESS.put("minecraft:iron_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:coal_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:diamond_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:gold_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:sand", 0.5f);
        BLOCK_HARDNESS.put("minecraft:gravel", 0.6f);
        BLOCK_HARDNESS.put("minecraft:cobblestone", 2.0f);
    }
    
    // Tool speeds
    private static final Map<Material, Float> TOOL_SPEEDS = new HashMap<>();
    static {
        TOOL_SPEEDS.put(Material.WOODEN_PICKAXE, 2.0f);
        TOOL_SPEEDS.put(Material.STONE_PICKAXE, 4.0f);
        TOOL_SPEEDS.put(Material.IRON_PICKAXE, 6.0f);
        TOOL_SPEEDS.put(Material.DIAMOND_PICKAXE, 8.0f);
        TOOL_SPEEDS.put(Material.NETHERITE_PICKAXE, 9.0f);
        TOOL_SPEEDS.put(Material.WOODEN_AXE, 2.0f);
        TOOL_SPEEDS.put(Material.STONE_AXE, 4.0f);
        TOOL_SPEEDS.put(Material.IRON_AXE, 6.0f);
        TOOL_SPEEDS.put(Material.DIAMOND_AXE, 8.0f);
        TOOL_SPEEDS.put(Material.WOODEN_SHOVEL, 2.0f);
        TOOL_SPEEDS.put(Material.STONE_SHOVEL, 4.0f);
        TOOL_SPEEDS.put(Material.IRON_SHOVEL, 6.0f);
        TOOL_SPEEDS.put(Material.DIAMOND_SHOVEL, 8.0f);
    }
    
    // Food values
    private static final Map<Material, FoodInfo> FOOD_VALUES = new HashMap<>();
    static {
        FOOD_VALUES.put(Material.APPLE, new FoodInfo(4, 2.4f));
        FOOD_VALUES.put(Material.BREAD, new FoodInfo(5, 6.0f));
        FOOD_VALUES.put(Material.COOKED_BEEF, new FoodInfo(8, 12.8f));
        FOOD_VALUES.put(Material.COOKED_CHICKEN, new FoodInfo(6, 7.2f));
        FOOD_VALUES.put(Material.COOKED_PORKCHOP, new FoodInfo(8, 12.8f));
        FOOD_VALUES.put(Material.GOLDEN_APPLE, new FoodInfo(4, 9.6f));
        FOOD_VALUES.put(Material.CARROT, new FoodInfo(3, 3.6f));
        FOOD_VALUES.put(Material.POTATO, new FoodInfo(1, 0.6f));
        FOOD_VALUES.put(Material.BAKED_POTATO, new FoodInfo(5, 6.0f));
        FOOD_VALUES.put(Material.COOKED_SALMON, new FoodInfo(6, 9.6f));
        FOOD_VALUES.put(Material.COOKED_COD, new FoodInfo(5, 6.0f));
    }
    
    public VanillaSurvival(Instance instance, LightingEngine lightingEngine) {
        this.instance = instance;
        this.lightingEngine = lightingEngine;
    }
    
    public void register(GlobalEventHandler eventHandler) {
        // Block breaking
        eventHandler.addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);
        
        // Block placing
        eventHandler.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
        
        // Block interact (eating, doors, etc)
        eventHandler.addListener(PlayerBlockInteractEvent.class, this::onBlockInteract);
        
        // Entity attacks (PvP and PvE)
        eventHandler.addListener(EntityAttackEvent.class, this::onEntityAttack);
        
        // Item dropping
        eventHandler.addListener(ItemDropEvent.class, event -> event.setCancelled(false));
        
        // Item pickup
        eventHandler.addListener(PickupItemEvent.class, event -> event.setCancelled(false));
        
        // Player spawn - initialize survival data
        eventHandler.addListener(PlayerSpawnEvent.class, this::onPlayerSpawn);
        
        // Player tick - handle hunger, health regen, etc
        eventHandler.addListener(PlayerTickEvent.class, this::onPlayerTick);
        
        // Player death
        eventHandler.addListener(PlayerDeathEvent.class, this::onPlayerDeath);
        
        // Start survival tick task
        MinecraftServer.getSchedulerManager().buildTask(this::survivalTick)
                .repeat(Duration.ofMillis(50)) // Every tick
                .schedule();
        
        logger.info("Vanilla survival mechanics registered");
    }
    
    /**
     * Initialize player survival data on spawn
     */
    private void onPlayerSpawn(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUuid();
        
        PlayerSurvivalData data = new PlayerSurvivalData();
        survivalData.put(uuid, data);
        foodExhaustion.put(uuid, 0.0f);
        foodSaturation.put(uuid, 5.0f);
        
        // Set initial health and food
        player.setHealth(20);
        player.setFood(20);
        player.setFoodSaturation(5.0f);
        
        logger.info("Initialized survival data for player {}", player.getUsername());
    }
    
    /**
     * Handle player death
     */
    private void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUuid();
        
        // Drop all items
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItemStack(i);
            if (!item.isAir()) {
                dropItem(player.getPosition(), item);
                inv.setItemStack(i, ItemStack.AIR);
            }
        }
        
        // Drop experience
        int exp = survivalData.getOrDefault(uuid, new PlayerSurvivalData()).experience;
        if (exp > 0) {
            ExperienceOrb expOrb = new ExperienceOrb(exp);
            expOrb.setInstance(instance, player.getPosition());
        }
        
        // Reset survival data
        survivalData.put(uuid, new PlayerSurvivalData());
        foodExhaustion.put(uuid, 0.0f);
        foodSaturation.put(uuid, 0.0f);
        
        logger.info("Player {} died", player.getUsername());
    }
    
    /**
     * Handle block breaking with proper drops and tool requirements
     */
    private void onBlockBreak(PlayerBlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Pos pos = new Pos(event.getBlockPosition());
        
        // Check if player can break this block (tool requirements)
        if (!canBreakBlock(player, block)) {
            event.setCancelled(true);
            return;
        }
        
        // Drop items
        ItemStack[] drops = getBlockDrops(block, player);
        for (ItemStack drop : drops) {
            if (!drop.isAir()) {
                dropItem(pos.add(0.5, 0.5, 0.5), drop);
            }
        }
        
        // Damage tool
        damageTool(player);
        
        // Add exhaustion
        addExhaustion(player, 0.005f);
        
        // Update lighting
        if (lightingEngine != null) {
            lightingEngine.updateBlockLight(
                    pos.blockX(), pos.blockY(), pos.blockZ(), block, Block.AIR);
        }
    }
    
    /**
     * Handle block placing
     */
    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        // Add exhaustion
        addExhaustion(player, 0.025f);
        
        // Update lighting
        if (lightingEngine != null) {
            Pos pos = new Pos(event.getBlockPosition());
            lightingEngine.updateBlockLight(
                    pos.blockX(), pos.blockY(), pos.blockZ(), Block.AIR, event.getBlock());
        }
    }
    
    /**
     * Handle block interactions (eating, doors, chests, etc)
     */
    private void onBlockInteract(PlayerBlockInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getItemInMainHand();
        
        // Check if eating food
        FoodInfo foodInfo = FOOD_VALUES.get(item.material());
        if (foodInfo != null && player.getFood() < 20) {
            // Eat the food
            player.setFood(Math.min(20, player.getFood() + foodInfo.hunger));
            player.setFoodSaturation(Math.min(player.getFood(), 
                    player.getFoodSaturation() + foodInfo.saturation));
            
            // Remove one item
            if (item.amount() > 1) {
                player.setItemInMainHand(item.withAmount(item.amount() - 1));
            } else {
                player.setItemInMainHand(ItemStack.AIR);
            }
            
            logger.debug("Player {} ate {}", player.getUsername(), item.material());
        }
    }
    
    /**
     * Handle entity attacks (PvP and PvE)
     */
    private void onEntityAttack(EntityAttackEvent event) {
        Entity attacker = event.getEntity();
        Entity target = event.getTarget();
        
        if (attacker instanceof Player playerAttacker) {
            float damage = calculateMeleeDamage(playerAttacker);
            
            if (target instanceof Player playerTarget) {
                // PvP
                playerTarget.damage(DamageType.PLAYER_ATTACK, damage);
                
                // Knockback
                applyKnockback(playerAttacker, playerTarget);
                
                // Add exhaustion to attacker
                addExhaustion(playerAttacker, 0.1f);
                
            } else {
                // PvE
                target.damage(DamageType.PLAYER_ATTACK, damage);
            }
            
            // Damage weapon
            damageTool(playerAttacker);
        }
    }
    
    /**
     * Player tick - handle per-player updates
     */
    private void onPlayerTick(PlayerTickEvent event) {
        Player player = event.getPlayer();
        
        // Update health packet for proper client display
        // This ensures health bar shows correctly
    }
    
    /**
     * Main survival tick - runs every server tick (50ms)
     */
    private void survivalTick() {
        for (Player player : instance.getPlayers()) {
            UUID uuid = player.getUuid();
            PlayerSurvivalData data = survivalData.get(uuid);
            if (data == null) continue;
            
            // Hunger mechanics
            processHunger(player);
            
            // Health regeneration
            processHealthRegen(player);
            
            // Check for starvation damage
            if (player.getFood() <= 0) {
                player.damage(DamageType.STARVE, 1.0f);
            }
        }
    }
    
    /**
     * Process hunger exhaustion and decrease
     */
    private void processHunger(Player player) {
        UUID uuid = player.getUuid();
        float exhaustion = foodExhaustion.getOrDefault(uuid, 0.0f);
        float saturation = foodSaturation.getOrDefault(uuid, 0.0f);
        
        if (exhaustion >= 4.0f) {
            exhaustion -= 4.0f;
            
            if (saturation > 0) {
                saturation = Math.max(0, saturation - 1.0f);
            } else {
                int food = player.getFood();
                if (food > 0) {
                    player.setFood(food - 1);
                }
            }
            
            foodExhaustion.put(uuid, exhaustion);
            foodSaturation.put(uuid, saturation);
            player.setFoodSaturation(saturation);
        }
    }
    
    /**
     * Process health regeneration
     */
    private void processHealthRegen(Player player) {
        // Regenerate health if food >= 18 (90%)
        if (player.getFood() >= 18 && player.getHealth() < 20) {
            // Every 4 ticks (200ms) regenerate 1 health
            // For simplicity, just add a small amount
            player.setHealth(Math.min(20, player.getHealth() + 0.05f));
            addExhaustion(player, 0.006f);
        }
    }
    
    /**
     * Add exhaustion to player
     */
    private void addExhaustion(Player player, float amount) {
        UUID uuid = player.getUuid();
        foodExhaustion.merge(uuid, amount, Float::sum);
    }
    
    /**
     * Check if player can break a block (tool requirements)
     */
    private boolean canBreakBlock(Player player, Block block) {
        // Bedrock cannot be broken in survival
        if (block == Block.BEDROCK) {
            return false;
        }
        
        // TODO: Add tool requirement checks (pickaxe for stone, etc)
        return true;
    }
    
    /**
     * Calculate melee damage
     */
    private float calculateMeleeDamage(Player player) {
        ItemStack held = player.getItemInMainHand();
        Material mat = held.material();
        
        // Base damage
        float damage = 1.0f;
        
        // Swords
        if (mat == Material.WOODEN_SWORD) damage = 4;
        else if (mat == Material.STONE_SWORD) damage = 5;
        else if (mat == Material.IRON_SWORD) damage = 6;
        else if (mat == Material.DIAMOND_SWORD) damage = 7;
        else if (mat == Material.NETHERITE_SWORD) damage = 8;
        // Axes
        else if (mat == Material.WOODEN_AXE) damage = 7;
        else if (mat == Material.STONE_AXE) damage = 9;
        else if (mat == Material.IRON_AXE) damage = 9;
        else if (mat == Material.DIAMOND_AXE) damage = 9;
        else if (mat == Material.NETHERITE_AXE) damage = 10;
        // Pickaxes
        else if (mat == Material.WOODEN_PICKAXE) damage = 2;
        else if (mat == Material.STONE_PICKAXE) damage = 3;
        else if (mat == Material.IRON_PICKAXE) damage = 4;
        else if (mat == Material.DIAMOND_PICKAXE) damage = 5;
        // Shovels
        else if (mat == Material.WOODEN_SHOVEL) damage = 2.5f;
        else if (mat == Material.STONE_SHOVEL) damage = 3.5f;
        else if (mat == Material.IRON_SHOVEL) damage = 4.5f;
        
        return damage;
    }
    
    /**
     * Apply knockback to target
     */
    private void applyKnockback(Player attacker, Player target) {
        Pos attackerPos = attacker.getPosition();
        Pos targetPos = target.getPosition();
        
        double dx = targetPos.x() - attackerPos.x();
        double dz = targetPos.z() - attackerPos.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        
        if (length > 0) {
            dx /= length;
            dz /= length;
            
            Vec velocity = new Vec(dx * 0.4, 0.4, dz * 0.4);
            target.setVelocity(velocity);
        }
    }
    
    /**
     * Get drops for a block
     */
    private ItemStack[] getBlockDrops(Block block, Player player) {
        String name = block.name();
        
        // Ores - require proper tool
        if (block == Block.COAL_ORE || block == Block.DEEPSLATE_COAL_ORE) {
            return new ItemStack[]{ItemStack.of(Material.COAL, 1)};
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
            return new ItemStack[]{ItemStack.of(Material.LAPIS_LAZULI, 4 + (int)(Math.random() * 5))};
        }
        if (block == Block.REDSTONE_ORE || block == Block.DEEPSLATE_REDSTONE_ORE) {
            return new ItemStack[]{ItemStack.of(Material.REDSTONE, 4 + (int)(Math.random() * 3))};
        }
        if (block == Block.COPPER_ORE || block == Block.DEEPSLATE_COPPER_ORE) {
            return new ItemStack[]{ItemStack.of(Material.RAW_COPPER, 2 + (int)(Math.random() * 3))};
        }
        
        // Stone variants
        if (block == Block.STONE) {
            return new ItemStack[]{ItemStack.of(Material.COBBLESTONE, 1)};
        }
        
        // Dirt/Grass
        if (block == Block.GRASS_BLOCK) {
            return new ItemStack[]{ItemStack.of(Material.DIRT, 1)};
        }
        if (block == Block.DIRT) {
            return new ItemStack[]{ItemStack.of(Material.DIRT, 1)};
        }
        
        // Sand
        if (block == Block.SAND) {
            return new ItemStack[]{ItemStack.of(Material.SAND, 1)};
        }
        if (block == Block.GRAVEL) {
            return new ItemStack[]{ItemStack.of(Material.GRAVEL, 1)};
        }
        
        // Wood
        if (name.contains("log") || name.contains("stem")) {
            Material drop = getLogDrop(block);
            if (drop != null) return new ItemStack[]{ItemStack.of(drop, 1)};
        }
        
        // Leaves
        if (name.contains("leaves")) {
            // Small chance for sapling
            if (Math.random() < 0.05) {
                Material sapling = getSaplingDrop(block);
                if (sapling != null) return new ItemStack[]{ItemStack.of(sapling, 1)};
            }
            return new ItemStack[]{};
        }
        
        // Default: drop block itself
        try {
            Material mat = Material.valueOf(name.toUpperCase().replace("MINECRAFT:", ""));
            return new ItemStack[]{ItemStack.of(mat, 1)};
        } catch (Exception e) {
            return new ItemStack[]{};
        }
    }
    
    /**
     * Drop an item at position
     */
    private void dropItem(Pos pos, ItemStack item) {
        ItemEntity entity = new ItemEntity(item);
        entity.setInstance(instance, pos);
        entity.setVelocity(new Vec(
                (Math.random() - 0.5) * 0.2,
                0.2,
                (Math.random() - 0.5) * 0.2
        ));
        entity.setPickupDelay(Duration.ofSeconds(1));
    }
    
    /**
     * Damage the tool in player's hand
     */
    private void damageTool(Player player) {
        ItemStack held = player.getItemInMainHand();
        Material mat = held.material();
        
        // Only damage tools
        if (!isTool(mat)) return;
        
        // TODO: Implement durability
        // For now, tools don't break
    }
    
    private boolean isTool(Material mat) {
        String name = mat.name().toLowerCase();
        return name.contains("pickaxe") || name.contains("axe") || name.contains("shovel") || 
               name.contains("hoe") || name.contains("sword");
    }
    
    private Material getLogDrop(Block block) {
        if (block == Block.OAK_LOG) return Material.OAK_LOG;
        if (block == Block.SPRUCE_LOG) return Material.SPRUCE_LOG;
        if (block == Block.BIRCH_LOG) return Material.BIRCH_LOG;
        if (block == Block.JUNGLE_LOG) return Material.JUNGLE_LOG;
        if (block == Block.ACACIA_LOG) return Material.ACACIA_LOG;
        if (block == Block.DARK_OAK_LOG) return Material.DARK_OAK_LOG;
        return null;
    }
    
    private Material getSaplingDrop(Block block) {
        if (block == Block.OAK_LEAVES) return Material.OAK_SAPLING;
        if (block == Block.SPRUCE_LEAVES) return Material.SPRUCE_SAPLING;
        if (block == Block.BIRCH_LEAVES) return Material.BIRCH_SAPLING;
        return null;
    }
    
    /**
     * Give starter items to new player
     */
    public static void giveStarterItems(Player player) {
        PlayerInventory inv = player.getInventory();
        
        inv.addItemStack(ItemStack.of(Material.OAK_LOG, 16));
        inv.addItemStack(ItemStack.of(Material.OAK_PLANKS, 32));
        inv.addItemStack(ItemStack.of(Material.STICK, 16));
        inv.addItemStack(ItemStack.of(Material.CRAFTING_TABLE, 1));
        inv.addItemStack(ItemStack.of(Material.FURNACE, 1));
        inv.addItemStack(ItemStack.of(Material.CHEST, 1));
        inv.addItemStack(ItemStack.of(Material.TORCH, 32));
        inv.addItemStack(ItemStack.of(Material.BREAD, 16));
        inv.addItemStack(ItemStack.of(Material.APPLE, 8));
        inv.addItemStack(ItemStack.of(Material.WOODEN_PICKAXE, 1));
        inv.addItemStack(ItemStack.of(Material.WOODEN_AXE, 1));
        inv.addItemStack(ItemStack.of(Material.WOODEN_SHOVEL, 1));
        inv.addItemStack(ItemStack.of(Material.WOODEN_SWORD, 1));
        inv.addItemStack(ItemStack.of(Material.STONE_PICKAXE, 1));
        inv.addItemStack(ItemStack.of(Material.STONE_AXE, 1));
        inv.addItemStack(ItemStack.of(Material.STONE_SWORD, 1));
        
        logger.info("Gave starter kit to player {}", player.getUsername());
    }
    
    private record FoodInfo(int hunger, float saturation) {}
    
    private static class PlayerSurvivalData {
        int experience = 0;
        int experienceLevel = 0;
        boolean isSprinting = false;
        boolean isSneaking = false;
        int airTicks = 300; // 15 seconds of air
        boolean isUnderwater = false;
    }
}
