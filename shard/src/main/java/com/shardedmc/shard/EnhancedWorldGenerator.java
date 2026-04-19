package com.shardedmc.shard;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

import java.util.Random;

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
        
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                
                // Calculate terrain height
                double heightNoise = getTerrainHeight(worldX, worldZ);
                int terrainHeight = (int) (60 + heightNoise * 20);
                terrainHeight = Math.max(5, Math.min(terrainHeight, 100));
                
                // Biome
                double temperature = getNoise(worldX * 0.002, worldZ * 0.002, seed + 1000);
                double humidity = getNoise(worldX * 0.003, worldZ * 0.003, seed + 2000);
                BiomeType biome = determineBiome(temperature, humidity);
                
                // Generate column from bottom to terrain height
                for (int y = startY; y < endY && y <= terrainHeight; y++) {
                    int depth = terrainHeight - y;
                    Block block;
                    
                    if (y < 3) {
                        block = Block.BEDROCK;
                    } else if (depth > 5) {
                        // Check caves
                        if (y < 50 && y > 5 && isCave(worldX, y, worldZ)) {
                            continue; // Skip - leave air
                        }
                        block = Block.STONE;
                        // Add ores randomly
                        if (depth > 8) {
                            block = getOreBlock(worldX, y, worldZ, block);
                        }
                    } else if (depth > 0) {
                        block = switch (biome) {
                            case DESERT -> Block.SAND;
                            default -> Block.DIRT;
                        };
                    } else {
                        block = switch (biome) {
                            case DESERT -> Block.SAND;
                            case SNOWY -> Block.SNOW_BLOCK;
                            default -> Block.GRASS_BLOCK;
                        };
                    }
                    
                    unit.modifier().setBlock(worldX, y, worldZ, block);
                }
                
                // Surface decorations
                int surfaceY = terrainHeight + 1;
                if (surfaceY >= startY && surfaceY < endY) {
                    addSurfaceDecorations(unit, worldX, surfaceY, worldZ, biome);
                }
            }
        }
    }
    
    public int getTerrainHeightAt(int worldX, int worldZ) {
        double heightNoise = getTerrainHeight(worldX, worldZ);
        return Math.max(5, Math.min((int) (60 + heightNoise * 20), 100));
    }
    
    private double getTerrainHeight(int x, int z) {
        double height = 0;
        double amplitude = 1.0;
        double frequency = 0.01;
        
        for (int i = 0; i < 4; i++) {
            height += getNoise(x * frequency, z * frequency, seed + i * 100) * amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        
        height = height / 1.875;
        return Math.max(-1.0, Math.min(1.0, height));
    }
    
    private double getNoise(double x, double y, long seed) {
        double n = Math.sin(x * 12.9898 + y * 78.233 + seed * 0.1) * 43758.5453;
        n = n - Math.floor(n);
        
        double n2 = Math.sin(x * 37.719 + y * 17.374 + seed * 0.2) * 12765.1234;
        n2 = n2 - Math.floor(n2);
        
        return (n + n2 * 0.5) / 1.5 * 2.0 - 1.0;
    }
    
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
    
    private boolean isCave(int x, int y, int z) {
        if (y > 50 || y < 5) return false;
        double caveNoise = getNoise(x * 0.05, y * 0.08, seed + 5000L);
        return caveNoise > 0.7;
    }
    
    private Block getOreBlock(int x, int y, int z, Block defaultBlock) {
        Random oreRandom = new Random(x * 234987L + y * 918273L + z * 555555L + seed);
        double chance = oreRandom.nextDouble();
        
        if (y < 16 && chance < 0.001) return Block.DIAMOND_ORE;
        if (y < 30 && chance < 0.003) return Block.GOLD_ORE;
        if (y < 50 && chance < 0.008) return Block.IRON_ORE;
        if (y < 60 && chance < 0.01) return Block.COAL_ORE;
        
        return defaultBlock;
    }
    
    private void addSurfaceDecorations(GenerationUnit unit, int x, int y, int z, BiomeType biome) {
        Random posRandom = new Random(x * 341873128712L + z * 132897987541L + seed);
        
        switch (biome) {
            case FOREST -> {
                if (posRandom.nextDouble() < 0.08) {
                    generateTree(unit, x, y, z);
                } else if (posRandom.nextDouble() < 0.50) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
            case PLAINS -> {
                if (posRandom.nextDouble() < 0.70) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                } else if (posRandom.nextDouble() < 0.03) {
                    Block flower = posRandom.nextBoolean() ? Block.POPPY : Block.DANDELION;
                    unit.modifier().setBlock(x, y, z, flower);
                }
            }
            case TAIGA -> {
                if (posRandom.nextDouble() < 0.05) {
                    generateSpruceTree(unit, x, y, z);
                } else if (posRandom.nextDouble() < 0.30) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
            case SNOWY -> {
                unit.modifier().setBlock(x, y, z, Block.SNOW);
            }
            case DESERT -> {
                if (posRandom.nextDouble() < 0.01) {
                    generateCactus(unit, x, y, z);
                } else if (posRandom.nextDouble() < 0.03) {
                    unit.modifier().setBlock(x, y, z, Block.DEAD_BUSH);
                }
            }
        }
    }
    
    private void generateTree(GenerationUnit unit, int x, int y, int z) {
        int trunkHeight = 4 + random.nextInt(3);
        
        for (int i = 0; i < trunkHeight; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.OAK_LOG);
        }
        
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
        
        unit.modifier().setBlock(x, leafStart + 3, z, Block.OAK_LEAVES);
    }
    
    private void generateSpruceTree(GenerationUnit unit, int x, int y, int z) {
        int trunkHeight = 6 + random.nextInt(4);
        
        for (int i = 0; i < trunkHeight; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.SPRUCE_LOG);
        }
        
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
