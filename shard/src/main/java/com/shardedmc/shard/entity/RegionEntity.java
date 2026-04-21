package com.shardedmc.shard.entity;

import net.minestom.server.entity.Entity;

import java.util.UUID;

public record RegionEntity(
        Entity entity,
        String regionId,
        String ownerShard,
        long lastSyncTime,
        boolean dirty
) {

    public RegionEntity(Entity entity, String regionId, String ownerShard) {
        this(entity, regionId, ownerShard, System.currentTimeMillis(), false);
    }

    public RegionEntity withRegion(String newRegionId, String newOwnerShard) {
        return new RegionEntity(entity, newRegionId, newOwnerShard, lastSyncTime, true);
    }

    public RegionEntity markDirty() {
        return new RegionEntity(entity, regionId, ownerShard, lastSyncTime, true);
    }

    public RegionEntity markSynced() {
        return new RegionEntity(entity, regionId, ownerShard, System.currentTimeMillis(), false);
    }

    public boolean shouldSync() {
        return dirty || System.currentTimeMillis() - lastSyncTime > 50; // Sync every 50ms if dirty, or every tick
    }

    public UUID uuid() {
        return entity.getUuid();
    }
}
