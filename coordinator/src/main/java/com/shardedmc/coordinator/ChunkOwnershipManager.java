package com.shardedmc.coordinator;

import com.shardedmc.shared.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages chunk ownership across shards.
 * Each chunk can be owned by only one shard at a time.
 * The owner is responsible for ticking and modifying the chunk.
 */
public class ChunkOwnershipManager {
    private static final Logger logger = LoggerFactory.getLogger(ChunkOwnershipManager.class);
    
    // Maps chunk position to owning shard ID
    private final Map<ChunkPos, String> chunkOwners = new ConcurrentHashMap<>();
    
    // Maps shard ID to set of owned chunks
    private final Map<String, Set<ChunkPos>> shardOwnedChunks = new ConcurrentHashMap<>();
    
    // Maps chunk position to set of subscribing shard IDs
    private final Map<ChunkPos, Set<String>> chunkSubscribers = new ConcurrentHashMap<>();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * Request ownership of a chunk.
     * Returns true if ownership was granted, false if another shard owns it.
     */
    public boolean requestOwnership(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        
        lock.writeLock().lock();
        try {
            String currentOwner = chunkOwners.get(pos);
            if (currentOwner == null) {
                // Chunk is unowned, grant ownership
                chunkOwners.put(pos, shardId);
                shardOwnedChunks.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet()).add(pos);
                logger.info("Granted ownership of chunk {},{} to shard {}", chunkX, chunkZ, shardId);
                return true;
            } else if (currentOwner.equals(shardId)) {
                // Already owns this chunk
                return true;
            } else {
                // Another shard owns this chunk
                logger.debug("Chunk {},{} already owned by shard {}", chunkX, chunkZ, currentOwner);
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Release ownership of a chunk.
     */
    public void releaseOwnership(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        
        lock.writeLock().lock();
        try {
            String currentOwner = chunkOwners.get(pos);
            if (currentOwner != null && currentOwner.equals(shardId)) {
                chunkOwners.remove(pos);
                Set<ChunkPos> shardsChunks = shardOwnedChunks.get(shardId);
                if (shardsChunks != null) {
                    shardsChunks.remove(pos);
                }
                logger.info("Released ownership of chunk {},{} from shard {}", chunkX, chunkZ, shardId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the owner of a chunk.
     */
    public String getOwner(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        lock.readLock().lock();
        try {
            return chunkOwners.get(pos);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Subscribe a shard to chunk updates.
     * Subscribers receive block changes but cannot modify the chunk.
     */
    public void subscribe(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        chunkSubscribers.computeIfAbsent(pos, k -> ConcurrentHashMap.newKeySet()).add(shardId);
        logger.debug("Shard {} subscribed to chunk {}, {}", shardId, chunkX, chunkZ);
    }
    
    /**
     * Unsubscribe a shard from chunk updates.
     */
    public void unsubscribe(String shardId, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Set<String> subscribers = chunkSubscribers.get(pos);
        if (subscribers != null) {
            subscribers.remove(shardId);
        }
    }
    
    /**
     * Get all subscribers for a chunk (excluding the owner).
     */
    public Set<String> getSubscribers(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        lock.readLock().lock();
        try {
            Set<String> subscribers = chunkSubscribers.get(pos);
            if (subscribers == null) {
                return Set.of();
            }
            // Return copy without owner
            Set<String> result = new HashSet<>(subscribers);
            String owner = chunkOwners.get(pos);
            if (owner != null) {
                result.remove(owner);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all chunks owned by a shard.
     */
    public Set<ChunkPos> getOwnedChunks(String shardId) {
        return shardOwnedChunks.getOrDefault(shardId, Set.of());
    }
    
    /**
     * Release all chunks owned by a shard (called when shard disconnects).
     */
    public void releaseAllOwnership(String shardId) {
        lock.writeLock().lock();
        try {
            Set<ChunkPos> chunks = shardOwnedChunks.remove(shardId);
            if (chunks != null) {
                for (ChunkPos pos : chunks) {
                    chunkOwners.remove(pos);
                    logger.info("Released ownership of chunk {},{} from disconnected shard {}", 
                            pos.x(), pos.z(), shardId);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if a shard owns a specific chunk.
     */
    public boolean isOwner(String shardId, int chunkX, int chunkZ) {
        return shardId.equals(getOwner(chunkX, chunkZ));
    }
}
