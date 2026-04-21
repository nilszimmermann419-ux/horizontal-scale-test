package com.shardedmc.shard.lighting;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LightingEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(LightingEngine.class);
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final int LIGHT_RADIUS = 15;
    private static final int MAX_Y = 320;
    private static final int MIN_Y = -64;
    private static final int MAX_SECTION = MAX_Y >> 4;
    private static final int MIN_SECTION = MIN_Y >> 4;
    private static final int TOTAL_SECTIONS = MAX_SECTION - MIN_SECTION;

    private final Instance instance;
    private final ExecutorService deepPassExecutor;
    private final LightPropagator propagator;
    private final Map<Long, LightSource> lightSources = new ConcurrentHashMap<>();
    private final Set<Long> pendingDeepPasses = ConcurrentHashMap.newKeySet();
    private final Map<Long, CompletableFuture<Void>> activeDeepPasses = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public LightingEngine(Instance instance) {
        this.instance = instance;
        this.deepPassExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "lighting-deep-pass");
                t.setDaemon(true);
                return t;
            }
        );
        this.propagator = new LightPropagator(instance, this);
    }

    public void initializeChunk(int chunkX, int chunkZ) {
        if (shutdown.get()) return;

        long chunkKey = packChunkCoord(chunkX, chunkZ);

        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            LOGGER.warn("Cannot initialize lighting for null chunk {}, {}", chunkX, chunkZ);
            return;
        }

        long startTime = System.nanoTime();

        calculateSkyLightQuick(chunkX, chunkZ);

        long quickPassTime = System.nanoTime() - startTime;
        LOGGER.debug("Quick sky light pass for chunk {}, {} took {}µs", 
            chunkX, chunkZ, quickPassTime / 1000);

        scheduleDeepPass(chunkX, chunkZ);
    }

    public void updateBlockLight(int x, int y, int z, Block oldBlock, Block newBlock) {
        if (shutdown.get()) return;

        int oldEmission = LightSource.getLightEmission(oldBlock);
        int newEmission = LightSource.getLightEmission(newBlock);

        long pos = LightSource.pack(x, y, z);

        if (oldEmission > 0 && newEmission <= 0) {
            lightSources.remove(pos);
        } else if (newEmission > 0) {
            lightSources.put(pos, new LightSource(x, y, z, newEmission));
        }

        if (oldEmission != newEmission || isTransparencyChanged(oldBlock, newBlock)) {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            scheduleDeepPass(chunkX, chunkZ);

            scheduleDeepPass(chunkX + 1, chunkZ);
            scheduleDeepPass(chunkX - 1, chunkZ);
            scheduleDeepPass(chunkX, chunkZ + 1);
            scheduleDeepPass(chunkX, chunkZ - 1);
        }
    }

    public void calculateSkyLightQuick(int chunkX, int chunkZ) {
        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;

        int[] heightmap = computeHeightmap(chunk);

        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            Section section = chunk.getSection(sectionY);
            if (section == null) continue;

            byte[] skyLight = new byte[2048];

            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    int worldYBase = sectionY << 4;
                    int height = heightmap[x + z * CHUNK_SIZE];

                    for (int y = 0; y < SECTION_SIZE; y++) {
                        int worldY = worldYBase + y;
                        int index = x + (z * CHUNK_SIZE) + (y * CHUNK_SIZE * CHUNK_SIZE);

                        if (worldY > height) {
                            LightPropagator.setLightLevel(skyLight, index, 15);
                        } else {
                            LightPropagator.setLightLevel(skyLight, index, 0);
                        }
                    }
                }
            }

            section.setSkyLight(skyLight);
        }
    }

    private int[] computeHeightmap(Chunk chunk) {
        int[] heightmap = new int[CHUNK_SIZE * CHUNK_SIZE];
        Arrays.fill(heightmap, MIN_Y - 1);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int y = MAX_Y - 1; y >= MIN_Y; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block != null && block != Block.AIR && 
                        block.registry().lightBlocked() >= 15) {
                        heightmap[x + z * CHUNK_SIZE] = y;
                        break;
                    }
                }
            }
        }

        return heightmap;
    }

    public void calculateBlockLightDeep(int chunkX, int chunkZ) {
        if (shutdown.get()) return;

        long chunkKey = packChunkCoord(chunkX, chunkZ);
        pendingDeepPasses.remove(chunkKey);

        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;

        long startTime = System.nanoTime();

        clearBlockLight(chunk);

        Map<Long, Byte> lightUpdates = new HashMap<>();
        
        List<LightSource> nearbySources = findSourcesNearChunk(chunkX, chunkZ);
        
        for (LightSource source : nearbySources) {
            propagator.propagateFromWithTracking(source, chunk, lightUpdates);
        }

        long deepPassTime = System.nanoTime() - startTime;
        LOGGER.debug("Deep block light pass for chunk {}, {} took {}ms ({} sources, {} updates)",
            chunkX, chunkZ, deepPassTime / 1_000_000.0, nearbySources.size(), lightUpdates.size());

        if (!lightUpdates.isEmpty() && chunk instanceof LightingChunk lightingChunk) {
            net.minestom.server.MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                if (!shutdown.get()) {
                    lightingChunk.sendLighting();
                }
            });
        }
    }

    private List<LightSource> findSourcesNearChunk(int chunkX, int chunkZ) {
        List<LightSource> nearby = new ArrayList<>();
        
        for (LightSource source : lightSources.values()) {
            int dx = Math.abs(source.chunkX() - chunkX);
            int dz = Math.abs(source.chunkZ() - chunkZ);
            if (dx <= 1 && dz <= 1) {
                nearby.add(source);
            }
        }

        return nearby;
    }

    private void clearBlockLight(Chunk chunk) {
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            Section section = chunk.getSection(sectionY);
            if (section != null) {
                section.setBlockLight(new byte[2048]);
            }
        }
    }

    private void scheduleDeepPass(int chunkX, int chunkZ) {
        if (shutdown.get()) return;

        long chunkKey = packChunkCoord(chunkX, chunkZ);

        if (!pendingDeepPasses.add(chunkKey)) return;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                calculateBlockLightDeep(chunkX, chunkZ);
            } catch (Exception e) {
                LOGGER.error("Deep block light pass failed for chunk {}, {}", chunkX, chunkZ, e);
            }
        }, deepPassExecutor);

        activeDeepPasses.put(chunkKey, future);
        
        future.whenComplete((result, ex) -> {
            activeDeepPasses.remove(chunkKey);
            pendingDeepPasses.remove(chunkKey);
        });
    }

    private boolean isTransparencyChanged(Block oldBlock, Block newBlock) {
        boolean oldTransparent = LightPropagator.isTransparent(oldBlock);
        boolean newTransparent = LightPropagator.isTransparent(newBlock);
        return oldTransparent != newTransparent;
    }

    public void registerLightSource(int x, int y, int z, int lightLevel) {
        long pos = LightSource.pack(x, y, z);
        lightSources.put(pos, new LightSource(x, y, z, lightLevel));
        
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        scheduleDeepPass(chunkX, chunkZ);
    }

    public void unregisterLightSource(int x, int y, int z) {
        long pos = LightSource.pack(x, y, z);
        if (lightSources.remove(pos) != null) {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            scheduleDeepPass(chunkX, chunkZ);
        }
    }

    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            LOGGER.info("Shutting down lighting engine");
            
            for (CompletableFuture<Void> future : activeDeepPasses.values()) {
                future.cancel(false);
            }
            activeDeepPasses.clear();
            pendingDeepPasses.clear();
            
            deepPassExecutor.shutdown();
            try {
                if (!deepPassExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    deepPassExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                deepPassExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            LOGGER.info("Lighting engine shutdown complete");
        }
    }

    public int getActiveDeepPassCount() {
        return activeDeepPasses.size();
    }

    public int getPendingDeepPassCount() {
        return pendingDeepPasses.size();
    }

    public int getLightSourceCount() {
        return lightSources.size();
    }

    private static long packChunkCoord(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
