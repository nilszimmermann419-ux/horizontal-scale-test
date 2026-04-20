package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Shared chunk loader that stores/retrieves chunks from Redis.
 * Ensures all shards see the exact same world.
 * 
 * Flow:
 * 1. Check Redis for existing chunk
 * 2. If found, load and return it
 * 3. If not found, generate using world generator, save to Redis, return it
 */
public class SharedChunkLoader implements IChunkLoader {
    private static final Logger logger = LoggerFactory.getLogger(SharedChunkLoader.class);
    
    private final RedisClient redis;
    private final String worldKey;
    private final AdvancedWorldGenerator generator;
    
    public SharedChunkLoader(RedisClient redis, String worldName, long seed) {
        this.redis = redis;
        this.worldKey = "world:" + worldName + ":chunk:";
        this.generator = new AdvancedWorldGenerator(seed);
    }
    
    @Override
    public Chunk loadChunk(Instance instance, int chunkX, int chunkZ) {
        String key = worldKey + chunkX + ":" + chunkZ;
        
        try {
            // Try to load from Redis
            String data = redis.get(key);
            if (data != null && !data.isEmpty()) {
                Chunk chunk = deserializeChunk(instance, chunkX, chunkZ, data);
                logger.debug("Loaded chunk {},{} from Redis", chunkX, chunkZ);
                return chunk;
            }
        } catch (Exception e) {
            logger.error("Error loading chunk {},{} from Redis: {}", chunkX, chunkZ, e.getMessage());
        }
        
        // Not in Redis - generate it
        logger.debug("Generating chunk {},{} (not in Redis)", chunkX, chunkZ);
        Chunk chunk = generateChunk(instance, chunkX, chunkZ);
        
        // Save to Redis for other shards
        saveChunk(chunk);
        
        return chunk;
    }
    
    @Override
    public void saveChunk(Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        String key = worldKey + chunkX + ":" + chunkZ;
        
        try {
            String data = serializeChunk(chunk);
            redis.set(key, data);
            logger.debug("Saved chunk {},{} to Redis", chunkX, chunkZ);
        } catch (Exception e) {
            logger.error("Error saving chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
        }
    }
    
    /**
     * Generate a chunk using the world generator.
     */
    private Chunk generateChunk(Instance instance, int chunkX, int chunkZ) {
        Chunk chunk = new DynamicChunk(instance, chunkX, chunkZ);
        
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int height = generator.getTerrainHeightAt(worldX, worldZ);
                
                // Fill column
                for (int y = -64; y < 320; y++) {
                    if (y <= height) {
                        Block block = getBlockAtDepth(worldX, y, worldZ, height);
                        chunk.setBlock(x, y, z, block);
                    }
                }
                
                // Add surface features (grass, trees, etc.)
                if (height + 1 < 320) {
                    addSurfaceFeatures(chunk, x, height + 1, z, worldX, worldZ, height);
                }
            }
        }
        
        return chunk;
    }
    
    private Block getBlockAtDepth(int worldX, int y, int worldZ, int terrainHeight) {
        int depth = terrainHeight - y;
        
        if (y < 3) {
            return y == -64 ? Block.BEDROCK : Block.STONE;
        }
        
        if (depth > 3) {
            return Block.STONE;
        }
        
        if (depth > 0) {
            return Block.DIRT;
        }
        
        return Block.GRASS_BLOCK;
    }
    
    private void addSurfaceFeatures(Chunk chunk, int x, int y, int z, int worldX, int worldZ, int height) {
        // Simple grass on top
        if (y < 320) {
            chunk.setBlock(x, y, z, Block.SHORT_GRASS);
        }
    }
    
    /**
     * Serialize chunk to string format for Redis storage.
     * Format: x,y,z,blockName;x,y,z,blockName;...
     */
    private String serializeChunk(Chunk chunk) {
        StringBuilder sb = new StringBuilder();
        
        for (int x = 0; x < 16; x++) {
            for (int y = -64; y < 320; y++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!block.isAir()) {
                        if (sb.length() > 0) sb.append(';');
                        sb.append(x).append(',').append(y).append(',').append(z).append(',').append(block.name());
                    }
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Deserialize chunk from string format.
     */
    private Chunk deserializeChunk(Instance instance, int chunkX, int chunkZ, String data) {
        Chunk chunk = new DynamicChunk(instance, chunkX, chunkZ);
        
        if (data == null || data.isEmpty()) {
            return chunk;
        }
        
        String[] blocks = data.split(";");
        for (String blockData : blocks) {
            if (blockData.trim().isEmpty()) continue;
            
            String[] parts = blockData.split(",");
            if (parts.length == 4) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    String blockName = parts[3];
                    
                    Block block = Block.fromKey(blockName);
                    if (block != null) {
                        chunk.setBlock(x, y, z, block);
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse block data: {}", blockData);
                }
            }
        }
        
        return chunk;
    }
    
    @Override
    public boolean supportsParallelLoading() {
        return true;
    }
}
