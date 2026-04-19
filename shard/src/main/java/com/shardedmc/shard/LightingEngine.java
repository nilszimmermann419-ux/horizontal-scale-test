package com.shardedmc.shard;

import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight lighting engine for Minestom instances.
 * Handles block light propagation, sky light, and dynamic updates.
 */
public class LightingEngine {
    private static final Logger logger = LoggerFactory.getLogger(LightingEngine.class);
    
    private final Instance instance;
    
    // Light data: 0-15 (Minecraft light levels)
    // Maps chunk position to light arrays
    private final Map<Long, byte[]> blockLightMap = new ConcurrentHashMap<>();
    private final Map<Long, byte[]> skyLightMap = new ConcurrentHashMap<>();
    
    // Light sources: block position string "x,y,z" -> light level
    private final Map<String, Integer> lightSources = new ConcurrentHashMap<>();
    
    // Maximum light propagation distance
    private static final int MAX_LIGHT = 15;
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    
    // Blocks that emit light
    private static final Map<String, Integer> EMISSIVE_BLOCKS = new HashMap<>();
    
    static {
        // Vanilla light levels
        EMISSIVE_BLOCKS.put("minecraft:glowstone", 15);
        EMISSIVE_BLOCKS.put("minecraft:jack_o_lantern", 15);
        EMISSIVE_BLOCKS.put("minecraft:lantern", 15);
        EMISSIVE_BLOCKS.put("minecraft:sea_lantern", 15);
        EMISSIVE_BLOCKS.put("minecraft:beacon", 15);
        EMISSIVE_BLOCKS.put("minecraft:end_rod", 14);
        EMISSIVE_BLOCKS.put("minecraft:torch", 14);
        EMISSIVE_BLOCKS.put("minecraft:wall_torch", 14);
        EMISSIVE_BLOCKS.put("minecraft:campfire", 15);
        EMISSIVE_BLOCKS.put("minecraft:soul_campfire", 10);
        EMISSIVE_BLOCKS.put("minecraft:soul_torch", 10);
        EMISSIVE_BLOCKS.put("minecraft:soul_wall_torch", 10);
        EMISSIVE_BLOCKS.put("minecraft:redstone_torch", 7);
        EMISSIVE_BLOCKS.put("minecraft:redstone_wall_torch", 7);
        EMISSIVE_BLOCKS.put("minecraft:lava", 15);
        EMISSIVE_BLOCKS.put("minecraft:crying_obsidian", 10);
        EMISSIVE_BLOCKS.put("minecraft:glow_lichen", 7);
        EMISSIVE_BLOCKS.put("minecraft:candle", 3);
        EMISSIVE_BLOCKS.put("minecraft:fire", 15);
        EMISSIVE_BLOCKS.put("minecraft:soul_fire", 10);
        EMISSIVE_BLOCKS.put("minecraft: furnace", 13);
        EMISSIVE_BLOCKS.put("minecraft:blast_furnace", 13);
        EMISSIVE_BLOCKS.put("minecraft:smoker", 13);
        EMISSIVE_BLOCKS.put("minecraft:nether_portal", 11);
        EMISSIVE_BLOCKS.put("minecraft:shroomlight", 15);
        EMISSIVE_BLOCKS.put("minecraft:ochre_froglight", 15);
        EMISSIVE_BLOCKS.put("minecraft:verdant_froglight", 15);
        EMISSIVE_BLOCKS.put("minecraft:pearlescent_froglight", 15);
        EMISSIVE_BLOCKS.put("minecraft:cave_vines", 14);
        EMISSIVE_BLOCKS.put("minecraft:cave_vines_plant", 14);
    }
    
    // Blocks that are transparent to light
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
        "minecraft:dead_bush", "minecraft:flower",
        "minecraft:dandelion", "minecraft:poppy",
        "minecraft:blue_orchid", "minecraft:allium",
        "minecraft:azure_bluet", "minecraft:red_tulip",
        "minecraft:orange_tulip", "minecraft:white_tulip",
        "minecraft:pink_tulip", "minecraft:oxeye_daisy",
        "minecraft:cornflower", "minecraft:lily_of_the_valley",
        "minecraft:sunflower", "minecraft:lilac",
        "minecraft:rose_bush", "minecraft:peony",
        "minecraft:brown_mushroom", "minecraft:red_mushroom",
        "minecraft:sugar_cane", "minecraft:vine",
        "minecraft:lily_pad", "minecraft:seagrass",
        "minecraft:tall_seagrass", "minecraft:kelp",
        "minecraft:kelp_plant", "minecraft:torch",
        "minecraft:wall_torch", "minecraft:soul_torch",
        "minecraft:soul_wall_torch", "minecraft:redstone_torch",
        "minecraft:redstone_wall_torch", "minecraft:lantern",
        "minecraft:soul_lantern", "minecraft:end_rod",
        "minecraft:glow_lichen", "minecraft:cobweb"
    );
    
    public LightingEngine(Instance instance) {
        this.instance = instance;
    }
    
    /**
     * Initialize lighting for a chunk
     */
    public void initializeChunk(int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        if (!blockLightMap.containsKey(chunkKey)) {
            blockLightMap.put(chunkKey, new byte[CHUNK_SIZE * CHUNK_SIZE * 384]); // 16x16x384 (full world height)
            skyLightMap.put(chunkKey, new byte[CHUNK_SIZE * CHUNK_SIZE * 384]);
        }
        
        // Calculate sky light (top-down)
        calculateSkyLight(chunkX, chunkZ);
        
        // Calculate block light from sources
        calculateBlockLight(chunkX, chunkZ);
    }
    
    /**
     * Update lighting when a block changes
     */
    public void updateBlockLight(int x, int y, int z, Block oldBlock, Block newBlock) {
        String newBlockName = newBlock.name();
        String oldBlockName = oldBlock.name();
        
        // Check if this is a light source
        int newLight = EMISSIVE_BLOCKS.getOrDefault(newBlockName, 0);
        int oldLight = EMISSIVE_BLOCKS.getOrDefault(oldBlockName, 0);
        
        String posKey = x + "," + y + "," + z;
        
        if (newLight > 0) {
            lightSources.put(posKey, newLight);
        } else {
            lightSources.remove(posKey);
        }
        
        // Re-calculate light around this block
        if (oldLight != newLight || isTransparent(newBlockName) != isTransparent(oldBlockName)) {
            // Update block light
            recalculateBlockLightAround(x, y, z);
            
            // Update sky light if needed
            if (y > getHighestBlockY(x, z) || isTransparent(newBlockName)) {
                recalculateSkyLightAround(x, z);
            }
        }
    }
    
    /**
     * Get light level at position (block light + sky light)
     */
    public int getLightLevel(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        byte[] blockLight = blockLightMap.get(chunkKey);
        byte[] skyLight = skyLightMap.get(chunkKey);
        
        if (blockLight == null || skyLight == null) {
            return 15; // Full light if not initialized
        }
        
        int localX = x & 0xF;
        int localZ = z & 0xF;
        int index = getIndex(localX, y, localZ);
        
        if (index < 0 || index >= blockLight.length) {
            return 15;
        }
        
        int block = Byte.toUnsignedInt(blockLight[index]);
        int sky = Byte.toUnsignedInt(skyLight[index]);
        
        return Math.max(block, sky);
    }
    
    /**
     * Get block light level
     */
    public int getBlockLight(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        byte[] light = blockLightMap.get(chunkKey);
        if (light == null) return 0;
        
        int index = getIndex(x & 0xF, y, z & 0xF);
        if (index < 0 || index >= light.length) return 0;
        
        return Byte.toUnsignedInt(light[index]);
    }
    
    /**
     * Get sky light level
     */
    public int getSkyLight(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        byte[] light = skyLightMap.get(chunkKey);
        if (light == null) return 15;
        
        int index = getIndex(x & 0xF, y, z & 0xF);
        if (index < 0 || index >= light.length) return 15;
        
        return Byte.toUnsignedInt(light[index]);
    }
    
    /**
     * Calculate sky light for a chunk (top-down propagation)
     */
    private void calculateSkyLight(int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        byte[] skyLight = skyLightMap.get(chunkKey);
        if (skyLight == null) return;
        
        // Initialize all to 0
        Arrays.fill(skyLight, (byte) 0);
        
        // Top-down propagation
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;
                
                int highestY = getHighestBlockY(worldX, worldZ);
                int light = 15;
                
                // Light propagates downward from top
                for (int y = 383; y >= -64; y--) {
                    int index = getIndex(localX, y, localZ);
                    if (index < 0 || index >= skyLight.length) continue;
                    
                    if (y > highestY) {
                        // Above ground - full sky light
                        skyLight[index] = (byte) 15;
                    } else {
                        // Below ground - propagate light
                        Block block = instance.getBlock(worldX, y, worldZ);
                        if (block != null && !isTransparent(block.name())) {
                            // Solid block blocks light
                            light = 0;
                        } else {
                            // Transparent block - light decreases by 1
                            light = Math.max(0, light - 1);
                        }
                        skyLight[index] = (byte) light;
                    }
                }
            }
        }
    }
    
    /**
     * Calculate block light from sources
     */
    private void calculateBlockLight(int chunkX, int chunkZ) {
        // Clear existing block light
        long chunkKey = getChunkKey(chunkX, chunkZ);
        byte[] blockLight = blockLightMap.get(chunkKey);
        if (blockLight == null) return;
        Arrays.fill(blockLight, (byte) 0);
        
        // Find all light sources in and around this chunk
        List<int[]> sources = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int checkChunkX = chunkX + dx;
                int checkChunkZ = chunkZ + dz;
                
                for (Map.Entry<String, Integer> entry : lightSources.entrySet()) {
                    String[] parts = entry.getKey().split(",");
                    int sx = Integer.parseInt(parts[0]);
                    int sy = Integer.parseInt(parts[1]);
                    int sz = Integer.parseInt(parts[2]);
                    if ((sx >> 4) == checkChunkX && (sz >> 4) == checkChunkZ) {
                        sources.add(new int[]{sx, sy, sz, entry.getValue()});
                    }
                }
            }
        }
        
        // Propagate from each source
        for (int[] source : sources) {
            propagateBlockLight(source[0], source[1], source[2], source[3]);
        }
    }
    
    /**
     * Propagate block light using flood-fill algorithm
     */
    private void propagateBlockLight(int startX, int startY, int startZ, int startLight) {
        Queue<LightNode> queue = new LinkedList<>();
        queue.add(new LightNode(startX, startY, startZ, startLight));
        
        Set<String> visited = new HashSet<>();
        visited.add(startX + "," + startY + "," + startZ);
        
        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            
            // Set light at this position
            setBlockLight(node.x, node.y, node.z, node.light);
            
            // Propagate to neighbors
            if (node.light > 1) {
                for (int[] dir : DIRECTIONS) {
                    int nx = node.x + dir[0];
                    int ny = node.y + dir[1];
                    int nz = node.z + dir[2];
                    
                    String key = nx + "," + ny + "," + nz;
                    if (visited.contains(key)) continue;
                    
                    Block block = instance.getBlock(nx, ny, nz);
                    if (block == null) continue;
                    
                    // Check if block is transparent
                    if (!isTransparent(block.name())) continue;
                    
                    int newLight = node.light - 1;
                    int existingLight = getBlockLight(nx, ny, nz);
                    
                    if (newLight > existingLight) {
                        visited.add(key);
                        queue.add(new LightNode(nx, ny, nz, newLight));
                    }
                }
            }
        }
    }
    
    /**
     * Recalculate block light around a position
     */
    private void recalculateBlockLightAround(int centerX, int centerY, int centerZ) {
        // Recalculate in a radius of MAX_LIGHT chunks
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                calculateBlockLight(chunkX + dx, chunkZ + dz);
            }
        }
    }
    
    /**
     * Recalculate sky light around a column
     */
    private void recalculateSkyLightAround(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        calculateSkyLight(chunkX, chunkZ);
    }
    
    /**
     * Set block light at position
     */
    private void setBlockLight(int x, int y, int z, int light) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        byte[] blockLight = blockLightMap.get(chunkKey);
        if (blockLight == null) return;
        
        int index = getIndex(x & 0xF, y, z & 0xF);
        if (index < 0 || index >= blockLight.length) return;
        
        blockLight[index] = (byte) light;
    }
    
    /**
     * Get highest block Y at position
     */
    private int getHighestBlockY(int x, int z) {
        for (int y = 383; y >= -64; y--) {
            Block block = instance.getBlock(x, y, z);
            if (block != null && !block.isAir()) {
                return y;
            }
        }
        return -64;
    }
    
    /**
     * Check if block is transparent to light
     */
    private boolean isTransparent(String blockName) {
        if (blockName == null) return true;
        return TRANSPARENT_BLOCKS.contains(blockName);
    }
    
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    private int getIndex(int x, int y, int z) {
        // Support full world height (-64 to 319 = 384 blocks)
        int adjustedY = y + 64;
        if (adjustedY < 0 || adjustedY >= 384) return -1;
        return (adjustedY * CHUNK_SIZE + z) * CHUNK_SIZE + x;
    }
    
    private static final int[][] DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };
    
    private record LightNode(int x, int y, int z, int light) {}
}
