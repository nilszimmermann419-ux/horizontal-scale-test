package com.shardedmc.shared;

import java.util.UUID;

public record EntityState(
    UUID uuid,
    String type,
    Vec3d position,
    Vec3d velocity,
    double health,
    byte[] metadata
) {
    
    public ChunkPos getChunkPos() {
        if (position == null) return null;
        return ChunkPos.fromBlockPos(position.toBlockPos());
    }
    
    public boolean isValid() {
        return uuid != null && type != null && position != null;
    }
}
