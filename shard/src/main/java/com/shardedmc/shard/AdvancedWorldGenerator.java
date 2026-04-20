package com.shardedmc.shard;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

import java.util.Random;

public class AdvancedWorldGenerator implements Generator {
    
    private final long seed;
    private final Random random;
    private final FastNoiseLite biomeNoise;
    private final FastNoiseLite terrainNoise;
    private final FastNoiseLite detailNoise;
    private final FastNoiseLite caveNoise;
    
    public AdvancedWorldGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        
        // Biome noise - large scale features
        this.biomeNoise = new FastNoiseLite((int) seed);
        this.biomeNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.biomeNoise.SetFrequency(0.002f);
        
        // Terrain noise - medium scale height variation
        this.terrainNoise = new FastNoiseLite((int) (seed + 1));
        this.terrainNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.terrainNoise.SetFrequency(0.01f);
        this.terrainNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        this.terrainNoise.SetFractalOctaves(5);
        
        // Detail noise - small bumps and features
        this.detailNoise = new FastNoiseLite((int) (seed + 2));
        this.detailNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.detailNoise.SetFrequency(0.05f);
        this.detailNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        this.detailNoise.SetFractalOctaves(3);
        
        // Cave noise - 3D caves
        this.caveNoise = new FastNoiseLite((int) (seed + 3));
        this.caveNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.caveNoise.SetFrequency(0.03f);
        this.caveNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        this.caveNoise.SetFractalOctaves(3);
    }
    
    public AdvancedWorldGenerator() {
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
        int endX = startX + sizeX;
        int endZ = startZ + sizeZ;
        
        // Pre-calculate biome and height maps
        BiomeType[][] biomeMap = new BiomeType[sizeX][sizeZ];
        int[][] heightMap = new int[sizeX][sizeZ];
        
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                
                // Calculate biome
                biomeMap[x][z] = calculateBiome(worldX, worldZ);
                
                // Calculate height
                heightMap[x][z] = calculateHeight(worldX, worldZ, biomeMap[x][z]);
            }
        }
        
        // Generate terrain
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int terrainHeight = heightMap[x][z];
                BiomeType biome = biomeMap[x][z];
                
                // Generate column
                for (int y = startY; y < endY; y++) {
                    if (y <= terrainHeight) {
                        Block block = getBlockForDepth(worldX, y, worldZ, terrainHeight, biome);
                        if (block != null) {
                            unit.modifier().setBlock(x, y, z, block);
                        }
                    } else if (y <= getWaterLevel() && biome != BiomeType.OCEAN_DEEP) {
                        // Water above terrain but below water level
                        if (biome == BiomeType.OCEAN || biome == BiomeType.RIVER || biome == BiomeType.BEACH) {
                            unit.modifier().setBlock(x, y, z, Block.WATER);
                        }
                    }
                }
                
                // Surface decorations - only place if within unit bounds
                int surfaceY = terrainHeight + 1;
                if (surfaceY >= startY && surfaceY < endY) {
                    addSurfaceDecorations(unit, x, surfaceY, z, biome, terrainHeight, sizeX, sizeZ);
                }
            }
        }
    }
    
    private BiomeType calculateBiome(int worldX, int worldZ) {
        // Multi-octave biome noise for smooth transitions
        float temp = biomeNoise.GetNoise(worldX, worldZ + 1000);
        float humidity = biomeNoise.GetNoise(worldX + 2000, worldZ);
        float elevation = terrainNoise.GetNoise(worldX, worldZ);
        
        // Water bodies
        float waterNoise = biomeNoise.GetNoise(worldX * 0.5f, worldZ * 0.5f + 5000);
        
        // Deep ocean
        if (waterNoise < -0.6f) {
            return BiomeType.OCEAN_DEEP;
        }
        
        // Ocean
        if (waterNoise < -0.3f) {
            return BiomeType.OCEAN;
        }
        
        // Beach (near water)
        if (waterNoise < -0.25f) {
            return BiomeType.BEACH;
        }
        
        // River
        float riverNoise = biomeNoise.GetNoise(worldX * 2f, worldZ * 2f + 3000);
        if (riverNoise > 0.7f && waterNoise > -0.2f) {
            return BiomeType.RIVER;
        }
        
        // Mountains
        if (elevation > 0.5f) {
            if (temp < -0.2f) {
                return BiomeType.MOUNTAIN_SNOW;
            }
            return BiomeType.MOUNTAIN;
        }
        
        // Temperature-based biomes
        if (temp < -0.4f) {
            if (humidity > 0.3f) return BiomeType.SNOWY_TAIGA;
            return BiomeType.SNOWY_PLAINS;
        } else if (temp < -0.2f) {
            return BiomeType.TAIGA;
        } else if (temp > 0.4f && humidity < -0.1f) {
            if (elevation > 0.2f) return BiomeType.BADLANDS;
            return BiomeType.DESERT;
        } else if (temp > 0.3f && humidity > 0.3f) {
            return BiomeType.JUNGLE;
        } else if (humidity > 0.4f) {
            return BiomeType.SWAMP;
        } else if (humidity > 0.2f) {
            return BiomeType.FOREST;
        } else if (humidity > -0.1f) {
            return BiomeType.PLAINS;
        } else {
            return BiomeType.SAVANNA;
        }
    }
    
    private int calculateHeight(int worldX, int worldZ, BiomeType biome) {
        float baseNoise = terrainNoise.GetNoise(worldX, worldZ);
        float detail = detailNoise.GetNoise(worldX, worldZ) * 0.3f;
        
        float height = baseNoise + detail;
        
        // Biome-specific height adjustments
        switch (biome) {
            case OCEAN_DEEP -> height = -0.7f + height * 0.2f;
            case OCEAN -> height = -0.4f + height * 0.3f;
            case BEACH -> height = -0.1f + height * 0.2f;
            case RIVER -> height = -0.3f + height * 0.2f;
            case MOUNTAIN -> height = 0.6f + height * 0.8f;
            case MOUNTAIN_SNOW -> height = 0.8f + height * 1.0f;
            case BADLANDS -> height = 0.3f + height * 0.6f;
            case DESERT -> height = 0.1f + height * 0.3f;
            case JUNGLE -> height = 0.2f + height * 0.4f;
            case SWAMP -> height = -0.1f + height * 0.2f;
            default -> height = height * 0.5f;
        }
        
        // Convert to block height
        int blockHeight = (int) (64 + height * 40);
        return Math.max(5, Math.min(blockHeight, 200));
    }
    
    private int getWaterLevel() {
        return 62;
    }
    
    private Block getBlockForDepth(int worldX, int y, int worldZ, int terrainHeight, BiomeType biome) {
        int depth = terrainHeight - y;
        
        // Bedrock layer
        if (y < 3) {
            return y == 0 ? Block.BEDROCK : (random.nextDouble() < 0.7 ? Block.BEDROCK : Block.STONE);
        }
        
        // Deep underground
        if (depth > 8) {
            // Caves
            if (y < 60 && y > 5 && isCave(worldX, y, worldZ)) {
                return null; // Air/cave
            }
            
            Block block = Block.STONE;
            
            // Ores
            block = getOreBlock(worldX, y, worldZ, block);
            
            // Deep slate below Y=0
            if (y < 0) {
                block = Block.DEEPSLATE;
                // Deepslate ores
                block = getDeepslateOreBlock(worldX, y, worldZ, block);
            }
            
            return block;
        }
        
        // Subsurface
        if (depth > 0) {
            return switch (biome) {
                case DESERT, BEACH -> Block.SAND;
                case BADLANDS -> Block.TERRACOTTA;
                case SNOWY_PLAINS, SNOWY_TAIGA, MOUNTAIN_SNOW -> Block.DIRT;
                case SWAMP -> Block.MUD;
                default -> Block.DIRT;
            };
        }
        
        // Surface block
        if (y <= getWaterLevel() && (biome == BiomeType.OCEAN || biome == BiomeType.OCEAN_DEEP || biome == BiomeType.RIVER)) {
            return Block.SAND;
        }
        
        return switch (biome) {
            case DESERT, BEACH -> Block.SAND;
            case BADLANDS -> Block.RED_SAND;
            case SNOWY_PLAINS, SNOWY_TAIGA, MOUNTAIN_SNOW -> Block.SNOW_BLOCK;
            case MOUNTAIN -> (y > 100 ? Block.STONE : Block.GRASS_BLOCK);
            case SWAMP -> Block.GRASS_BLOCK;
            case JUNGLE -> Block.GRASS_BLOCK;
            default -> Block.GRASS_BLOCK;
        };
    }
    
    private boolean isCave(int x, int y, int z) {
        if (y > 80 || y < 5) return false;
        
        float cave1 = caveNoise.GetNoise(x, y, z);
        float cave2 = caveNoise.GetNoise(x + 1000, y + 500, z + 2000);
        
        // Main caves
        if (cave1 > 0.65f) return true;
        
        // Ravines (tall, narrow)
        if (cave2 > 0.8f && Math.abs(cave1) < 0.3f) return true;
        
        return false;
    }
    
    private Block getOreBlock(int x, int y, int z, Block defaultBlock) {
        Random oreRandom = new Random(x * 234987L + y * 918273L + z * 555555L + seed);
        double chance = oreRandom.nextDouble();
        
        if (y < 16 && chance < 0.001) return Block.DIAMOND_ORE;
        if (y < 30 && chance < 0.003) return Block.GOLD_ORE;
        if (y < 50 && chance < 0.008) return Block.IRON_ORE;
        if (y < 60 && chance < 0.01) return Block.COAL_ORE;
        if (y < 40 && chance < 0.005) return Block.REDSTONE_ORE;
        if (y < 30 && chance < 0.002) return Block.LAPIS_ORE;
        if (y < 80 && chance < 0.008) return Block.COPPER_ORE;
        
        return defaultBlock;
    }
    
    private Block getDeepslateOreBlock(int x, int y, int z, Block defaultBlock) {
        Random oreRandom = new Random(x * 234987L + y * 918273L + z * 555555L + seed);
        double chance = oreRandom.nextDouble();
        
        if (y < -50 && chance < 0.0015) return Block.DEEPSLATE_DIAMOND_ORE;
        if (y < -20 && chance < 0.004) return Block.DEEPSLATE_GOLD_ORE;
        if (y < -10 && chance < 0.01) return Block.DEEPSLATE_IRON_ORE;
        if (y < 0 && chance < 0.012) return Block.DEEPSLATE_COAL_ORE;
        if (y < -30 && chance < 0.006) return Block.DEEPSLATE_REDSTONE_ORE;
        if (y < -20 && chance < 0.003) return Block.DEEPSLATE_LAPIS_ORE;
        if (y < -10 && chance < 0.01) return Block.DEEPSLATE_COPPER_ORE;
        
        return defaultBlock;
    }
    
    private void addSurfaceDecorations(GenerationUnit unit, int x, int y, int z, BiomeType biome, int terrainHeight, int sizeX, int sizeZ) {
        Random posRandom = new Random(x * 341873128712L + z * 132897987541L + seed);
        
        // Don't decorate underwater
        if (y <= getWaterLevel()) {
            if (biome == BiomeType.OCEAN || biome == BiomeType.OCEAN_DEEP) {
                // Seagrass
                if (posRandom.nextDouble() < 0.3) {
                    unit.modifier().setBlock(x, y, z, Block.SEAGRASS);
                }
            }
            return;
        }
        
        switch (biome) {
            case FOREST -> {
                if (posRandom.nextDouble() < 0.12 && x > 2 && x < sizeX - 2 && z > 2 && z < sizeZ - 2) {
                    generateTree(unit, x, y, z, TreeType.OAK);
                } else if (posRandom.nextDouble() < 0.6) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                } else if (posRandom.nextDouble() < 0.05) {
                    unit.modifier().setBlock(x, y, z, posRandom.nextBoolean() ? Block.POPPY : Block.DANDELION);
                }
            }
            case PLAINS -> {
                if (posRandom.nextDouble() < 0.8) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                } else if (posRandom.nextDouble() < 0.1) {
                    Block flower = switch (posRandom.nextInt(4)) {
                        case 0 -> Block.POPPY;
                        case 1 -> Block.DANDELION;
                        case 2 -> Block.AZURE_BLUET;
                        default -> Block.OXEYE_DAISY;
                    };
                    unit.modifier().setBlock(x, y, z, flower);
                }
            }
            case TAIGA -> {
                if (posRandom.nextDouble() < 0.1 && x > 2 && x < sizeX - 2 && z > 2 && z < sizeZ - 2) {
                    generateTree(unit, x, y, z, TreeType.SPRUCE);
                } else if (posRandom.nextDouble() < 0.4) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                } else if (posRandom.nextDouble() < 0.05) {
                    unit.modifier().setBlock(x, y, z, Block.SWEET_BERRY_BUSH);
                }
            }
            case SNOWY_TAIGA -> {
                if (posRandom.nextDouble() < 0.08 && x > 2 && x < sizeX - 2 && z > 2 && z < sizeZ - 2) {
                    generateTree(unit, x, y, z, TreeType.SPRUCE);
                } else if (posRandom.nextDouble() < 0.3) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
                // Snow on top
                if (y + 1 < 320) unit.modifier().setBlock(x, y + 1, z, Block.SNOW);
            }
            case SNOWY_PLAINS -> {
                unit.modifier().setBlock(x, y, z, Block.SNOW);
            }
            case DESERT -> {
                if (posRandom.nextDouble() < 0.02 && x > 1 && x < sizeX - 1 && z > 1 && z < sizeZ - 1) {
                    generateCactus(unit, x, y, z);
                } else if (posRandom.nextDouble() < 0.01) {
                    unit.modifier().setBlock(x, y, z, Block.DEAD_BUSH);
                }
            }
            case BADLANDS -> {
                if (posRandom.nextDouble() < 0.01 && x > 1 && x < sizeX - 1 && z > 1 && z < sizeZ - 1) {
                    generateCactus(unit, x, y, z);
                } else if (posRandom.nextDouble() < 0.02) {
                    unit.modifier().setBlock(x, y, z, Block.DEAD_BUSH);
                }
            }
            case JUNGLE -> {
                if (posRandom.nextDouble() < 0.15 && x > 3 && x < sizeX - 3 && z > 3 && z < sizeZ - 3) {
                    generateTree(unit, x, y, z, TreeType.JUNGLE);
                } else if (posRandom.nextDouble() < 0.7) {
                    unit.modifier().setBlock(x, y, z, Block.FERN);
                } else if (posRandom.nextDouble() < 0.05) {
                    unit.modifier().setBlock(x, y, z, Block.BAMBOO);
                }
            }
            case SWAMP -> {
                if (posRandom.nextDouble() < 0.06 && x > 2 && x < sizeX - 2 && z > 2 && z < sizeZ - 2) {
                    generateTree(unit, x, y, z, TreeType.OAK); // Swamp oaks
                } else if (posRandom.nextDouble() < 0.5) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                } else if (posRandom.nextDouble() < 0.03) {
                    unit.modifier().setBlock(x, y, z, Block.BLUE_ORCHID);
                }
                // Lily pads in water
                if (terrainHeight < getWaterLevel() && posRandom.nextDouble() < 0.1) {
                    unit.modifier().setBlock(x, getWaterLevel(), z, Block.LILY_PAD);
                }
            }
            case SAVANNA -> {
                if (posRandom.nextDouble() < 0.04 && x > 2 && x < sizeX - 2 && z > 2 && z < sizeZ - 2) {
                    generateTree(unit, x, y, z, TreeType.ACACIA);
                } else if (posRandom.nextDouble() < 0.5) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                } else if (posRandom.nextDouble() < 0.05) {
                    unit.modifier().setBlock(x, y, z, Block.TALL_GRASS);
                }
            }
            case MOUNTAIN -> {
                if (y > 100) {
                    // High altitude - stone/gravel
                    if (posRandom.nextDouble() < 0.1) {
                        unit.modifier().setBlock(x, y, z, Block.GRAVEL);
                    }
                } else {
                    if (posRandom.nextDouble() < 0.3) {
                        unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                    }
                }
            }
            case MOUNTAIN_SNOW -> {
                unit.modifier().setBlock(x, y, z, Block.SNOW);
                // Powder snow at high altitudes
                if (y > 130 && posRandom.nextDouble() < 0.3) {
                    unit.modifier().setBlock(x, y - 1, z, Block.POWDER_SNOW);
                }
            }
            case BEACH -> {
                // Sugar cane
                if (posRandom.nextDouble() < 0.02 && terrainHeight <= getWaterLevel() + 1) {
                    generateSugarCane(unit, x, y, z);
                }
            }
            case RIVER -> {
                // Nothing special on river banks
            }
            default -> {
                if (posRandom.nextDouble() < 0.4) {
                    unit.modifier().setBlock(x, y, z, Block.SHORT_GRASS);
                }
            }
        }
    }
    
    private void generateTree(GenerationUnit unit, int x, int y, int z, TreeType type) {
        switch (type) {
            case OAK -> generateOakTree(unit, x, y, z);
            case SPRUCE -> generateSpruceTree(unit, x, y, z);
            case JUNGLE -> generateJungleTree(unit, x, y, z);
            case ACACIA -> generateAcaciaTree(unit, x, y, z);
        }
    }
    
    private void generateOakTree(GenerationUnit unit, int x, int y, int z) {
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
    
    private void generateJungleTree(GenerationUnit unit, int x, int y, int z) {
        int trunkHeight = 8 + random.nextInt(6);
        
        for (int i = 0; i < trunkHeight; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.JUNGLE_LOG);
        }
        
        // Large canopy
        int leafStart = y + trunkHeight - 3;
        for (int ly = leafStart; ly < leafStart + 4; ly++) {
            int radius = ly >= leafStart + 2 ? 1 : 3;
            for (int lx = -radius; lx <= radius; lx++) {
                for (int lz = -radius; lz <= radius; lz++) {
                    if (lx == 0 && lz == 0 && ly < y + trunkHeight) continue;
                    unit.modifier().setBlock(x + lx, ly, z + lz, Block.JUNGLE_LEAVES);
                }
            }
        }
    }
    
    private void generateAcaciaTree(GenerationUnit unit, int x, int y, int z) {
        int trunkHeight = 4 + random.nextInt(3);
        
        for (int i = 0; i < trunkHeight; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.ACACIA_LOG);
        }
        
        // Flat canopy
        int leafY = y + trunkHeight;
        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = -2; lz <= 2; lz++) {
                if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
                unit.modifier().setBlock(x + lx, leafY, z + lz, Block.ACACIA_LEAVES);
                unit.modifier().setBlock(x + lx, leafY - 1, z + lz, Block.ACACIA_LEAVES);
            }
        }
    }
    
    private void generateCactus(GenerationUnit unit, int x, int y, int z) {
        int height = 1 + random.nextInt(2);
        for (int i = 0; i < height; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.CACTUS);
        }
    }
    
    private void generateSugarCane(GenerationUnit unit, int x, int y, int z) {
        int height = 2 + random.nextInt(2);
        for (int i = 0; i < height; i++) {
            unit.modifier().setBlock(x, y + i, z, Block.SUGAR_CANE);
        }
    }
    
    public int getTerrainHeightAt(int worldX, int worldZ) {
        BiomeType biome = calculateBiome(worldX, worldZ);
        return calculateHeight(worldX, worldZ, biome);
    }
    
    enum BiomeType {
        PLAINS, FOREST, DESERT, SNOWY_PLAINS, SNOWY_TAIGA, TAIGA,
        JUNGLE, SWAMP, SAVANNA, BADLANDS, MOUNTAIN, MOUNTAIN_SNOW,
        OCEAN, OCEAN_DEEP, BEACH, RIVER
    }
    
    enum TreeType {
        OAK, SPRUCE, JUNGLE, ACACIA
    }
}
