package com.shardedmc.api.events;

import com.shardedmc.api.ShardedEvent;

import java.util.UUID;

public class EntityDamageEvent extends ShardedEvent {
    
    private final UUID entityId;
    private final double damage;
    private final String source;
    private boolean cancelled;
    
    public EntityDamageEvent(UUID entityId, double damage, String source) {
        super(false);
        this.entityId = entityId;
        this.damage = damage;
        this.source = source;
        this.cancelled = false;
    }
    
    public UUID getEntityId() { return entityId; }
    public double getDamage() { return damage; }
    public String getSource() { return source; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
