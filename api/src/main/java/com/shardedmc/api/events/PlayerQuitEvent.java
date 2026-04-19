package com.shardedmc.api.events;

import com.shardedmc.api.ShardedEvent;

import java.util.UUID;

public class PlayerQuitEvent extends ShardedEvent {
    
    private final UUID playerId;
    private final String reason;
    
    public PlayerQuitEvent(UUID playerId, String reason) {
        super(true);
        this.playerId = playerId;
        this.reason = reason;
    }
    
    public UUID getPlayerId() { return playerId; }
    public String getReason() { return reason; }
}
