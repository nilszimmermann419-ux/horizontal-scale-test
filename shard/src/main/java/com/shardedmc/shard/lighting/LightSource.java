package com.shardedmc.shard.lighting;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.Objects;

public class LightSource {
    private final int x;
    private final int y;
    private final int z;
    private final int lightLevel;
    private final long packedPos;

    public LightSource(int x, int y, int z, int lightLevel) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lightLevel = Math.clamp(lightLevel, 0, 15);
        this.packedPos = pack(x, y, z);
    }

    public LightSource(Point point, int lightLevel) {
        this(point.blockX(), point.blockY(), point.blockZ(), lightLevel);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int lightLevel() {
        return lightLevel;
    }

    public long packedPos() {
        return packedPos;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LightSource that = (LightSource) o;
        return packedPos == that.packedPos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packedPos);
    }

    @Override
    public String toString() {
        return "LightSource[" + x + "," + y + "," + z + ",level=" + lightLevel + "]";
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }

    public static int[] unpack(long packed) {
        int x = (int) (packed << 38 >> 38); // Sign extend
        int y = (int) ((packed << 25 >> 51)); // Sign extend
        int z = (int) (packed >> 38 << 38 >> 38); // Sign extend
        return new int[]{x, y, z};
    }

    public static int getLightEmission(Block block) {
        if (block == null || block == Block.AIR) return 0;
        return block.registry().lightEmission();
    }

    public static boolean isLightSource(Block block) {
        return getLightEmission(block) > 0;
    }
}
