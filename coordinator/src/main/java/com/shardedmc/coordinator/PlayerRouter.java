package com.shardedmc.coordinator;

import com.shardedmc.shared.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes players to the correct shard based on their position.
 * Maintains player-to-shard mapping and handles transfers.
 */
public class PlayerRouter {
    private static final Logger logger = LoggerFactory.getLogger(PlayerRouter.class);
    
    // Maps player UUID to current shard ID
    private final Map<String, String> playerLocations = new ConcurrentHashMap<>();
    
    // Maps player UUID to their current chunk position
    private final Map<String, ChunkPos> playerPositions = new ConcurrentHashMap<>();
    
    // Maps shard ID to set of player UUIDs
    private final Map<String, Set<String>> shardPlayers = new ConcurrentHashMap<>();
    
    private final ChunkOwnershipManager ownershipManager;
    
    public PlayerRouter(ChunkOwnershipManager ownershipManager) {
        this.ownershipManager = ownershipManager;
    }
    
    /**
     * Register a player on a shard.
     */
    public void registerPlayer(String playerUuid, String shardId, int chunkX, int chunkZ) {
        // Remove from old shard if any
        String oldShard = playerLocations.get(playerUuid);
        if (oldShard != null && !oldShard.equals(shardId)) {
            shardPlayers.computeIfAbsent(oldShard, k -> ConcurrentHashMap.newKeySet()).remove(playerUuid);
        }
        
        playerLocations.put(playerUuid, shardId);
        playerPositions.put(playerUuid, new ChunkPos(chunkX, chunkZ));
        shardPlayers.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
        
        logger.debug("Player {} registered on shard {} at chunk {}, {}", 
                playerUuid, shardId, chunkX, chunkZ);
    }
    
    /**
     * Update player position.
     * Returns target shard ID if player needs to transfer, null otherwise.
     */
    public String updatePlayerPosition(String playerUuid, int chunkX, int chunkZ) {
        ChunkPos newPos = new ChunkPos(chunkX, chunkZ);
        ChunkPos oldPos = playerPositions.get(playerUuid);
        
        if (oldPos != null && oldPos.equals(newPos)) {
            return null; // No change
        }
        
        playerPositions.put(playerUuid, newPos);
        
        // Check if player crossed into another shard's territory
        String currentShard = playerLocations.get(playerUuid);
        String chunkOwner = ownershipManager.getOwner(chunkX, chunkZ);
        
        if (chunkOwner != null && !chunkOwner.equals(currentShard)) {
            // Player needs to transfer to owning shard
            logger.info("Player {} should transfer from shard {} to shard {} (crossed chunk boundary)", 
                    playerUuid, currentShard, chunkOwner);
            return chunkOwner;
        }
        
        return null;
    }
    
    /**
     * Get the shard that should handle a player based on their position.
     */
    public String getTargetShard(int chunkX, int chunkZ) {
        String owner = ownershipManager.getOwner(chunkX, chunkZ);
        if (owner != null) {
            return owner;
        }
        
        // If no owner, find nearest shard or return first available
        return shardPlayers.keySet().stream().findFirst().orElse(null);
    }
    
    /**
     * Get current shard for a player.
     */
    public String getPlayerShard(String playerUuid) {
        return playerLocations.get(playerUuid);
    }
    
    /**
     * Remove player (disconnect).
     */
    public void removePlayer(String playerUuid) {
        String shardId = playerLocations.remove(playerUuid);
        playerPositions.remove(playerUuid);
        
        if (shardId != null) {
            Set<String> players = shardPlayers.get(shardId);
            if (players != null) {
                players.remove(playerUuid);
            }
        }
        
        logger.debug("Player {} removed", playerUuid);
    }
    
    /**
     * Get all players on a shard.
     */
    public Set<String> getShardPlayers(String shardId) {
        return shardPlayers.getOrDefault(shardId, Set.of());
    }
    
    /**
     * Get player count on a shard.
     */
    public int getPlayerCount(String shardId) {
        return getShardPlayers(shardId).size();
    }
    
    /**
     * Get least loaded shard.
     */
    public String getLeastLoadedShard() {
        return shardPlayers.entrySet().stream()
                .min(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Remove all players from a shard (called when shard disconnects).
     */
    public void removeShard(String shardId) {
        Set<String> players = shardPlayers.remove(shardId);
        if (players != null) {
            for (String playerUuid : players) {
                playerLocations.remove(playerUuid);
                playerPositions.remove(playerUuid);
            }
            logger.info("Removed {} players from disconnected shard {}", players.size(), shardId);
        }
    }
}
