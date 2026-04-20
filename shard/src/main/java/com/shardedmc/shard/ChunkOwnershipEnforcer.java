package com.shardedmc.shard;

import com.shardedmc.shared.ChunkPos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces chunk ownership rules on the shard.
 * Prevents players from modifying chunks that don't belong to this shard.
 * Based on MultiPaper's chunk ownership model:
 * - Each shard only allows block modifications in chunks it owns
 * - Players are transferred to the correct shard when crossing boundaries
 */
public class ChunkOwnershipEnforcer {
    private static final Logger logger = LoggerFactory.getLogger(ChunkOwnershipEnforcer.class);
    
    private final ShardCoordinatorClient coordinatorClient;
    private final String shardId;
    
    // Local cache of chunk ownership to avoid repeated coordinator calls
    private final Map<ChunkPos, Boolean> ownershipCache = new ConcurrentHashMap<>();
    private final Set<ChunkPos> ownedChunks = ConcurrentHashMap.newKeySet();
    
    public ChunkOwnershipEnforcer(ShardCoordinatorClient coordinatorClient, String shardId) {
        this.coordinatorClient = coordinatorClient;
        this.shardId = shardId;
    }
    
    public void registerEvents(EventNode<net.minestom.server.event.Event> eventNode) {
        // Block break events
        eventNode.addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            ChunkPos chunk = new ChunkPos(
                    event.getBlockPosition().chunkX(),
                    event.getBlockPosition().chunkZ()
            );
            
            if (!isChunkOwned(chunk)) {
                event.setCancelled(true);
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "You can only break blocks in your current region!"));
                logger.debug("Blocked block break by {} at chunk {} (not owned)", 
                        player.getUsername(), chunk);
            }
        });
        
        // Block place events
        eventNode.addListener(PlayerBlockPlaceEvent.class, event -> {
            Player player = event.getPlayer();
            ChunkPos chunk = new ChunkPos(
                    event.getBlockPosition().chunkX(),
                    event.getBlockPosition().chunkZ()
            );
            
            if (!isChunkOwned(chunk)) {
                event.setCancelled(true);
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "You can only place blocks in your current region!"));
                logger.debug("Blocked block place by {} at chunk {} (not owned)", 
                        player.getUsername(), chunk);
            }
        });
        
        logger.info("Chunk ownership enforcement registered for shard {}", shardId);
    }
    
    /**
     * Check if this shard owns a chunk.
     * Uses local cache first, then falls back to coordinator query.
     */
    private boolean isChunkOwned(ChunkPos chunk) {
        // Check local cache first
        Boolean cached = ownershipCache.get(chunk);
        if (cached != null) {
            return cached;
        }
        
        // Query coordinator
        try {
            var response = coordinatorClient.requestChunkLoad(shardId, chunk.x(), chunk.z()).get();
            boolean owned = response.getSuccess() && response.getOwnerShardId().equals(shardId);
            
            // Cache the result
            ownershipCache.put(chunk, owned);
            if (owned) {
                ownedChunks.add(chunk);
            }
            
            return owned;
        } catch (Exception e) {
            logger.error("Error checking chunk ownership for {}", chunk, e);
            // Default to allowing the action if we can't verify ownership
            // In production, you might want to default to false (deny)
            return true;
        }
    }
    
    /**
     * Mark a chunk as owned by this shard (called when chunks are allocated)
     */
    public void markChunkOwned(int chunkX, int chunkZ) {
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        ownershipCache.put(chunk, true);
        ownedChunks.add(chunk);
    }
    
    /**
     * Mark multiple chunks as owned
     */
    public void markChunksOwned(Set<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            ownershipCache.put(chunk, true);
            ownedChunks.add(chunk);
        }
    }
    
    /**
     * Clear ownership cache (e.g., when rebalancing occurs)
     */
    public void clearCache() {
        ownershipCache.clear();
        ownedChunks.clear();
    }
    
    /**
     * Get the number of chunks currently marked as owned
     */
    public int getOwnedChunkCount() {
        return ownedChunks.size();
    }
}
