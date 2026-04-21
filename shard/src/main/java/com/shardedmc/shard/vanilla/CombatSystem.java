package com.shardedmc.shard.vanilla;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CombatSystem {
    private static final Logger logger = LoggerFactory.getLogger(CombatSystem.class);
    private static final Random random = new Random();
    private static final Map<Material, Float> WEAPON_DAMAGE = new HashMap<>();
    
    static {
        WEAPON_DAMAGE.put(Material.WOODEN_SWORD, 4.0f);
        WEAPON_DAMAGE.put(Material.STONE_SWORD, 5.0f);
        WEAPON_DAMAGE.put(Material.IRON_SWORD, 6.0f);
        WEAPON_DAMAGE.put(Material.DIAMOND_SWORD, 7.0f);
        WEAPON_DAMAGE.put(Material.NETHERITE_SWORD, 8.0f);
        WEAPON_DAMAGE.put(Material.GOLDEN_SWORD, 4.0f);
        WEAPON_DAMAGE.put(Material.WOODEN_AXE, 7.0f);
        WEAPON_DAMAGE.put(Material.STONE_AXE, 9.0f);
        WEAPON_DAMAGE.put(Material.IRON_AXE, 9.0f);
        WEAPON_DAMAGE.put(Material.DIAMOND_AXE, 9.0f);
        WEAPON_DAMAGE.put(Material.NETHERITE_AXE, 10.0f);
        WEAPON_DAMAGE.put(Material.GOLDEN_AXE, 7.0f);
        WEAPON_DAMAGE.put(Material.WOODEN_PICKAXE, 2.0f);
        WEAPON_DAMAGE.put(Material.STONE_PICKAXE, 3.0f);
        WEAPON_DAMAGE.put(Material.IRON_PICKAXE, 4.0f);
        WEAPON_DAMAGE.put(Material.DIAMOND_PICKAXE, 5.0f);
        WEAPON_DAMAGE.put(Material.NETHERITE_PICKAXE, 6.0f);
        WEAPON_DAMAGE.put(Material.GOLDEN_PICKAXE, 2.0f);
        WEAPON_DAMAGE.put(Material.WOODEN_SHOVEL, 2.5f);
        WEAPON_DAMAGE.put(Material.STONE_SHOVEL, 3.5f);
        WEAPON_DAMAGE.put(Material.IRON_SHOVEL, 4.5f);
        WEAPON_DAMAGE.put(Material.DIAMOND_SHOVEL, 5.5f);
        WEAPON_DAMAGE.put(Material.NETHERITE_SHOVEL, 6.5f);
        WEAPON_DAMAGE.put(Material.GOLDEN_SHOVEL, 2.5f);
        WEAPON_DAMAGE.put(Material.TRIDENT, 9.0f);
    }
    
    private final Map<Material, Integer> toolDurability = new HashMap<>();
    
    public CombatSystem() {
        initToolDurability();
    }
    
    private void initToolDurability() {
        toolDurability.put(Material.WOODEN_SWORD, 59);
        toolDurability.put(Material.WOODEN_PICKAXE, 59);
        toolDurability.put(Material.WOODEN_AXE, 59);
        toolDurability.put(Material.WOODEN_SHOVEL, 59);
        toolDurability.put(Material.WOODEN_HOE, 59);
        toolDurability.put(Material.STONE_SWORD, 131);
        toolDurability.put(Material.STONE_PICKAXE, 131);
        toolDurability.put(Material.STONE_AXE, 131);
        toolDurability.put(Material.STONE_SHOVEL, 131);
        toolDurability.put(Material.STONE_HOE, 131);
        toolDurability.put(Material.IRON_SWORD, 250);
        toolDurability.put(Material.IRON_PICKAXE, 250);
        toolDurability.put(Material.IRON_AXE, 250);
        toolDurability.put(Material.IRON_SHOVEL, 250);
        toolDurability.put(Material.IRON_HOE, 250);
        toolDurability.put(Material.DIAMOND_SWORD, 1561);
        toolDurability.put(Material.DIAMOND_PICKAXE, 1561);
        toolDurability.put(Material.DIAMOND_AXE, 1561);
        toolDurability.put(Material.DIAMOND_SHOVEL, 1561);
        toolDurability.put(Material.DIAMOND_HOE, 1561);
        toolDurability.put(Material.NETHERITE_SWORD, 2031);
        toolDurability.put(Material.NETHERITE_PICKAXE, 2031);
        toolDurability.put(Material.NETHERITE_AXE, 2031);
        toolDurability.put(Material.NETHERITE_SHOVEL, 2031);
        toolDurability.put(Material.NETHERITE_HOE, 2031);
        toolDurability.put(Material.GOLDEN_SWORD, 32);
        toolDurability.put(Material.GOLDEN_PICKAXE, 32);
        toolDurability.put(Material.GOLDEN_AXE, 32);
        toolDurability.put(Material.GOLDEN_SHOVEL, 32);
        toolDurability.put(Material.GOLDEN_HOE, 32);
    }
    
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(EntityAttackEvent.class, this::onEntityAttack);
        eventHandler.addListener(PlayerMoveEvent.class, this::onPlayerMove);
    }
    
    private void onEntityAttack(EntityAttackEvent event) {
        Entity attacker = event.getEntity();
        Entity target = event.getTarget();
        
        if (attacker instanceof Player playerAttacker) {
            float damage = calculateDamage(playerAttacker);
            
            if (target instanceof LivingEntity livingTarget) {
                if (target instanceof Player playerTarget) {
                    playerTarget.damage(DamageType.PLAYER_ATTACK, damage);
                } else {
                    livingTarget.damage(DamageType.PLAYER_ATTACK, damage);
                }
                
                applyKnockback(playerAttacker, livingTarget, damage);
                damageWeapon(playerAttacker);
            }
        }
    }
    
    private void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (player.isOnGround()) {
            double fallDistance = player.getPosition().y() - player.getTag(net.minestom.server.tag.Tag.Double("fall_start"));
            if (fallDistance > 3) {
                float damage = (float) ((fallDistance - 3) * 1);
                if (damage > 0) {
                    player.damage(DamageType.FALL, damage);
                }
            }
            player.setTag(net.minestom.server.tag.Tag.Double("fall_start"), player.getPosition().y());
        }
    }
    
    private void applyKnockback(Player attacker, LivingEntity target, float damage) {
        double dx = target.getPosition().x() - attacker.getPosition().x();
        double dz = target.getPosition().z() - attacker.getPosition().z();
        
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length == 0) {
            dx = random.nextDouble() - 0.5;
            dz = random.nextDouble() - 0.5;
            length = Math.sqrt(dx * dx + dz * dz);
        }
        
        dx /= length;
        dz /= length;
        
        double horizontalStrength = 0.4;
        double verticalStrength = 0.4;
        
        if (attacker.isSprinting()) {
            horizontalStrength *= 1.5;
        }
        
        Vec knockbackVel = new Vec(
            dx * horizontalStrength,
            verticalStrength,
            dz * horizontalStrength
        );
        
        target.setVelocity(knockbackVel);
        
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!target.isRemoved()) {
                Vec currentVel = target.getVelocity();
                if (Math.abs(currentVel.x()) < Math.abs(knockbackVel.x()) * 0.5 &&
                    Math.abs(currentVel.z()) < Math.abs(knockbackVel.z()) * 0.5) {
                    target.setVelocity(knockbackVel);
                }
            }
        }).delay(java.time.Duration.ofMillis(50)).schedule();
        
        logger.debug("Applied knockback: dx={}, dz={}, strength={}", dx, dz, horizontalStrength);
    }
    
    private float calculateDamage(Player player) {
        ItemStack held = player.getItemInMainHand();
        Material mat = held.material();
        
        float damage = WEAPON_DAMAGE.getOrDefault(mat, 1.0f);
        
        if (mat.name().contains("_hoe")) damage = 1.0f;
        
        if (!player.isOnGround() && player.getVelocity().y() < 0) {
            damage *= 1.5f;
        }
        
        return damage;
    }
    
    private void damageWeapon(Player player) {
        ItemStack held = player.getItemInMainHand();
        Material mat = held.material();
        
        if (isWeapon(mat)) {
            // Weapon durability not implemented in basic version
        }
    }
    
    private boolean isWeapon(Material mat) {
        String name = mat.name().toLowerCase();
        return name.contains("sword") || name.contains("axe") || name.contains("trident");
    }
}
