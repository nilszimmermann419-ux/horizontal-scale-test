package com.shardedmc.shard.api;

import com.shardedmc.api.ShardedEntity;
import com.shardedmc.shared.Vec3d;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ShardedEntityImpl implements ShardedEntity {
    
    private final Entity entity;
    
    public ShardedEntityImpl(Entity entity) {
        this.entity = entity;
    }
    
    @Override
    public UUID getUuid() {
        return entity.getUuid();
    }
    
    @Override
    public String getType() {
        return entity.getEntityType().name();
    }
    
    @Override
    public CompletableFuture<Vec3d> getPosition() {
        return CompletableFuture.completedFuture(new Vec3d(
                entity.getPosition().x(),
                entity.getPosition().y(),
                entity.getPosition().z()));
    }
    
    @Override
    public CompletableFuture<Void> setPosition(Vec3d position) {
        return CompletableFuture.runAsync(() -> 
                entity.teleport(new Pos(position.x(), position.y(), position.z())));
    }
    
    @Override
    public CompletableFuture<Vec3d> getVelocity() {
        return CompletableFuture.completedFuture(new Vec3d(
                entity.getVelocity().x(),
                entity.getVelocity().y(),
                entity.getVelocity().z()));
    }
    
    @Override
    public CompletableFuture<Void> setVelocity(Vec3d velocity) {
        return CompletableFuture.runAsync(() -> entity.setVelocity(
                new net.minestom.server.coordinate.Vec(velocity.x(), velocity.y(), velocity.z())));
    }
    
    @Override
    public CompletableFuture<Double> getHealth() {
        return CompletableFuture.completedFuture(20.0); // Simplified
    }
    
    @Override
    public CompletableFuture<Void> setHealth(double health) {
        return CompletableFuture.completedFuture(null); // Simplified
    }
    
    @Override
    public CompletableFuture<Void> remove() {
        return CompletableFuture.runAsync(() -> entity.remove());
    }
    
    @Override
    public CompletableFuture<Void> setMetadata(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            // TODO: Implement metadata storage
        });
    }
    
    @Override
    public CompletableFuture<Optional<String>> getMetadata(String key) {
        return CompletableFuture.completedFuture(Optional.empty()); // Simplified
    }
}
