package com.shardedmc.shared;

import java.util.UUID;

public record PlayerState(
    UUID uuid,
    String username,
    Vec3d position,
    Vec3d velocity,
    double health,
    byte[] inventory,
    int gameMode,
    byte[] metadata
) {
    
    public ChunkPos getChunkPos() {
        if (position == null) return null;
        return ChunkPos.fromBlockPos(position.toBlockPos());
    }
    
    public boolean isValid() {
        return uuid != null && position != null && health >= 0;
    }
    
    public PlayerState withPosition(Vec3d newPosition) {
        return new PlayerState(uuid, username, newPosition, velocity, health, inventory, gameMode, metadata);
    }
    
    public PlayerState withHealth(double newHealth) {
        return new PlayerState(uuid, username, position, velocity, newHealth, inventory, gameMode, metadata);
    }
}
