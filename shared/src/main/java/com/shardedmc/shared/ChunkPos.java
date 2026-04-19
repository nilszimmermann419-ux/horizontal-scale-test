package com.shardedmc.shared;

public record ChunkPos(int x, int z) {
    
    public static ChunkPos fromBlockPos(int blockX, int blockZ) {
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }
    
    public static ChunkPos fromBlockPos(Vec3i pos) {
        return fromBlockPos(pos.x(), pos.z());
    }
    
    public int getBlockX() {
        return x << 4;
    }
    
    public int getBlockZ() {
        return z << 4;
    }
    
    public double getCenterX() {
        return (x << 4) + 8.0;
    }
    
    public double getCenterZ() {
        return (z << 4) + 8.0;
    }
    
    public ChunkPos add(int dx, int dz) {
        return new ChunkPos(x + dx, z + dz);
    }
    
    public int distanceSquared(ChunkPos other) {
        int dx = x - other.x;
        int dz = z - other.z;
        return dx * dx + dz * dz;
    }
    
    @Override
    public String toString() {
        return String.format("ChunkPos{x=%d, z=%d}", x, z);
    }
}
