package com.shardedmc.shard;

import com.shardedmc.shard.vanilla.NPCManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat logging prevention system.
 * Tracks combat state and spawns NPCs when players disconnect during combat.
 * If the NPC dies, the player dies on reconnect.
 */
public class CombatLogger {
    private static final Logger logger = LoggerFactory.getLogger(CombatLogger.class);

    // Configuration
    private final long combatDurationMs;
    private final long npcSurvivalTimeMs;
    private final boolean dropItemsOnDeath;

    // Combat state tracking: player UUID -> combat end timestamp
    private final Map<UUID, Long> combatStates = new ConcurrentHashMap<>();

    // Active combat NPCs: player UUID -> CombatNPC
    private final Map<UUID, CombatNPC> activeNPCs = new ConcurrentHashMap<>();

    // Players who died while offline (NPC died): UUID -> death data
    private final Map<UUID, CombatDeathData> pendingDeaths = new ConcurrentHashMap<>();

    private final NPCManager npcManager;

    public CombatLogger(NPCManager npcManager) {
        this(npcManager, 30000, 30000, true);
    }

    public CombatLogger(NPCManager npcManager, long combatDurationMs, long npcSurvivalTimeMs, boolean dropItemsOnDeath) {
        this.npcManager = npcManager;
        this.combatDurationMs = combatDurationMs;
        this.npcSurvivalTimeMs = npcSurvivalTimeMs;
        this.dropItemsOnDeath = dropItemsOnDeath;
    }

    public void register(EventNode<Event> eventNode) {
        // Track combat on entity attack
        eventNode.addListener(EntityAttackEvent.class, this::onEntityAttack);

        // Track combat on entity damage (including projectiles, fall, etc.)
        eventNode.addListener(EntityDamageEvent.class, this::onEntityDamage);

        // Handle disconnect during combat
        eventNode.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Handle player spawn (reconnect)
        eventNode.addListener(PlayerSpawnEvent.class, this::onPlayerSpawn);

        // Handle NPC death
        eventNode.addListener(EntityDeathEvent.class, this::onEntityDeath);

        // Start cleanup task
        startCleanupTask();

        logger.info("CombatLogger registered (combatDuration={}s, npcSurvival={}s, dropItems={})",
                combatDurationMs / 1000, npcSurvivalTimeMs / 1000, dropItemsOnDeath);
    }

    private void onEntityAttack(EntityAttackEvent event) {
        Entity attacker = event.getEntity();
        Entity target = event.getTarget();

        // Mark both attacker and target as in combat
        if (attacker instanceof Player attackerPlayer) {
            enterCombat(attackerPlayer);
        }
        if (target instanceof Player targetPlayer) {
            enterCombat(targetPlayer);
        }
    }

    private void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        // Only count PvP/PvE damage (not fall, fire, drowning, etc.)
        // In Minestom, DamageTypes is not public, so we accept all damage events
        enterCombat(player);
    }

    private void enterCombat(Player player) {
        long combatEnd = System.currentTimeMillis() + combatDurationMs;
        combatStates.put(player.getUuid(), combatEnd);
        logger.debug("{} entered combat (expires at {})", player.getUsername(), combatEnd);
    }

    private boolean isInCombat(Player player) {
        Long combatEnd = combatStates.get(player.getUuid());
        if (combatEnd == null) return false;

        if (System.currentTimeMillis() > combatEnd) {
            combatStates.remove(player.getUuid());
            return false;
        }

        return true;
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Player player = event.getPlayer();

        if (!isInCombat(player)) {
            logger.debug("{} disconnected but was not in combat", player.getUsername());
            return;
        }

        // Remove combat state
        combatStates.remove(player.getUuid());

        // Spawn combat NPC
        spawnCombatNPC(player);
    }

    private void spawnCombatNPC(Player player) {
        try {
            Pos pos = player.getPosition();
            String name = player.getUsername();
            UUID playerUuid = player.getUuid();

            // Get player skin info (if available)
            String skinTexture = null;
            String skinSignature = null;
            try {
                // Try to get skin from player settings
                var skin = player.getSkin();
                if (skin != null) {
                    skinTexture = skin.textures();
                    skinSignature = skin.signature();
                }
            } catch (Exception e) {
                logger.debug("Could not get skin for {}", name);
            }

            // Spawn NPC
            NPCManager.NPC npc = npcManager.spawnNPC(
                    player.getInstance(), pos, name, skinTexture, skinSignature);

            // Set NPC health to match player health
            if (npc.entity instanceof net.minestom.server.entity.LivingEntity livingNPC) {
                livingNPC.setHealth(player.getHealth());
            }

            // Store player inventory for item drops
            List<ItemStack> inventory = new ArrayList<>();
            PlayerInventory playerInv = player.getInventory();
            for (int i = 0; i < playerInv.getSize(); i++) {
                ItemStack item = playerInv.getItemStack(i);
                if (item != null && !item.isAir()) {
                    inventory.add(item);
                }
            }

            // Store armor
            List<ItemStack> armor = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = player.getEquipment(slot);
                if (item != null && !item.isAir()) {
                    armor.add(item);
                }
            }

            // Create combat NPC record
            CombatNPC combatNPC = new CombatNPC(
                    playerUuid, name, npc, inventory, armor,
                    player.getHealth(), 20.0f,
                    System.currentTimeMillis() + npcSurvivalTimeMs
            );
            activeNPCs.put(playerUuid, combatNPC);

            logger.info("Spawned combat NPC for {} at {} (health={:.1f}, items={})",
                    name, pos, player.getHealth(), inventory.size());

            // Schedule NPC removal after survival time
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                removeCombatNPC(playerUuid, false);
            }).delay(TaskSchedule.duration(java.time.Duration.ofMillis(npcSurvivalTimeMs))).schedule();

        } catch (Exception e) {
            logger.error("Failed to spawn combat NPC for {}", player.getUsername(), e);
        }
    }

    private void onPlayerSpawn(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUuid();

        // Check if there's a pending death
        CombatDeathData deathData = pendingDeaths.remove(uuid);
        if (deathData != null) {
            // Player's NPC died - kill the player
            player.setHealth(0);
            player.sendMessage(Component.text("You died while combat logged!", NamedTextColor.RED));
            logger.info("{} reconnected after combat NPC died - player killed", player.getUsername());
            return;
        }

        // Check if there's an active NPC
        CombatNPC combatNPC = activeNPCs.remove(uuid);
        if (combatNPC != null) {
            // Player reconnected while NPC is still alive
            // Remove the NPC
            npcManager.removeNPC(combatNPC.npc.id);
            player.sendMessage(Component.text("You reconnected during combat. Your combat NPC has been removed.", NamedTextColor.YELLOW));
            logger.info("{} reconnected during combat - NPC removed safely", player.getUsername());
        }
    }

    private void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Check if this is a combat NPC
        for (Map.Entry<UUID, CombatNPC> entry : activeNPCs.entrySet()) {
            CombatNPC combatNPC = entry.getValue();
            if (combatNPC.npc.entity.getUuid().equals(entity.getUuid())) {
                UUID playerUuid = entry.getKey();

                // Drop items if configured
                if (dropItemsOnDeath) {
                    dropNPCItems(combatNPC, entity.getPosition());
                }

                // Mark player for death on reconnect
                pendingDeaths.put(playerUuid, new CombatDeathData(
                        playerUuid, combatNPC.playerName, System.currentTimeMillis()
                ));

                // Remove from active NPCs
                activeNPCs.remove(playerUuid);

                logger.info("Combat NPC for {} died - player will be killed on reconnect",
                        combatNPC.playerName);
                return;
            }
        }
    }

    private void dropNPCItems(CombatNPC combatNPC, Pos pos) {
        var instance = combatNPC.npc.entity.getInstance();
        if (instance == null) return;

        // Drop inventory items
        for (ItemStack item : combatNPC.inventory) {
            ItemEntity itemEntity = new ItemEntity(item);
            itemEntity.setInstance(instance, pos.add(
                    Math.random() * 2 - 1, 0.5, Math.random() * 2 - 1));
            itemEntity.setPickupDelay(1000, ChronoUnit.MILLIS);
        }

        // Drop armor items
        for (ItemStack item : combatNPC.armor) {
            ItemEntity itemEntity = new ItemEntity(item);
            itemEntity.setInstance(instance, pos.add(
                    Math.random() * 2 - 1, 0.5, Math.random() * 2 - 1));
            itemEntity.setPickupDelay(1000, ChronoUnit.MILLIS);
        }

        logger.info("Dropped {} inventory items and {} armor items from combat NPC for {}",
                combatNPC.inventory.size(), combatNPC.armor.size(), combatNPC.playerName);
    }

    private void removeCombatNPC(UUID playerUuid, boolean survived) {
        CombatNPC combatNPC = activeNPCs.remove(playerUuid);
        if (combatNPC == null) return;

        npcManager.removeNPC(combatNPC.npc.id);

        if (survived) {
            logger.info("Combat NPC for {} survived the combat period", combatNPC.playerName);
        }
    }

    private void startCleanupTask() {
        // Clean up expired combat states every 10 seconds
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long now = System.currentTimeMillis();

            // Clean expired combat states
            combatStates.entrySet().removeIf(entry -> entry.getValue() <= now);

            // Clean expired pending deaths (after 1 hour)
            pendingDeaths.entrySet().removeIf(entry ->
                    (now - entry.getValue().deathTime) > 3600000L);

        }).repeat(TaskSchedule.duration(Duration.ofSeconds(10))).schedule();
    }

    /**
     * Check if a player is currently in combat.
     */
    public boolean isInCombat(UUID playerUuid) {
        Long combatEnd = combatStates.get(playerUuid);
        if (combatEnd == null) return false;

        if (System.currentTimeMillis() > combatEnd) {
            combatStates.remove(playerUuid);
            return false;
        }

        return true;
    }

    /**
     * Get remaining combat time in milliseconds.
     */
    public long getRemainingCombatTime(UUID playerUuid) {
        Long combatEnd = combatStates.get(playerUuid);
        if (combatEnd == null) return 0;

        long remaining = combatEnd - System.currentTimeMillis();
        if (remaining <= 0) {
            combatStates.remove(playerUuid);
            return 0;
        }

        return remaining;
    }

    /**
     * Force a player out of combat.
     */
    public void clearCombat(UUID playerUuid) {
        combatStates.remove(playerUuid);
    }

    /**
     * Get active combat NPC count.
     */
    public int getActiveNPCCount() {
        return activeNPCs.size();
    }

    /**
     * Data class for tracking combat NPCs.
     */
    private static class CombatNPC {
        final UUID playerUuid;
        final String playerName;
        final NPCManager.NPC npc;
        final List<ItemStack> inventory;
        final List<ItemStack> armor;
        final double health;
        final double maxHealth;
        final long expireTime;

        CombatNPC(UUID playerUuid, String playerName, NPCManager.NPC npc,
                  List<ItemStack> inventory, List<ItemStack> armor,
                  double health, double maxHealth, long expireTime) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.npc = npc;
            this.inventory = inventory;
            this.armor = armor;
            this.health = health;
            this.maxHealth = maxHealth;
            this.expireTime = expireTime;
        }
    }

    /**
     * Data class for tracking pending deaths.
     */
    private static class CombatDeathData {
        final UUID playerUuid;
        final String playerName;
        final long deathTime;

        CombatDeathData(UUID playerUuid, String playerName, long deathTime) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.deathTime = deathTime;
        }
    }
}
