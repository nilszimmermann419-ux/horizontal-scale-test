package com.shardedmc.shard.api;

import com.shardedmc.api.ShardedInventory;
import com.shardedmc.api.ShardedPlayer;
import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shared.Vec3d;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.coordinate.Pos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ShardedPlayerImpl implements ShardedPlayer {
    
    private final net.minestom.server.entity.Player player;
    
    public ShardedPlayerImpl(net.minestom.server.entity.Player player) {
        this.player = player;
    }
    
    @Override
    public UUID getUuid() {
        return player.getUuid();
    }
    
    @Override
    public String getUsername() {
        return player.getUsername();
    }
    
    @Override
    public CompletableFuture<Vec3d> getPosition() {
        return CompletableFuture.completedFuture(new Vec3d(
                player.getPosition().x(),
                player.getPosition().y(),
                player.getPosition().z()));
    }
    
    @Override
    public CompletableFuture<Void> teleport(Vec3d position) {
        return CompletableFuture.runAsync(() -> 
                player.teleport(new Pos(position.x(), position.y(), position.z())));
    }
    
    @Override
    public CompletableFuture<Void> teleportAsync(Vec3d position) {
        return teleport(position);
    }
    
    @Override
    public CompletableFuture<Double> getHealth() {
        return CompletableFuture.completedFuture((double) player.getHealth());
    }
    
    @Override
    public CompletableFuture<Void> setHealth(double health) {
        return CompletableFuture.runAsync(() -> player.setHealth((float) health));
    }
    
    @Override
    public CompletableFuture<Double> getMaxHealth() {
        return CompletableFuture.completedFuture(20.0); // Default max health
    }
    
    @Override
    public CompletableFuture<ShardedInventory> getInventory() {
        return CompletableFuture.completedFuture(new ShardedInventoryImpl(player.getInventory()));
    }
    
    @Override
    public CompletableFuture<String> getGameMode() {
        return CompletableFuture.completedFuture(player.getGameMode().name().toLowerCase());
    }
    
    @Override
    public CompletableFuture<Void> setGameMode(String mode) {
        return CompletableFuture.runAsync(() -> {
            if (mode == null) {
                throw new RuntimeException("Game mode cannot be null");
            }
            try {
                net.minestom.server.entity.GameMode gameMode = net.minestom.server.entity.GameMode.valueOf(mode.toUpperCase());
                player.setGameMode(gameMode);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid game mode: " + mode);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(Component message) {
        return CompletableFuture.runAsync(() -> player.sendMessage(message));
    }
    
    @Override
    public CompletableFuture<Void> sendActionBar(Component message) {
        return CompletableFuture.runAsync(() -> player.sendActionBar(message));
    }
    
    @Override
    public CompletableFuture<Void> sendTitle(Title title) {
        return CompletableFuture.runAsync(() -> player.showTitle(title));
    }
    
    @Override
    public CompletableFuture<Void> playSound(String sound, float volume, float pitch) {
        return CompletableFuture.runAsync(() -> 
                player.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key(sound),
                        net.kyori.adventure.sound.Sound.Source.MASTER,
                        volume, pitch)));
    }
    
    @Override
    public CompletableFuture<Set<ChunkPos>> getViewableChunks() {
        return CompletableFuture.supplyAsync(() -> {
            Set<ChunkPos> chunks = new HashSet<>();
            // TODO: Implement based on player's view distance
            return chunks;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> isOnline() {
        return CompletableFuture.completedFuture(player.isOnline());
    }
    
    @Override
    public CompletableFuture<Void> kick(Component reason) {
        return CompletableFuture.runAsync(() -> player.kick(reason));
    }
}
