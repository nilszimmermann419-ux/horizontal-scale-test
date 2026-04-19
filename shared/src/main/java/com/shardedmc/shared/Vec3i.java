package com.shardedmc.shared;

public record Vec3i(int x, int y, int z) {
    
    @Override
    public String toString() {
        return String.format("Vec3i{x=%d, y=%d, z=%d}", x, y, z);
    }
}
