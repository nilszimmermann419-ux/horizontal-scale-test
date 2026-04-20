package com.shardedmc.coordinator;

import com.shardedmc.shared.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages chunk locks to prevent concurrent modifications.
 * Before a shard can write to a chunk, it must acquire a lock from the master.
 * This ensures data consistency across all shards.
 */
public class ChunkLockManager {
    private static final Logger logger = LoggerFactory.getLogger(ChunkLockManager.class);
    
    // Maps chunk position to current lock holder (shard ID)
    private final Map<ChunkPos, String> chunkLocks = new ConcurrentHashMap<>();
    
    // Maps chunk position to queue of waiting shard IDs
    private final Map<ChunkPos, java.util.Queue<String>> lockQueues = new ConcurrentHashMap<>();
    
    // Lock timeout in milliseconds
    private static final long LOCK_TIMEOUT_MS = 5000;
    
    /**
     * Attempt to acquire a lock on a chunk.
     * Returns CompletableFuture that completes with true if lock acquired, false otherwise.
     */
    public CompletableFuture<Boolean> acquireLock(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        
        String currentHolder = chunkLocks.get(pos);
        if (currentHolder == null) {
            // Lock is available
            chunkLocks.put(pos, shardId);
            logger.debug("Lock acquired for chunk {},{} by shard {}", chunkX, chunkZ, shardId);
            return CompletableFuture.completedFuture(true);
        } else if (currentHolder.equals(shardId)) {
            // Already holds the lock
            return CompletableFuture.completedFuture(true);
        } else {
            // Lock is held by another shard, add to queue
            lockQueues.computeIfAbsent(pos, k -> new java.util.LinkedList<>()).add(shardId);
            logger.debug("Shard {} queued for lock on chunk {},{} (held by {})", 
                    shardId, chunkX, chunkZ, currentHolder);
            
            // Return future that will be completed when lock is available
            return CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < LOCK_TIMEOUT_MS) {
                    String holder = chunkLocks.get(pos);
                    if (holder != null && holder.equals(shardId)) {
                        return true;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                // Timeout - remove from queue
                java.util.Queue<String> queue = lockQueues.get(pos);
                if (queue != null) {
                    queue.remove(shardId);
                }
                logger.warn("Lock timeout for shard {} on chunk {}, {}", shardId, chunkX, chunkZ);
                return false;
            });
        }
    }
    
    /**
     * Release a lock on a chunk.
     * Grants lock to next shard in queue if any.
     */
    public void releaseLock(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        
        String currentHolder = chunkLocks.get(pos);
        if (currentHolder != null && currentHolder.equals(shardId)) {
            java.util.Queue<String> queue = lockQueues.get(pos);
            if (queue != null && !queue.isEmpty()) {
                // Grant to next in queue
                String nextShard = queue.poll();
                chunkLocks.put(pos, nextShard);
                logger.debug("Lock transferred for chunk {},{} to shard {}", chunkX, chunkZ, nextShard);
            } else {
                // No one waiting, remove lock
                chunkLocks.remove(pos);
                logger.debug("Lock released for chunk {},{} by shard {}", chunkX, chunkZ, shardId);
            }
        }
    }
    
    /**
     * Check if a shard holds the lock for a chunk.
     */
    public boolean hasLock(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        String holder = chunkLocks.get(pos);
        return holder != null && holder.equals(shardId);
    }
    
    /**
     * Force release all locks held by a shard (called on disconnect).
     */
    public void releaseAllLocks(String shardId) {
        for (Map.Entry<ChunkPos, String> entry : chunkLocks.entrySet()) {
            if (entry.getValue().equals(shardId)) {
                ChunkPos pos = entry.getKey();
                releaseLock(shardId, pos.x(), pos.z());
            }
        }
    }
    
    /**
     * Get current lock holder for a chunk.
     */
    public String getLockHolder(int chunkX, int chunkZ) {
        return chunkLocks.get(new ChunkPos(chunkX, chunkZ));
    }
}
