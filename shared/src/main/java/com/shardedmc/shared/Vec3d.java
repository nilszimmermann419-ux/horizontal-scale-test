package com.shardedmc.shared;

public record Vec3d(double x, double y, double z) {
    
    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }
    
    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }
    
    public double distanceSquared(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
    
    public double distance(Vec3d other) {
        return Math.sqrt(distanceSquared(other));
    }
    
    public Vec3i toBlockPos() {
        return new Vec3i((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
    
    @Override
    public String toString() {
        return String.format("Vec3d{x=%.3f, y=%.3f, z=%.3f}", x, y, z);
    }
}
