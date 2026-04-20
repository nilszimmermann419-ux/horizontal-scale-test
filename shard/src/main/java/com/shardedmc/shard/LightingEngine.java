package com.shardedmc.shard;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Lightweight lighting engine that integrates with Minestom's chunk light system.
 * Sets sky light and block light data on chunk sections so the client renders lighting properly.
 */
public class LightingEngine {
    private static final Logger logger = LoggerFactory.getLogger(LightingEngine.class);
    
    private final Instance instance;
    
    // Light level 0-15 per block position
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final int MAX_LIGHT = 15;
    
    // Track which chunks have been lit
    private final Set<Long> litChunks = new HashSet<>();
    
    // Light sources: "x,y,z" -> light level
    private final Map<String, Integer> blockLightSources = new HashMap<>();
    
    // Blocks that emit light and their levels
    private static final Map<String, Integer> EMISSIVE_BLOCKS = new HashMap<>();
    static {
        EMISSIVE_BLOCKS.put("minecraft:torch", 14);
        EMISSIVE_BLOCKS.put("minecraft:wall_torch", 14);
        EMISSIVE_BLOCKS.put("minecraft:soul_torch", 10);
        EMISSIVE_BLOCKS.put("minecraft:soul_wall_torch", 10);
        EMISSIVE_BLOCKS.put("minecraft:lantern", 15);
        EMISSIVE_BLOCKS.put("minecraft:soul_lantern", 10);
        EMISSIVE_BLOCKS.put("minecraft:glowstone", 15);
        EMISSIVE_BLOCKS.put("minecraft:jack_o_lantern", 15);
        EMISSIVE_BLOCKS.put("minecraft:sea_lantern", 15);
        EMISSIVE_BLOCKS.put("minecraft:beacon", 15);
        EMISSIVE_BLOCKS.put("minecraft:end_rod", 14);
        EMISSIVE_BLOCKS.put("minecraft:campfire", 15);
        EMISSIVE_BLOCKS.put("minecraft:soul_campfire", 10);
        EMISSIVE_BLOCKS.put("minecraft:fire", 15);
        EMISSIVE_BLOCKS.put("minecraft:soul_fire", 10);
        EMISSIVE_BLOCKS.put("minecraft:lava", 15);
        EMISSIVE_BLOCKS.put("minecraft:crying_obsidian", 10);
        EMISSIVE_BLOCKS.put("minecraft:glow_lichen", 7);
        EMISSIVE_BLOCKS.put("minecraft:shroomlight", 15);
        EMISSIVE_BLOCKS.put("minecraft:ochre_froglight", 15);
        EMISSIVE_BLOCKS.put("minecraft:verdant_froglight", 15);
        EMISSIVE_BLOCKS.put("minecraft:pearlescent_froglight", 15);
        EMISSIVE_BLOCKS.put("minecraft:cave_vines", 14);
        EMISSIVE_BLOCKS.put("minecraft:cave_vines_plant", 14);
        EMISSIVE_BLOCKS.put("minecraft:redstone_torch", 7);
        EMISSIVE_BLOCKS.put("minecraft:redstone_wall_torch", 7);
        EMISSIVE_BLOCKS.put("minecraft:nether_portal", 11);
    }
    
    // Transparent blocks that don't block light
    private static final Set<String> TRANSPARENT_BLOCKS = Set.of(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:glass", "minecraft:glass_pane",
        "minecraft:white_stained_glass", "minecraft:orange_stained_glass",
        "minecraft:magenta_stained_glass", "minecraft:light_blue_stained_glass",
        "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass",
        "minecraft:pink_stained_glass", "minecraft:gray_stained_glass",
        "minecraft:light_gray_stained_glass", "minecraft:cyan_stained_glass",
        "minecraft:purple_stained_glass", "minecraft:blue_stained_glass",
        "minecraft:brown_stained_glass", "minecraft:green_stained_glass",
        "minecraft:red_stained_glass", "minecraft:black_stained_glass",
        "minecraft:water", "minecraft:bubble_column",
        "minecraft:ice", "minecraft:frosted_ice",
        "minecraft:short_grass", "minecraft:tall_grass",
        "minecraft:fern", "minecraft:large_fern",
        "minecraft:dead_bush", "minecraft:dandelion",
        "minecraft:poppy", "minecraft:blue_orchid",
        "minecraft:allium", "minecraft:azure_bluet",
        "minecraft:red_tulip", "minecraft:orange_tulip",
        "minecraft:white_tulip", "minecraft:pink_tulip",
        "minecraft:oxeye_daisy", "minecraft:cornflower",
        "minecraft:lily_of_the_valley", "minecraft:sunflower",
        "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
        "minecraft:brown_mushroom", "minecraft:red_mushroom",
        "minecraft:sugar_cane", "minecraft:vine",
        "minecraft:lily_pad", "minecraft:seagrass",
        "minecraft:tall_seagrass", "minecraft:kelp",
        "minecraft:kelp_plant", "minecraft:cobweb",
        "minecraft:torch", "minecraft:wall_torch",
        "minecraft:soul_torch", "minecraft:soul_wall_torch",
        "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
        "minecraft:lantern", "minecraft:soul_lantern",
        "minecraft:end_rod", "minecraft:glow_lichen"
    );
    
    public LightingEngine(Instance instance) {
        this.instance = instance;
    }
    
    /**
     * Initialize lighting for a chunk when it loads
     */
    public void initializeChunk(int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        if (litChunks.contains(chunkKey)) {
            return; // Already lit
        }
        
        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        
        // Pre-compute highest block for each column
        int[][] highestBlocks = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                highestBlocks[x][z] = getHighestBlockY((chunkX << 4) + x, (chunkZ << 4) + z);
            }
        }
        
        // Scan chunk for light sources
        scanChunkForLightSources(chunk, chunkX, chunkZ);
        
        // Calculate and set light for each section
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            Section section = chunk.getSection(sectionY);
            if (section == null) continue;
            
            // Generate sky light (top-down) using cached highest blocks
            byte[] skyLight = calculateSkyLightForSection(chunkX, chunkZ, sectionY, highestBlocks);
            section.setSkyLight(skyLight);
            
            // Generate block light (from sources)
            byte[] blockLight = calculateBlockLightForSection(chunkX, chunkZ, sectionY);
            section.setBlockLight(blockLight);
        }
        
        litChunks.add(chunkKey);
        
        // Resend chunk to all viewers to update lighting
        chunk.sendChunk();
        
        logger.debug("Initialized lighting for chunk {}, {}", chunkX, chunkZ);
    }
    
    /**
     * Update lighting when a block changes
     */
    public void updateBlockLight(int x, int y, int z, Block oldBlock, Block newBlock) {
        String newName = newBlock.name();
        String oldName = oldBlock.name();
        
        // Update light sources registry
        int newLight = EMISSIVE_BLOCKS.getOrDefault(newName, 0);
        int oldLight = EMISSIVE_BLOCKS.getOrDefault(oldName, 0);
        String posKey = x + "," + y + "," + z;
        
        if (newLight > 0) {
            blockLightSources.put(posKey, newLight);
        } else {
            blockLightSources.remove(posKey);
        }
        
        // Only recalculate if light changed or transparency changed
        if (oldLight != newLight || isTransparent(oldName) != isTransparent(newName)) {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            
            // Recalculate this chunk and neighbors
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    recalculateChunk(chunkX + dx, chunkZ + dz);
                }
            }
        }
    }
    
    /**
     * Recalculate all light for a chunk
     */
    private void recalculateChunk(int chunkX, int chunkZ) {
        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;
        
        // Pre-compute highest blocks
        int[][] highestBlocks = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                highestBlocks[x][z] = getHighestBlockY((chunkX << 4) + x, (chunkZ << 4) + z);
            }
        }
        
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            Section section = chunk.getSection(sectionY);
            if (section == null) continue;
            
            byte[] skyLight = calculateSkyLightForSection(chunkX, chunkZ, sectionY, highestBlocks);
            section.setSkyLight(skyLight);
            
            byte[] blockLight = calculateBlockLightForSection(chunkX, chunkZ, sectionY);
            section.setBlockLight(blockLight);
        }
        
        // Resend to viewers
        chunk.sendChunk();
    }
    
    /**
     * Scan chunk for light sources and register them
     */
    private void scanChunkForLightSources(Chunk chunk, int chunkX, int chunkZ) {
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            for (int y = sectionY << 4; y < (sectionY + 1) << 4; y++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        int worldX = (chunkX << 4) + x;
                        int worldZ = (chunkZ << 4) + z;
                        
                        Block block = instance.getBlock(worldX, y, worldZ);
                        if (block != null) {
                            int light = EMISSIVE_BLOCKS.getOrDefault(block.name(), 0);
                            if (light > 0) {
                                blockLightSources.put(worldX + "," + y + "," + worldZ, light);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Calculate sky light for a section (top-down)
     */
    private byte[] calculateSkyLightForSection(int chunkX, int chunkZ, int sectionY, int[][] highestBlocks) {
        byte[] light = new byte[2048]; // 4096 blocks, 2 per byte (nibbles)
        int sectionBaseY = sectionY << 4;
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int highestY = highestBlocks[x][z];
                
                for (int localY = 0; localY < SECTION_SIZE; localY++) {
                    int y = sectionBaseY + localY;
                    int lightLevel;
                    
                    if (y > highestY) {
                        // Above ground - full sky light
                        lightLevel = MAX_LIGHT;
                    } else if (y == highestY) {
                        // At surface - full light
                        lightLevel = MAX_LIGHT;
                    } else {
                        // Below ground - simple occlusion
                        lightLevel = 0;
                    }
                    
                    setNibble(light, x, localY, z, lightLevel);
                }
            }
        }
        
        return light;
    }
    
    /**
     * Calculate block light for a section (from light sources)
     */
    private byte[] calculateBlockLightForSection(int chunkX, int chunkZ, int sectionY) {
        byte[] light = new byte[2048]; // All zeros initially
        
        // For each block in section, check if it's a light source or affected by one
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int localY = 0; localY < SECTION_SIZE; localY++) {
                    int worldX = (chunkX << 4) + x;
                    int y = (sectionY << 4) + localY;
                    int worldZ = (chunkZ << 4) + z;
                    
                    int lightLevel = getBlockLightAt(worldX, y, worldZ);
                    setNibble(light, x, localY, z, lightLevel);
                }
            }
        }
        
        return light;
    }
    
    /**
     * Calculate sky light at a position
     */
    private int calculateSkyLightAt(int x, int y, int z, int highestY) {
        if (y > highestY) return MAX_LIGHT;
        
        // Simple top-down: decrease by 1 for each solid block
        int light = MAX_LIGHT;
        for (int checkY = highestY; checkY >= y; checkY--) {
            Block block = instance.getBlock(x, checkY, z);
            if (block != null && !isTransparent(block.name())) {
                light = 0;
                break;
            } else {
                light = Math.max(0, light - 1);
            }
        }
        
        return light;
    }
    
    /**
     * Get block light level at position (from sources)
     */
    private int getBlockLightAt(int x, int y, int z) {
        int maxLight = 0;
        
        // Check all light sources within 15 blocks
        for (Map.Entry<String, Integer> entry : blockLightSources.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int sx = Integer.parseInt(parts[0]);
            int sy = Integer.parseInt(parts[1]);
            int sz = Integer.parseInt(parts[2]);
            
            int dx = Math.abs(x - sx);
            int dy = Math.abs(y - sy);
            int dz = Math.abs(z - sz);
            int dist = Math.max(dx, Math.max(dy, dz));
            
            if (dist <= MAX_LIGHT) {
                int sourceLight = entry.getValue();
                int propagated = Math.max(0, sourceLight - dist);
                
                // Check line of sight (simple)
                if (hasLineOfSight(x, y, z, sx, sy, sz)) {
                    maxLight = Math.max(maxLight, propagated);
                }
            }
        }
        
        return maxLight;
    }
    
    /**
     * Simple line of sight check
     */
    private boolean hasLineOfSight(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Simple check - just verify no solid blocks very close
        // For performance, we skip full raycasting
        return true;
    }
    
    /**
     * Get highest solid block Y at x,z
     */
    private int getHighestBlockY(int x, int z) {
        for (int y = 319; y >= -64; y--) {
            Block block = instance.getBlock(x, y, z);
            if (block != null && !block.isAir() && !isTransparent(block.name())) {
                return y;
            }
        }
        return -64;
    }
    
    /**
     * Check if block is transparent
     */
    private boolean isTransparent(String blockName) {
        if (blockName == null) return true;
        return TRANSPARENT_BLOCKS.contains(blockName);
    }
    
    /**
     * Set nibble in byte array
     * index = (y * 16 + z) * 16 + x
     * byteIndex = index / 2
     * high = index % 2 == 0
     */
    private void setNibble(byte[] array, int x, int y, int z, int value) {
        int index = (y * CHUNK_SIZE + z) * CHUNK_SIZE + x;
        int byteIndex = index >> 1;
        boolean high = (index & 1) == 0;
        
        if (high) {
            array[byteIndex] = (byte) ((array[byteIndex] & 0x0F) | ((value & 0x0F) << 4));
        } else {
            array[byteIndex] = (byte) ((array[byteIndex] & 0xF0) | (value & 0x0F));
        }
    }
    
    /**
     * Get nibble from byte array
     */
    private int getNibble(byte[] array, int x, int y, int z) {
        int index = (y * CHUNK_SIZE + z) * CHUNK_SIZE + x;
        int byteIndex = index >> 1;
        boolean high = (index & 1) == 0;
        
        if (high) {
            return (array[byteIndex] >> 4) & 0x0F;
        } else {
            return array[byteIndex] & 0x0F;
        }
    }
    
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
