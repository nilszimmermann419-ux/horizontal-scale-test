package com.shardedmc.api.events;

import com.shardedmc.api.ShardedEvent;
import com.shardedmc.shared.Vec3d;
import net.minestom.server.instance.block.Block;

import java.util.UUID;

public class BlockBreakEvent extends ShardedEvent {
    
    private final UUID playerId;
    private final Vec3d position;
    private final Block block;
    private boolean cancelled;
    
    public BlockBreakEvent(UUID playerId, Vec3d position, Block block) {
        super(false); // Local event
        this.playerId = playerId;
        this.position = position;
        this.block = block;
        this.cancelled = false;
    }
    
    public UUID getPlayerId() { return playerId; }
    public Vec3d getPosition() { return position; }
    public Block getBlock() { return block; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
