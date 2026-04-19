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
        int sizeY = size.blockY();
        
        // Generate terrain height map
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                
                // Calculate terrain height using layered noise
                double heightNoise = getTerrainHeight(worldX, worldZ);
                int terrainHeight = (int) (60 + heightNoise * 20); // Base height 60, variation ±20
                
                // Determine biome based on temperature and humidity noise
                double temperature = getNoise(worldX * 0.002, worldZ * 0.002, seed + 1000);
                double humidity = getNoise(worldX * 0.003, worldZ * 0.003, seed + 2000);
                
                BiomeType biome = determineBiome(temperature, humidity);
                
                // Generate terrain column
                for (int y = 0; y < sizeY && y + start.blockY() <= terrainHeight; y++) {
                    int worldY = start.blockY() + y;
                    Block block = getBlockForDepth(worldY, terrainHeight, biome);
                    unit.modifier().setBlock(worldX, worldY, worldZ, block);
                }
                
                // Fill below terrain with stone
                for (int y = 0; y < sizeY && y + start.blockY() < terrainHeight - 3; y++) {
                    int worldY = start.blockY() + y;
                    if (worldY < terrainHeight - 3) {
                        // Add caves
                        if (!isCave(worldX, worldY, worldZ)) {
                            unit.modifier().setBlock(worldX, worldY, worldZ, Block.STONE);
                        }
                    }
                }
                
                // Generate bedrock at bottom
                if (start.blockY() <= 0) {
                    for (int y = 0; y < sizeY && start.blockY() + y < 5; y++) {
                        int worldY = start.blockY() + y;
                        if (worldY >= 0 && worldY < 3) {
                            unit.modifier().setBlock(worldX, worldY, worldZ, Block.BEDROCK);
                        }
                    }
                }
                
                // Add surface decorations (trees, grass, etc.)
                if (start.blockY() <= terrainHeight && terrainHeight < start.blockY() + sizeY) {
                    addSurfaceDecorations(unit, worldX, terrainHeight + 1, worldZ, biome);
                }
            }
        }
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
        
        // Sharpen peaks
        height = Math.pow(Math.abs(height), 1.2) * Math.signum(height);
        
        return height;
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
     * Get block type based on depth and biome
     */
    private Block getBlockForDepth(int y, int terrainHeight, BiomeType biome) {
        int depth = terrainHeight - y;
        
        if (depth == 0) {
            // Surface block
            return switch (biome) {
                case DESERT -> Block.SAND;
                case SNOWY -> Block.SNOW_BLOCK;
                default -> Block.GRASS_BLOCK;
            };
        } else if (depth <= 3) {
            // Subsurface
            return switch (biome) {
                case DESERT -> Block.SAND;
                case SNOWY -> Block.DIRT;
                default -> Block.DIRT;
            };
        } else {
            return Block.STONE;
        }
    }
    
    /**
     * Simple cave generation
     */
    private boolean isCave(int x, int y, int z) {
        if (y > 50 || y < 5) return false;
        
        double caveNoise = getNoise(x * 0.05, y * 0.08, seed + 5000L);
        return caveNoise > 0.7;
    }
    
    /**
     * Add surface decorations like trees, grass, flowers
     */
    private void addSurfaceDecorations(GenerationUnit unit, int x, int y, int z, BiomeType biome) {
        // Use deterministic random based on position
        Random posRandom = new Random(x * 341873128712L + z * 132897987541L + seed);
        
        switch (biome) {
            case FOREST -> {
                // Trees (10% chance)
                if (posRandom.nextDouble() < 0.10) {
                    generateTree(unit, x, y, z);
                }
                // Grass (60% chance)
                else if (posRandom.nextDouble() < 0.60) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
            case PLAINS -> {
                // Grass (80% chance)
                if (posRandom.nextDouble() < 0.80) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
                // Flowers (5% chance)
                else if (posRandom.nextDouble() < 0.05) {
                    Block flower = posRandom.nextBoolean() ? Block.POPPY : Block.DANDELION;
                    unit.modifier().setBlock(x, y, z, flower);
                }
            }
            case TAIGA -> {
                // Spruce trees (8% chance)
                if (posRandom.nextDouble() < 0.08) {
                    generateSpruceTree(unit, x, y, z);
                }
                // Grass (40% chance)
                else if (posRandom.nextDouble() < 0.40) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
            case SNOWY -> {
                // Snow layer on top
                unit.modifier().setBlock(x, y, z, Block.SNOW);
            }
            case DESERT -> {
                // Cacti (2% chance)
                if (posRandom.nextDouble() < 0.02) {
                    generateCactus(unit, x, y, z);
                }
                // Dead bushes (5% chance)
                else if (posRandom.nextDouble() < 0.05) {
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
    
    /**
     * Generate ore veins
     */
    private void generateOres(GenerationUnit unit, int x, int y, int z) {
        // Coal (common)
        if (y < 60 && random.nextDouble() < 0.01) {
            generateOreVein(unit, x, y, z, Block.COAL_ORE, 8);
        }
        // Iron
        if (y < 50 && random.nextDouble() < 0.008) {
            generateOreVein(unit, x, y, z, Block.IRON_ORE, 6);
        }
        // Gold
        if (y < 30 && random.nextDouble() < 0.003) {
            generateOreVein(unit, x, y, z, Block.GOLD_ORE, 4);
        }
        // Diamond
        if (y < 16 && random.nextDouble() < 0.001) {
            generateOreVein(unit, x, y, z, Block.DIAMOND_ORE, 3);
        }
    }
    
    private void generateOreVein(GenerationUnit unit, int x, int y, int z, Block ore, int size) {
        for (int i = 0; i < size; i++) {
            int ox = x + random.nextInt(3) - 1;
            int oy = y + random.nextInt(3) - 1;
            int oz = z + random.nextInt(3) - 1;
            unit.modifier().setBlock(ox, oy, oz, ore);
        }
    }
    
    enum BiomeType {
        PLAINS, FOREST, DESERT, SNOWY, TAIGA
    }
}
