package com.shardedmc.shard.vanilla;

import com.shardedmc.shard.DimensionManager;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Nether and End portal mechanics including coordinate translation
 * and cross-dimension teleportation.
 */
public class PortalHandler {
    private static final Logger logger = LoggerFactory.getLogger(PortalHandler.class);
    
    private static final long PORTAL_COOLDOWN_MS = 15000; // 15 seconds
    private static final int NETHER_SEARCH_RADIUS = 128;
    private static final int OVERWORLD_SEARCH_RADIUS = 1024;
    
    private final DimensionManager dimensionManager;
    private final Map<UUID, Long> portalCooldowns = new ConcurrentHashMap<>();
    
    public PortalHandler(DimensionManager dimensionManager) {
        this.dimensionManager = dimensionManager;
    }
    
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerMoveEvent.class, this::onPlayerMove);
    }
    
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Pos newPos = event.getNewPosition();
        
        // Check cooldown
        if (isOnCooldown(player)) {
            return;
        }
        
        // Check if player entered a portal block
        Block block = player.getInstance().getBlock(newPos.blockX(), newPos.blockY(), newPos.blockZ());
        
        if (block == Block.NETHER_PORTAL) {
            handleNetherPortal(player, newPos);
        } else if (block == Block.END_PORTAL) {
            handleEndPortal(player, newPos);
        }
    }
    
    private boolean isOnCooldown(Player player) {
        Long lastUse = portalCooldowns.get(player.getUuid());
        if (lastUse == null) {
            return false;
        }
        return System.currentTimeMillis() - lastUse < PORTAL_COOLDOWN_MS;
    }
    
    private void setCooldown(Player player) {
        portalCooldowns.put(player.getUuid(), System.currentTimeMillis());
    }
    
    private void handleNetherPortal(Player player, Pos pos) {
        if (!(player.getInstance() instanceof InstanceContainer instanceContainer)) {
            logger.warn("Player {} is not in an InstanceContainer", player.getUsername());
            return;
        }
        DimensionManager.Dimension currentDim = dimensionManager.getDimensionForInstance(
            instanceContainer
        );
        
        if (currentDim == null) {
            logger.warn("Player {} is in an unknown dimension", player.getUsername());
            return;
        }
        
        // Calculate target position
        double targetX, targetZ;
        DimensionManager.Dimension targetDim;
        int searchRadius;
        
        if (currentDim == DimensionManager.Dimension.OVERWORLD) {
            targetX = pos.x() / 8.0;
            targetZ = pos.z() / 8.0;
            targetDim = DimensionManager.Dimension.NETHER;
            searchRadius = NETHER_SEARCH_RADIUS;
        } else if (currentDim == DimensionManager.Dimension.NETHER) {
            targetX = pos.x() * 8.0;
            targetZ = pos.z() * 8.0;
            targetDim = DimensionManager.Dimension.OVERWORLD;
            searchRadius = OVERWORLD_SEARCH_RADIUS;
        } else {
            // Can't use nether portal from End
            return;
        }
        
        InstanceContainer targetInstance = dimensionManager.getInstance(targetDim);
        if (targetInstance == null) {
            logger.error("Target dimension {} not initialized", targetDim.id);
            return;
        }
        
        // Search for existing portal within radius
        Pos targetPos = findOrCreatePortalLocation(targetInstance, targetX, targetZ, searchRadius);
        
        // Teleport player
        teleportPlayer(player, targetInstance, targetPos);
        
        setCooldown(player);
        
        logger.info("Player {} teleported from {} to {} via Nether portal at {}, {}",
            player.getUsername(), currentDim.id, targetDim.id, targetPos.x(), targetPos.z());
    }
    
    private void handleEndPortal(Player player, Pos pos) {
        DimensionManager.Dimension currentDim = dimensionManager.getDimensionForInstance(
            (InstanceContainer) player.getInstance()
        );
        
        if (currentDim == null) {
            logger.warn("Player {} is in an unknown dimension", player.getUsername());
            return;
        }
        
        DimensionManager.Dimension targetDim;
        Pos targetPos;
        
        if (currentDim == DimensionManager.Dimension.OVERWORLD || currentDim == DimensionManager.Dimension.NETHER) {
            // Entering the End - teleport to obsidian platform
            targetDim = DimensionManager.Dimension.END;
            targetPos = new Pos(100.5, 49, 0.5);
        } else {
            // In End, return to Overworld spawn
            targetDim = DimensionManager.Dimension.OVERWORLD;
            targetPos = player.getRespawnPoint();
            if (targetPos == null) {
                targetPos = new Pos(0.5, 70, 0.5);
            }
        }
        
        InstanceContainer targetInstance = dimensionManager.getInstance(targetDim);
        if (targetInstance == null) {
            logger.error("Target dimension {} not initialized", targetDim.id);
            return;
        }
        
        teleportPlayer(player, targetInstance, targetPos);
        
        setCooldown(player);
        
        if (currentDim != DimensionManager.Dimension.END) {
            player.sendMessage(Component.text("Entering The End..."));
        } else {
            player.sendMessage(Component.text("Returning to the Overworld..."));
        }
        
        logger.info("Player {} teleported from {} to {} via End portal",
            player.getUsername(), currentDim.id, targetDim.id);
    }
    
    private Pos findOrCreatePortalLocation(InstanceContainer instance, double targetX, double targetZ, int searchRadius) {
        // Search for existing portal frames within radius
        int centerX = (int) targetX;
        int centerZ = (int) targetZ;
        
        for (int radius = 0; radius <= searchRadius; radius += 16) {
            for (int x = -radius; x <= radius; x += 16) {
                for (int z = -radius; z <= radius; z += 16) {
                    int checkX = centerX + x;
                    int checkZ = centerZ + z;
                    
                    // Load the chunk if needed
                    int chunkX = checkX >> 4;
                    int chunkZ = checkZ >> 4;
                    instance.loadChunk(chunkX, chunkZ);
                    
                    // Check for portal blocks at various Y levels
                    for (int y = 32; y < 128; y++) {
                        Block block = instance.getBlock(checkX, y, checkZ);
                        if (block == Block.NETHER_PORTAL) {
                            return new Pos(checkX + 0.5, y, checkZ + 0.5);
                        }
                    }
                }
            }
        }
        
        // No existing portal found - create at calculated position
        // Find a safe Y level
        int safeY = findSafeY(instance, centerX, centerZ);
        return new Pos(centerX + 0.5, safeY, centerZ + 0.5);
    }
    
    private int findSafeY(InstanceContainer instance, int x, int z) {
        // Try to find a safe spot near Y=70
        for (int y = 70; y < 120; y++) {
            Block block = instance.getBlock(x, y, z);
            Block blockAbove = instance.getBlock(x, y + 1, z);
            if (block.isAir() && blockAbove.isAir()) {
                return y;
            }
        }
        return 70;
    }
    
    private void teleportPlayer(Player player, InstanceContainer targetInstance, Pos targetPos) {
        // Save player's current velocity and state
        player.setInstance(targetInstance, targetPos);
    }
    
    /**
     * Clear a player's portal cooldown (useful for admin commands or debugging).
     */
    public void clearCooldown(Player player) {
        portalCooldowns.remove(player.getUuid());
    }
}
