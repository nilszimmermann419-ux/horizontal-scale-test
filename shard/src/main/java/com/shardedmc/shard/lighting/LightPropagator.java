package com.shardedmc.shard.lighting;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;

import java.util.*;

public class LightPropagator {
    private static final int LIGHT_RADIUS = 15;
    private static final int MAX_QUEUE_SIZE = 100000;

    private final Instance instance;
    private final LightingEngine engine;

    public LightPropagator(Instance instance, LightingEngine engine) {
        this.instance = instance;
        this.engine = engine;
    }

    public void propagateFrom(LightSource source, Chunk targetChunk) {
        if (source.lightLevel() <= 0) return;

        Queue<LightNode> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        queue.offer(new LightNode(source.x(), source.y(), source.z(), source.lightLevel()));
        visited.add(LightSource.pack(source.x(), source.y(), source.z()));

        while (!queue.isEmpty() && queue.size() < MAX_QUEUE_SIZE) {
            LightNode current = queue.poll();

            if (current.level <= 0) continue;

            processNode(current, queue, visited, source.x(), source.y(), source.z());
        }
    }

    private void processNode(LightNode current, Queue<LightNode> queue, Set<Long> visited,
                            int sourceX, int sourceY, int sourceZ) {
        int chunkX = current.x >> 4;
        int chunkZ = current.z >> 4;

        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;

        int sectionY = current.y >> 4;
        Section section = chunk.getSection(sectionY);
        if (section == null) return;

        int localX = current.x & 15;
        int localY = current.y & 15;
        int localZ = current.z & 15;

        int index = getLightIndex(localX, localY, localZ);
        byte[] blockLight = copyLightArray(section.blockLight().array());

        int existingLevel = getLightLevel(blockLight, index);
        if (current.level <= existingLevel) return;

        setLightLevel(blockLight, index, current.level);
        section.setBlockLight(blockLight);

        for (int[] dir : DIRECTIONS) {
            int nx = current.x + dir[0];
            int ny = current.y + dir[1];
            int nz = current.z + dir[2];

            long packed = LightSource.pack(nx, ny, nz);
            if (visited.contains(packed)) continue;

            Block neighborBlock = getBlockAt(nx, ny, nz);
            if (neighborBlock == null) continue;

            if (isOpaque(neighborBlock)) continue;

            int attenuation = getLightAttenuation(neighborBlock);
            int newLevel = current.level - attenuation;

            if (newLevel > 0 && isWithinRadius(sourceX, sourceY, sourceZ, nx, ny, nz)) {
                visited.add(packed);
                queue.offer(new LightNode(nx, ny, nz, newLevel));
            }
        }
    }

    private static final int[][] DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    public void propagateFromWithTracking(LightSource source, Chunk targetChunk, 
                                          Map<Long, Byte> lightUpdates) {
        if (source.lightLevel() <= 0) return;

        Queue<LightNode> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        queue.offer(new LightNode(source.x(), source.y(), source.z(), source.lightLevel()));
        visited.add(LightSource.pack(source.x(), source.y(), source.z()));

        while (!queue.isEmpty() && queue.size() < MAX_QUEUE_SIZE) {
            LightNode current = queue.poll();

            if (current.level <= 0) continue;

            processNodeWithTracking(current, queue, visited, lightUpdates, 
                source.x(), source.y(), source.z());
        }
    }

    private void processNodeWithTracking(LightNode current, Queue<LightNode> queue, 
                                         Set<Long> visited, Map<Long, Byte> lightUpdates,
                                         int sourceX, int sourceY, int sourceZ) {
        int chunkX = current.x >> 4;
        int chunkZ = current.z >> 4;

        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;

        int sectionY = current.y >> 4;
        Section section = chunk.getSection(sectionY);
        if (section == null) return;

        int localX = current.x & 15;
        int localY = current.y & 15;
        int localZ = current.z & 15;

        int index = getLightIndex(localX, localY, localZ);
        byte[] blockLight = copyLightArray(section.blockLight().array());
        
        int existingLevel = getLightLevel(blockLight, index);
        if (current.level <= existingLevel) return;

        setLightLevel(blockLight, index, current.level);
        section.setBlockLight(blockLight);

        long packed = LightSource.pack(current.x, current.y, current.z);
        lightUpdates.put(packed, (byte) current.level);

        for (int[] dir : DIRECTIONS) {
            int nx = current.x + dir[0];
            int ny = current.y + dir[1];
            int nz = current.z + dir[2];

            long nPacked = LightSource.pack(nx, ny, nz);
            if (visited.contains(nPacked)) continue;

            Block neighborBlock = getBlockAt(nx, ny, nz);
            if (neighborBlock == null) continue;

            if (isOpaque(neighborBlock)) continue;

            int attenuation = getLightAttenuation(neighborBlock);
            int newLevel = current.level - attenuation;

            if (newLevel > 0 && isWithinRadius(sourceX, sourceY, sourceZ, nx, ny, nz)) {
                visited.add(nPacked);
                queue.offer(new LightNode(nx, ny, nz, newLevel));
            }
        }
    }

    private boolean isWithinRadius(int sx, int sy, int sz, int x, int y, int z) {
        int dx = Math.abs(x - sx);
        int dy = Math.abs(y - sy);
        int dz = Math.abs(z - sz);
        return dx <= LIGHT_RADIUS && dy <= LIGHT_RADIUS && dz <= LIGHT_RADIUS;
    }

    public Block getBlockAt(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return null;
        
        int localX = x & 15;
        int localZ = z & 15;
        return chunk.getBlock(localX, y, localZ);
    }

    public static boolean isOpaque(Block block) {
        if (block == null || block == Block.AIR) return false;
        return block.registry().lightBlocked() >= 15;
    }

    public static boolean isTransparent(Block block) {
        return !isOpaque(block);
    }

    public static int getLightAttenuation(Block block) {
        if (block == null || block == Block.AIR) return 1;
        int blocked = block.registry().lightBlocked();
        if (blocked >= 15) return 15;
        return 1 + (blocked > 0 ? 1 : 0);
    }

    public static int getLightIndex(int x, int y, int z) {
        return x + (z * 16) + (y * 256);
    }

    public static int getLightLevel(byte[] array, int index) {
        int byteIndex = index >> 1;
        int nibble = index & 1;
        if (nibble == 0) {
            return array[byteIndex] & 0x0F;
        } else {
            return (array[byteIndex] >> 4) & 0x0F;
        }
    }

    public static void setLightLevel(byte[] array, int index, int level) {
        int byteIndex = index >> 1;
        int nibble = index & 1;
        if (nibble == 0) {
            array[byteIndex] = (byte) ((array[byteIndex] & 0xF0) | (level & 0x0F));
        } else {
            array[byteIndex] = (byte) ((array[byteIndex] & 0x0F) | ((level & 0x0F) << 4));
        }
    }

    public static byte[] copyLightArray(byte[] source) {
        if (source == null) {
            return new byte[2048];
        }
        return Arrays.copyOf(source, source.length);
    }

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
}
