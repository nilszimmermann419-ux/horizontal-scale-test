package com.shardedmc.coordinator;

import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shared.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Chunk Ownership and Subscription System - Core of the sharded world.
 * Based on MultiPaper's architecture:
 * - Each chunk is owned by exactly one shard
 * - Shards subscribe to chunks they need but don't own
 * - Chunk locks prevent concurrent modifications
 */
public class ChunkManager {
    private static final Logger logger = LoggerFactory.getLogger(ChunkManager.class);
    
    private final RedisClient redis;
    private final ShardRegistry shardRegistry;
    
    // Chunk ownership: chunk pos -> owning shard ID
    private final Map<ChunkPos, String> chunkOwners = new ConcurrentHashMap<>();
    
    // Chunk subscribers: chunk pos -> set of subscribed shard IDs
    private final Map<ChunkPos, Set<String>> chunkSubscribers = new ConcurrentHashMap<>();
    
    // Reverse mapping: shard ID -> set of owned chunks
    private final Map<String, Set<ChunkPos>> shardOwnedChunks = new ConcurrentHashMap<>();
    
    // Reverse mapping: shard ID -> set of subscribed chunks
    private final Map<String, Set<ChunkPos>> shardSubscribedChunks = new ConcurrentHashMap<>();
    
    // Chunk locks: chunk pos -> lock info
    private final Map<ChunkPos, ChunkLock> chunkLocks = new ConcurrentHashMap<>();
    
    // Load balancing: track chunk load per shard
    private final Map<String, Integer> shardChunkLoad = new ConcurrentHashMap<>();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public ChunkManager(RedisClient redis, ShardRegistry shardRegistry) {
        this.redis = redis;
        this.shardRegistry = shardRegistry;
    }
    
    /**
     * Request ownership of a chunk. Returns true if ownership granted.
     */
    public CompletableFuture<Boolean> requestChunkOwnership(String shardId, ChunkPos chunk) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                // Check if already owned
                String currentOwner = chunkOwners.get(chunk);
                if (currentOwner != null) {
                    if (currentOwner.equals(shardId)) {
                        return true; // Already owner
                    }
                    
                    // Check if owner is still alive
                    if (!shardRegistry.isShardHealthy(currentOwner)) {
                        // Steal ownership from dead shard
                        logger.warn("Stealing chunk {} from dead shard {} to {}", 
                                chunk, currentOwner, shardId);
                        transferOwnership(chunk, currentOwner, shardId);
                        return true;
                    }
                    
                    // Already owned by another living shard
                    logger.debug("Chunk {} already owned by {}", chunk, currentOwner);
                    return false;
                }
                
                // Grant ownership
                chunkOwners.put(chunk, shardId);
                shardOwnedChunks.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet()).add(chunk);
                shardChunkLoad.merge(shardId, 1, Integer::sum);
                
                logger.info("Granted ownership of chunk {} to shard {}", chunk, shardId);
                return true;
                
            } finally {
                lock.writeLock().unlock();
            }
        }).thenApply(success -> {
            if (success) {
                // Persist to Redis outside lock
                redis.hsetAsync("chunk:" + chunk.x() + ":" + chunk.z(),
                        Map.of("owner", shardId, "timestamp", String.valueOf(System.currentTimeMillis())));
            }
            return success;
        });
    }
    
    /**
     * Release ownership of a chunk
     */
    public CompletableFuture<Void> releaseChunkOwnership(String shardId, ChunkPos chunk) {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                String owner = chunkOwners.get(chunk);
                if (shardId.equals(owner)) {
                    chunkOwners.remove(chunk);
                    Set<ChunkPos> owned = shardOwnedChunks.get(shardId);
                    if (owned != null) {
                        owned.remove(chunk);
                    }
                    shardChunkLoad.merge(shardId, -1, Integer::sum);
                    // Remove zero entries to prevent accumulation
                    shardChunkLoad.computeIfPresent(shardId, (k, v) -> v <= 0 ? null : v);
                    
                    logger.info("Released ownership of chunk {} from shard {}", chunk, shardId);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }).thenRun(() -> {
            redis.delAsync("chunk:" + chunk.x() + ":" + chunk.z());
        });
    }
    
    /**
     * Subscribe to a chunk for updates (without owning it)
     */
    public CompletableFuture<Boolean> subscribeToChunk(String shardId, ChunkPos chunk) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                // Check if shard exists and is healthy
                if (!shardRegistry.isShardHealthy(shardId)) {
                    return false;
                }
                
                // Add subscriber
                chunkSubscribers.computeIfAbsent(chunk, k -> ConcurrentHashMap.newKeySet()).add(shardId);
                shardSubscribedChunks.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet()).add(chunk);
                
                // Notify owner about new subscriber
                String owner = chunkOwners.get(chunk);
                if (owner != null && !owner.equals(shardId)) {
                    notifyOwnerOfSubscriber(owner, shardId, chunk);
                }
                
                logger.debug("Shard {} subscribed to chunk {}", shardId, chunk);
                return true;
                
            } finally {
                lock.writeLock().unlock();
            }
        });
    }
    
    /**
     * Unsubscribe from a chunk
     */
    public CompletableFuture<Void> unsubscribeFromChunk(String shardId, ChunkPos chunk) {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                Set<String> subscribers = chunkSubscribers.get(chunk);
                if (subscribers != null) {
                    subscribers.remove(shardId);
                    if (subscribers.isEmpty()) {
                        chunkSubscribers.remove(chunk);
                    }
                }
                
                Set<ChunkPos> subscribed = shardSubscribedChunks.get(shardId);
                if (subscribed != null) {
                    subscribed.remove(chunk);
                }
                
                logger.debug("Shard {} unsubscribed from chunk {}", shardId, chunk);
            } finally {
                lock.writeLock().unlock();
            }
        });
    }
    
    /**
     * Get chunk owner
     */
    public String getChunkOwner(ChunkPos chunk) {
        lock.readLock().lock();
        try {
            return chunkOwners.get(chunk);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get chunk subscribers
     */
    public Set<String> getChunkSubscribers(ChunkPos chunk) {
        lock.readLock().lock();
        try {
            return new HashSet<>(chunkSubscribers.getOrDefault(chunk, Set.of()));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all chunks owned by a shard
     */
    public Set<ChunkPos> getOwnedChunks(String shardId) {
        lock.readLock().lock();
        try {
            return new HashSet<>(shardOwnedChunks.getOrDefault(shardId, Set.of()));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Lock a chunk for writing (prevents concurrent modifications)
     */
    public boolean lockChunk(String shardId, ChunkPos chunk) {
        lock.writeLock().lock();
        try {
            ChunkLock chunkLock = chunkLocks.get(chunk);
            if (chunkLock == null) {
                chunkLocks.put(chunk, new ChunkLock(shardId, System.currentTimeMillis()));
                return true;
            }
            // Already locked
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Unlock a chunk
     */
    public void unlockChunk(ChunkPos chunk) {
        lock.writeLock().lock();
        try {
            chunkLocks.remove(chunk);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get best shard for a new chunk (load balancing)
     */
    public String getBestShardForChunk(ChunkPos chunk) {
        lock.readLock().lock();
        try {
            List<ShardRegistry.ShardInfo> healthyShards = shardRegistry.getHealthyShards();
            if (healthyShards.isEmpty()) {
                return null;
            }
            
            // Find shard with lowest chunk load
            return healthyShards.stream()
                    .min(Comparator.comparingInt(s -> shardChunkLoad.getOrDefault(s.shardId(), 0)))
                    .map(ShardRegistry.ShardInfo::shardId)
                    .orElse(null);
                    
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Rebalance chunks when a shard joins or leaves
     */
    public void rebalanceChunks() {
        lock.writeLock().lock();
        try {
            List<ShardRegistry.ShardInfo> healthyShards = shardRegistry.getHealthyShards();
            if (healthyShards.size() < 2) {
                return; // Need at least 2 shards to rebalance
            }
            
            // Calculate target chunks per shard
            int totalChunks = chunkOwners.size();
            int targetPerShard = totalChunks / healthyShards.size();
            
            // Find overloaded and underloaded shards
            for (ShardRegistry.ShardInfo shard : healthyShards) {
                int load = shardChunkLoad.getOrDefault(shard.shardId(), 0);
                if (load > targetPerShard * 1.2) {
                    // Overloaded - migrate some chunks
                    int toMigrate = load - targetPerShard;
                    migrateChunksFromShard(shard.shardId(), toMigrate);
                }
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Handle shard failure - redistribute its chunks
     */
    public void handleShardFailure(String shardId) {
        lock.writeLock().lock();
        try {
            logger.warn("Handling shard failure: {}", shardId);
            
            // Get all chunks owned by failed shard
            Set<ChunkPos> owned = shardOwnedChunks.remove(shardId);
            if (owned != null) {
                for (ChunkPos chunk : owned) {
                    chunkOwners.remove(chunk);
                    
                    // Find new owner
                    String newOwner = getBestShardForChunk(chunk);
                    if (newOwner != null) {
                        chunkOwners.put(chunk, newOwner);
                        shardOwnedChunks.computeIfAbsent(newOwner, k -> ConcurrentHashMap.newKeySet()).add(chunk);
                        shardChunkLoad.merge(newOwner, 1, Integer::sum);
                        
                        logger.info("Migrated chunk {} from failed shard {} to {}", 
                                chunk, shardId, newOwner);
                    }
                }
            }
            
            // Clean up subscriptions
            shardSubscribedChunks.remove(shardId);
            chunkSubscribers.values().forEach(s -> s.remove(shardId));
            
            shardChunkLoad.remove(shardId);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void transferOwnership(ChunkPos chunk, String oldOwner, String newOwner) {
        chunkOwners.put(chunk, newOwner);
        Set<ChunkPos> oldOwned = shardOwnedChunks.get(oldOwner);
        if (oldOwned != null) {
            oldOwned.remove(chunk);
        }
        shardOwnedChunks.computeIfAbsent(newOwner, k -> ConcurrentHashMap.newKeySet()).add(chunk);
        shardChunkLoad.merge(oldOwner, -1, Integer::sum);
        shardChunkLoad.computeIfPresent(oldOwner, (k, v) -> v <= 0 ? null : v);
        shardChunkLoad.merge(newOwner, 1, Integer::sum);
    }
    
    private void migrateChunksFromShard(String shardId, int count) {
        Set<ChunkPos> owned = shardOwnedChunks.get(shardId);
        if (owned == null || owned.isEmpty()) return;
        
        Iterator<ChunkPos> it = owned.iterator();
        int migrated = 0;
        while (it.hasNext() && migrated < count) {
            ChunkPos chunk = it.next();
            String newOwner = getBestShardForChunk(chunk);
            if (newOwner != null && !newOwner.equals(shardId)) {
                transferOwnership(chunk, shardId, newOwner);
                migrated++;
            }
        }
        
        logger.info("Migrated {} chunks from shard {}", migrated, shardId);
    }
    
    private void notifyOwnerOfSubscriber(String owner, String subscriber, ChunkPos chunk) {
        // This would send a gRPC message to the owner shard
        // For now, just log it
        logger.debug("Notifying owner {} that {} subscribed to {}", owner, subscriber, chunk);
    }
    
    public Map<String, Object> getStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalChunks", chunkOwners.size());
            stats.put("totalSubscribers", chunkSubscribers.values().stream().mapToInt(Set::size).sum());
            stats.put("shardLoads", new HashMap<>(shardChunkLoad));
            stats.put("lockedChunks", chunkLocks.size());
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Allocate initial regions for a newly registered shard.
     * This replaces ChunkAllocationManager.allocateRegionsForShard()
     */
    public List<ChunkPos> allocateRegionsForShard(String shardId, int regionCount) {
        lock.writeLock().lock();
        List<ChunkPos> assigned;
        try {
            assigned = new ArrayList<>();
            
            // Find available chunks using a spiral pattern from origin
            int x = 0, z = 0;
            int dx = 0, dz = -1;
            int found = 0;
            
            while (found < regionCount) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!chunkOwners.containsKey(chunk)) {
                    chunkOwners.put(chunk, shardId);
                    shardOwnedChunks.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet()).add(chunk);
                    shardChunkLoad.merge(shardId, 1, Integer::sum);
                    assigned.add(chunk);
                    found++;
                }
                
                // Spiral pattern
                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int temp = dx;
                    dx = -dz;
                    dz = temp;
                }
                x += dx;
                z += dz;
            }
            
            logger.info("Allocated {} chunks to shard {}", assigned.size(), shardId);
        } finally {
            lock.writeLock().unlock();
        }
        
        // Persist to Redis outside lock
        for (ChunkPos chunk : assigned) {
            redis.hsetAsync("chunk:" + chunk.x() + ":" + chunk.z(),
                    Map.of("owner", shardId, "timestamp", String.valueOf(System.currentTimeMillis())));
        }
        
        return assigned;
    }
    
    /**
     * Get the shard that owns a specific chunk.
     * This replaces ChunkAllocationManager.getShardForChunk()
     */
    public CompletableFuture<Optional<String>> getShardForChunk(int x, int z) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ChunkPos chunk = new ChunkPos(x, z);
                String owner = chunkOwners.get(chunk);
                
                if (owner != null) {
                    // Verify owner is still healthy
                    if (shardRegistry.isShardHealthy(owner)) {
                        return Optional.of(owner);
                    } else {
                        // Owner is dead, remove it
                        logger.warn("Chunk {} owner {} is unhealthy, clearing ownership", chunk, owner);
                        chunkOwners.remove(chunk);
                        Set<ChunkPos> owned = shardOwnedChunks.get(owner);
                        if (owned != null) {
                            owned.remove(chunk);
                        }
                        return Optional.empty();
                    }
                }
                
                return Optional.empty();
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    private record ChunkLock(String shardId, long timestamp) {}
}
