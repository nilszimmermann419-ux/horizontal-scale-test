package com.shardedmc.api;

import com.shardedmc.shared.Vec3d;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ShardedEntity {
    
    UUID getUuid();
    String getType();
    
    CompletableFuture<Vec3d> getPosition();
    CompletableFuture<Void> setPosition(Vec3d position);
    CompletableFuture<Vec3d> getVelocity();
    CompletableFuture<Void> setVelocity(Vec3d velocity);
    
    CompletableFuture<Double> getHealth();
    CompletableFuture<Void> setHealth(double health);
    CompletableFuture<Void> remove();
    
    CompletableFuture<Void> setMetadata(String key, String value);
    CompletableFuture<Optional<String>> getMetadata(String key);
}
