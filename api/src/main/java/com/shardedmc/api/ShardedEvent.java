package com.shardedmc.api;

import java.util.UUID;

public abstract class ShardedEvent {
    
    private final UUID eventId;
    private final long timestamp;
    private final boolean global;
    
    public ShardedEvent() {
        this(false);
    }
    
    public ShardedEvent(boolean global) {
        this.eventId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.global = global;
    }
    
    public UUID getEventId() {
        return eventId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean isGlobal() {
        return global;
    }
}
