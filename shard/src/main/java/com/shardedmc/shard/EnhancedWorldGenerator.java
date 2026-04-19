package com.shardedmc.shard;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

import java.util.Random;

/**
 * Enhanced world generator with varied terrain, biomes, trees, and decorations.
 * Uses simple noise functions to create interesting landscapes.
 */
public class EnhancedWorldGenerator implements Generator {
    
    private final long seed;
    private final Random random;
    
    public EnhancedWorldGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }
    
    public EnhancedWorldGenerator() {
        this(new Random().nextLong());
    }
    
    @Override
    public void generate(GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point size = unit.size();
        
        int startX = start.blockX();
        int startZ = start.blockZ();
        int sizeX = size.blockX();
        int sizeZ = size.blockZ();
        int startY = start.blockY();
        int sizeY = size.blockY();
        int endY = startY + sizeY;
        
        // Generate terrain height map
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                
                // Calculate terrain height using layered noise
                double heightNoise = getTerrainHeight(worldX, worldZ);
                int terrainHeight = (int) (60 + heightNoise * 20); // Base height 60, variation ±20
                
                // Clamp terrain height to valid range
                terrainHeight = Math.max(5, Math.min(terrainHeight, 100));
                
                // Determine biome based on temperature and humidity noise
                double temperature = getNoise(worldX * 0.002, worldZ * 0.002, seed + 1000);
                double humidity = getNoise(worldX * 0.003, worldZ * 0.003, seed + 2000);
                
                BiomeType biome = determineBiome(temperature, humidity);
                
                // Fill from bottom up to terrain height
                for (int y = startY; y < endY && y <= terrainHeight; y++) {
                    int depth = terrainHeight - y;
                    Block block;
                    
                    if (y < 3) {
                        block = Block.BEDROCK;
                    } else if (depth > 3) {
                        // Deep underground - stone with caves
                        if (y < 50 && y > 5 && isCave(worldX, y, worldZ)) {
                            block = Block.AIR;
                        } else {
                            block = Block.STONE;
                            // Add ores
                            if (depth > 5) {
                                block = getOreBlock(worldX, y, worldZ, block);
                            }
                        }
                    } else if (depth > 0) {
                        // Subsurface - dirt or sand
                        block = switch (biome) {
                            case DESERT -> Block.SAND;
                            case SNOWY -> Block.DIRT;
                            default -> Block.DIRT;
                        };
                    } else {
                        // Surface block
                        block = switch (biome) {
                            case DESERT -> Block.SAND;
                            case SNOWY -> Block.SNOW_BLOCK;
                            default -> Block.GRASS_BLOCK;
                        };
                    }
                    
                    if (block != Block.AIR) {
                        unit.modifier().setBlock(worldX, y, worldZ, block);
                    }
                }
                
                // Add surface decorations
                int surfaceY = terrainHeight + 1;
                if (surfaceY >= startY && surfaceY < endY) {
                    addSurfaceDecorations(unit, worldX, surfaceY, worldZ, biome);
                }
            }
        }
    }
    
    /**
     * Get the terrain height at a specific location (for spawn calculation)
     */
    public int getTerrainHeightAt(int worldX, int worldZ) {
        double heightNoise = getTerrainHeight(worldX, worldZ);
        return Math.max(5, Math.min((int) (60 + heightNoise * 20), 100));
    }
    
    /**
     * Calculate terrain height using layered noise (fbm)
     */
    private double getTerrainHeight(int x, int z) {
        double height = 0;
        double amplitude = 1.0;
        double frequency = 0.01;
        
        // 4 octaves of noise
        for (int i = 0; i < 4; i++) {
            height += getNoise(x * frequency, z * frequency, seed + i * 100) * amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        
        // Normalize to roughly [-1, 1]
        height = height / 1.875;
        
        return Math.max(-1.0, Math.min(1.0, height));
    }
    
    /**
     * Simple noise function (combination of sine waves for deterministic output)
     */
    private double getNoise(double x, double y, long seed) {
        double n = Math.sin(x * 12.9898 + y * 78.233 + seed * 0.1) * 43758.5453;
        n = n - Math.floor(n);
        
        // Add second octave
        double n2 = Math.sin(x * 37.719 + y * 17.374 + seed * 0.2) * 12765.1234;
        n2 = n2 - Math.floor(n2);
        
        return (n + n2 * 0.5) / 1.5 * 2.0 - 1.0; // Normalize to [-1, 1]
    }
    
    /**
     * Determine biome based on temperature and humidity
     */
    private BiomeType determineBiome(double temperature, double humidity) {
        if (temperature < -0.3) {
            return BiomeType.SNOWY;
        } else if (temperature > 0.3 && humidity < -0.2) {
            return BiomeType.DESERT;
        } else if (humidity > 0.3) {
            return BiomeType.FOREST;
        } else if (temperature > 0.2) {
            return BiomeType.PLAINS;
        } else {
            return BiomeType.TAIGA;
        }
    }
    
    /**
     * Check if position is a cave
     */
    private boolean isCave(int x, int y, int z) {
        if (y > 50 || y < 5) return false;
        
        double caveNoise = getNoise(x * 0.05, y * 0.08, seed + 5000L);
        return caveNoise > 0.7;
    }
    
    /**
     * Get ore block based on position and chance
     */
    private Block getOreBlock(int x, int y, int z, Block defaultBlock) {
        Random oreRandom = new Random(x * 234987L + y * 918273L + z * 555555L + seed);
        double chance = oreRandom.nextDouble();
        
        if (y < 16 && chance < 0.001) return Block.DIAMOND_ORE;
        if (y < 30 && chance < 0.003) return Block.GOLD_ORE;
        if (y < 50 && chance < 0.008) return Block.IRON_ORE;
        if (y < 60 && chance < 0.01) return Block.COAL_ORE;
        
        return defaultBlock;
    }
    
    /**
     * Add surface decorations like trees, grass, flowers
     */
    private void addSurfaceDecorations(GenerationUnit unit, int x, int y, int z, BiomeType biome) {
        // Use deterministic random based on position
        Random posRandom = new Random(x * 341873128712L + z * 132897987541L + seed);
        
        switch (biome) {
            case FOREST -> {
                // Trees (8% chance) - reduced to not spawn too many
                if (posRandom.nextDouble() < 0.08) {
                    generateTree(unit, x, y, z);
                }
                // Grass (50% chance)
                else if (posRandom.nextDouble() < 0.50) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
            case PLAINS -> {
                // Grass (70% chance)
                if (posRandom.nextDouble() < 0.70) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
                // Flowers (3% chance)
                else if (posRandom.nextDouble() < 0.03) {
                    Block flower = posRandom.nextBoolean() ? Block.POPPY : Block.DANDELION;
                    unit.modifier().setBlock(x, y, z, flower);
                }
            }
            case TAIGA -> {
                // Spruce trees (5% chance)
                if (posRandom.nextDouble() < 0.05) {
                    generateSpruceTree(unit, x, y, z);
                }
                // Grass (30% chance)
                else if (posRandom.nextDouble() < 0.30) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
            case SNOWY -> {
                // Snow layer on top
                unit.modifier().setBlock(x, y, z, Block.SNOW);
            }
            case DESERT -> {
                // Cacti (1% chance)
                if (posRandom.nextDouble() < 0.01) {
                    generateCactus(unit, x, y, z);
                }
                // Dead bushes (3% chance)
                else if (posRandom.nextDouble() < 0.03) {
                    unit.modifier().setBlock(x, y, z, Block.DEAD_BUSH);
                }
            }
        }
    }
    
    /**
     * Generate a simple oak tree
     */
    private void generateTree(GenerationUnit unit, int x, int y, int z) {
        int trunkHeight = 4 + random.nextInt(3);
        
        // Trunk
        for (int i = 0; i < trunkHeight; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.OAK_LOG);
        }
        
        // Leaves
        int leafStart = y + trunkHeight - 2;
        for (int ly = leafStart; ly < leafStart + 3; ly++) {
            int radius = ly == leafStart + 2 ? 1 : 2;
            for (int lx = -radius; lx <= radius; lx++) {
                for (int lz = -radius; lz <= radius; lz++) {
                    if (lx == 0 && lz == 0 && ly < y + trunkHeight) continue;
                    if (Math.abs(lx) == radius && Math.abs(lz) == radius && random.nextBoolean()) continue;
                    unit.modifier().setBlock(x + lx, ly, z + lz, Block.OAK_LEAVES);
                }
            }
        }
        
        // Top leaf
        unit.modifier().setBlock(x, leafStart + 3, z, Block.OAK_LEAVES);
    }
    
    /**
     * Generate a spruce tree
     */
    private void generateSpruceTree(GenerationUnit unit, int x, int y, int z) {
        int trunkHeight = 6 + random.nextInt(4);
        
        // Trunk
        for (int i = 0; i < trunkHeight; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.SPRUCE_LOG);
        }
        
        // Leaves (pyramid shape)
        int leafStart = y + 2;
        int radius = 2;
        for (int ly = leafStart; ly < y + trunkHeight + 1; ly++) {
            for (int lx = -radius; lx <= radius; lx++) {
                for (int lz = -radius; lz <= radius; lz++) {
                    if (lx == 0 && lz == 0 && ly < y + trunkHeight) continue;
                    if (Math.abs(lx) == radius && Math.abs(lz) == radius) continue;
                    unit.modifier().setBlock(x + lx, ly, z + lz, Block.SPRUCE_LEAVES);
                }
            }
            if ((ly - leafStart) % 2 == 1) radius = Math.max(0, radius - 1);
        }
    }
    
    /**
     * Generate a cactus
     */
    private void generateCactus(GenerationUnit unit, int x, int y, int z) {
        int height = 1 + random.nextInt(2);
        for (int i = 0; i < height; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.CACTUS);
        }
    }
    
    enum BiomeType {
        PLAINS, FOREST, DESERT, SNOWY, TAIGA
    }
}
