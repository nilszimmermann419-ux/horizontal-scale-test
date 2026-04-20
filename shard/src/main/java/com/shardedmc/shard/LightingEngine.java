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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight lighting engine that integrates with Minestom's chunk light system.
 * Sets sky light and block light data on chunk sections so the client renders lighting properly.
 * 
 * Performance optimizations:
 * - Spatial indexing for light sources (chunk-based)
 * - Cached highest block Y values per chunk
 * - BFS light propagation queue instead of nested loops over all sources
 * - Reusable light arrays to reduce allocations
 */
public class LightingEngine {
    private static final Logger logger = LoggerFactory.getLogger(LightingEngine.class);
    
    private final Instance instance;
    
    // Light level 0-15 per block position
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final int MAX_LIGHT = 15;
    private static final int LIGHT_RADIUS = 15;
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int LIGHT_ARRAY_SIZE = SECTION_BLOCKS / 2; // 2048 bytes (2 blocks per byte)
    
    // Track which chunks have been lit
    private final Set<Long> litChunks = ConcurrentHashMap.newKeySet();
    
    // Spatial index for light sources: chunkKey -> list of sources in that chunk
    // This avoids iterating all sources for every block
    private final Map<Long, List<LightSource>> lightSourceIndex = new ConcurrentHashMap<>();
    
    // Cache for highest solid block Y per column: chunkKey -> int[16*16]
    private final Map<Long, int[]> highestBlockCache = new ConcurrentHashMap<>();
    
    // Reusable light arrays to avoid allocation per section
    private final ThreadLocal<byte[]> reusableSkyLight = ThreadLocal.withInitial(() -> new byte[LIGHT_ARRAY_SIZE]);
    private final ThreadLocal<byte[]> reusableBlockLight = ThreadLocal.withInitial(() -> new byte[LIGHT_ARRAY_SIZE]);
    
    // Light propagation queue for BFS
    private static class LightNode {
        final int x, y, z;
        final int level;
        
        LightNode(int x, int y, int z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }
    
    // Light source representation
    private static class LightSource {
        final int x, y, z;
        final int level;
        
        LightSource(int x, int y, int z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }
    
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
        
        // Pre-compute and cache highest block for each column
        int[] highestBlocks = computeHighestBlocks(chunkX, chunkZ);
        highestBlockCache.put(chunkKey, highestBlocks);
        
        // Scan chunk for light sources and add to spatial index
        scanChunkForLightSources(chunk, chunkX, chunkZ);
        
        // Calculate and set light for each section
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            Section section = chunk.getSection(sectionY);
            if (section == null) continue;
            
            // Generate sky light (top-down) using cached highest blocks
            byte[] skyLight = calculateSkyLightForSection(chunkX, chunkZ, sectionY, highestBlocks);
            section.setSkyLight(skyLight);
            
            // Generate block light using BFS propagation from nearby sources
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
        
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        if (newLight > 0) {
            addLightSource(chunkKey, x, y, z, newLight);
        } else if (oldLight > 0) {
            removeLightSource(chunkKey, x, y, z);
        }
        
        // Update highest block cache if needed
        if (!isTransparent(newName) || !isTransparent(oldName)) {
            highestBlockCache.remove(chunkKey);
        }
        
        // Only recalculate if light changed or transparency changed
        if (oldLight != newLight || isTransparent(oldName) != isTransparent(newName)) {
            // Recalculate this chunk and neighbors (light can propagate across chunk boundaries)
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
        
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        // Recompute and cache highest blocks
        int[] highestBlocks = computeHighestBlocks(chunkX, chunkZ);
        highestBlockCache.put(chunkKey, highestBlocks);
        
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
     * Scan chunk for light sources and register them in spatial index
     */
    private void scanChunkForLightSources(Chunk chunk, int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            int sectionBaseY = sectionY << 4;
            for (int y = sectionBaseY; y < sectionBaseY + SECTION_SIZE; y++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        int worldX = (chunkX << 4) + x;
                        int worldZ = (chunkZ << 4) + z;
                        
                        Block block = instance.getBlock(worldX, y, worldZ);
                        if (block != null) {
                            int light = EMISSIVE_BLOCKS.getOrDefault(block.name(), 0);
                            if (light > 0) {
                                addLightSource(chunkKey, worldX, y, worldZ, light);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Add a light source to the spatial index
     */
    private void addLightSource(long chunkKey, int x, int y, int z, int level) {
        lightSourceIndex.computeIfAbsent(chunkKey, k -> new ArrayList<>())
            .add(new LightSource(x, y, z, level));
    }
    
    /**
     * Remove a light source from the spatial index
     */
    private void removeLightSource(long chunkKey, int x, int y, int z) {
        List<LightSource> sources = lightSourceIndex.get(chunkKey);
        if (sources != null) {
            sources.removeIf(src -> src.x == x && src.y == y && src.z == z);
            if (sources.isEmpty()) {
                lightSourceIndex.remove(chunkKey);
            }
        }
    }
    
    /**
     * Compute highest solid block Y for each column in a chunk
     */
    private int[] computeHighestBlocks(int chunkX, int chunkZ) {
        int[] highest = new int[CHUNK_SIZE * CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;
                highest[x * CHUNK_SIZE + z] = getHighestBlockY(worldX, worldZ);
            }
        }
        return highest;
    }
    
    /**
     * Calculate sky light for a section (top-down)
     */
    private byte[] calculateSkyLightForSection(int chunkX, int chunkZ, int sectionY, int[] highestBlocks) {
        byte[] light = reusableSkyLight.get();
        Arrays.fill(light, (byte) 0); // Reset array
        
        int sectionBaseY = sectionY << 4;
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int highestY = highestBlocks[x * CHUNK_SIZE + z];
                
                for (int localY = 0; localY < SECTION_SIZE; localY++) {
                    int y = sectionBaseY + localY;
                    int lightLevel;
                    
                    if (y >= highestY) {
                        // Above or at surface - full sky light
                        lightLevel = MAX_LIGHT;
                    } else {
                        // Below ground - simple occlusion
                        lightLevel = 0;
                    }
                    
                    setNibble(light, x, localY, z, lightLevel);
                }
            }
        }
        
        // Return a copy since the reusable array will be modified
        return Arrays.copyOf(light, light.length);
    }
    
    /**
     * Calculate block light for a section using BFS propagation from nearby sources
     */
    private byte[] calculateBlockLightForSection(int chunkX, int chunkZ, int sectionY) {
        byte[] light = reusableBlockLight.get();
        Arrays.fill(light, (byte) 0); // Reset array
        
        int sectionBaseY = sectionY << 4;
        int sectionMaxY = sectionBaseY + SECTION_SIZE - 1;
        
        // Collect all light sources that could affect this section
        // Sources within LIGHT_RADIUS blocks of the section boundaries
        ArrayDeque<LightNode> queue = new ArrayDeque<>();
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long nearbyChunkKey = getChunkKey(chunkX + dx, chunkZ + dz);
                List<LightSource> sources = lightSourceIndex.get(nearbyChunkKey);
                if (sources == null) continue;
                
                for (LightSource src : sources) {
                    // Check if source could affect this section
                    if (src.y < sectionBaseY - LIGHT_RADIUS || src.y > sectionMaxY + LIGHT_RADIUS) {
                        continue;
                    }
                    
                    // Start BFS from this source
                    queue.add(new LightNode(src.x, src.y, src.z, src.level));
                }
            }
        }
        
        // BFS propagation
        // Use a simple visited set with position keys to avoid cycles
        // Since we process in decreasing light level order, we can just check if current is higher
        Set<Long> visited = new HashSet<>();
        
        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            
            // Check if this node is within the current section
            int nodeChunkX = node.x >> 4;
            int nodeChunkZ = node.z >> 4;
            
            if (nodeChunkX == chunkX && nodeChunkZ == chunkZ) {
                int localY = node.y - sectionBaseY;
                if (localY >= 0 && localY < SECTION_SIZE) {
                    int localX = node.x & 0xF;
                    int localZ = node.z & 0xF;
                    int currentLight = getNibble(light, localX, localY, localZ);
                    if (node.level > currentLight) {
                        setNibble(light, localX, localY, localZ, node.level);
                    }
                }
            }
            
            if (node.level <= 1) continue; // No more propagation
            
            // Propagate to neighbors
            int nextLevel = node.level - 1;
            
            // Check 6 neighbors
            tryPropagate(queue, visited, node.x + 1, node.y, node.z, nextLevel);
            tryPropagate(queue, visited, node.x - 1, node.y, node.z, nextLevel);
            tryPropagate(queue, visited, node.x, node.y + 1, node.z, nextLevel);
            tryPropagate(queue, visited, node.x, node.y - 1, node.z, nextLevel);
            tryPropagate(queue, visited, node.x, node.y, node.z + 1, nextLevel);
            tryPropagate(queue, visited, node.x, node.y, node.z - 1, nextLevel);
        }
        
        // Return a copy since the reusable array will be modified
        return Arrays.copyOf(light, light.length);
    }
    
    /**
     * Try to propagate light to a neighbor position
     */
    private void tryPropagate(ArrayDeque<LightNode> queue, Set<Long> visited, 
                               int x, int y, int z, int level) {
        if (y < -64 || y > 319) return; // Out of world bounds
        
        long posKey = ((long) x << 32) | ((long) y << 16) | (z & 0xFFFFL);
        if (!visited.add(posKey)) return; // Already visited
        
        Block block = instance.getBlock(x, y, z);
        if (block != null && !isTransparent(block.name())) {
            return; // Solid block blocks light
        }
        
        queue.add(new LightNode(x, y, z, level));
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
