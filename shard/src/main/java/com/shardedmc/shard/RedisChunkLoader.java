package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chunk loader that persists chunks to Redis for seamless multi-shard worlds.
 * All shards share the same chunk storage, so players see a consistent world
 * regardless of which shard they're connected to.
 */
public class RedisChunkLoader implements IChunkLoader {
    private static final Logger logger = LoggerFactory.getLogger(RedisChunkLoader.class);
    
    private final RedisClient redis;
    private final String worldKey;
    private final AdvancedWorldGenerator fallbackGenerator;
    
    public RedisChunkLoader(RedisClient redis, String worldName, long seed) {
        this.redis = redis;
        this.worldKey = "world:" + worldName + ":chunk:";
        this.fallbackGenerator = new AdvancedWorldGenerator(seed);
    }
    
    @Override
    public Chunk loadChunk(Instance instance, int chunkX, int chunkZ) {
        String key = worldKey + chunkX + ":" + chunkZ;
        
        try {
            String data = redis.get(key);
            if (data != null && !data.isEmpty()) {
                // Chunk exists in Redis - load it
                Chunk chunk = deserializeChunk(instance, chunkX, chunkZ, data);
                logger.info("Loaded chunk {},{} from Redis", chunkX, chunkZ);
                return chunk;
            }
        } catch (Exception e) {
            logger.error("Failed to load chunk {},{} from Redis, regenerating", chunkX, chunkZ, e);
        }
        
        // Chunk doesn't exist - generate it
        logger.info("Generating new chunk {}, {}", chunkX, chunkZ);
        Chunk chunk = new DynamicChunk(instance, chunkX, chunkZ);
        
        // Generate chunk data
        generateChunkData(chunk, chunkX, chunkZ);
        
        // Save to Redis for other shards (async)
        saveChunk(chunk);
        
        return chunk;
    }
    
    @Override
    public void saveChunk(Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        String key = worldKey + chunkX + ":" + chunkZ;
        
        try {
            // Simple serialization: save as comma-separated blocks
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 16; x++) {
                for (int y = -64; y < 320; y++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (!block.isAir()) {
                            sb.append(x).append(',').append(y).append(',').append(z).append(',').append(block.name()).append(';');
                        }
                    }
                }
            }
            
            redis.set(key, sb.toString());
            logger.debug("Saved chunk {},{} to Redis", chunkX, chunkZ);
        } catch (Exception e) {
            logger.error("Failed to save chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
        }
    }
    
    /**
     * Generate chunk data using the world generator.
     * This ensures all shards generate identical chunks for the same coordinates.
     */
    private void generateChunkData(Chunk chunk, int chunkX, int chunkZ) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int height = fallbackGenerator.getTerrainHeightAt(worldX, worldZ);
                
                // Fill column
                for (int y = -64; y < 320; y++) {
                    if (y <= height) {
                        Block block = getBlockAtDepth(worldX, y, worldZ, height);
                        chunk.setBlock(x, y, z, block);
                    }
                }
            }
        }
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
    
    private Chunk deserializeChunk(Instance instance, int chunkX, int chunkZ, String data) {
        Chunk chunk = new DynamicChunk(instance, chunkX, chunkZ);
        
        try {
            // Parse format: x,y,z,blockName;x,y,z,blockName;...
            String[] blocks = data.split(";");
            for (String blockData : blocks) {
                if (blockData.trim().isEmpty()) continue;
                String[] parts = blockData.split(",");
                if (parts.length == 4) {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    String blockName = parts[3];
                    
                    Block block = Block.fromKey(blockName);
                    if (block != null) {
                        chunk.setBlock(x, y, z, block);
                    }
                }
            }
            return chunk;
        } catch (Exception e) {
            logger.error("Chunk deserialization failed, using generated chunk", e);
            generateChunkData(chunk, chunkX, chunkZ);
            return chunk;
        }
    }
    
    @Override
    public boolean supportsParallelLoading() {
        return true;
    }
    
    @Override
    public boolean supportsParallelSaving() {
        return true;
    }
}
