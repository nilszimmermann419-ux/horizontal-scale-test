package com.shardedmc.api.events;

import com.shardedmc.api.ShardedEvent;
import com.shardedmc.shared.Vec3d;

import java.util.UUID;

public class PlayerJoinEvent extends ShardedEvent {
    
    private final UUID playerId;
    private final String username;
    private final Vec3d spawnPosition;
    
    public PlayerJoinEvent(UUID playerId, String username, Vec3d spawnPosition) {
        super(true); // Global event
        this.playerId = playerId;
        this.username = username;
        this.spawnPosition = spawnPosition;
    }
    
    public UUID getPlayerId() { return playerId; }
    public String getUsername() { return username; }
    public Vec3d getSpawnPosition() { return spawnPosition; }
}
