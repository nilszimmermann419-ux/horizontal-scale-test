package com.shardedmc.api;

import com.shardedmc.shared.Vec3d;
import com.shardedmc.shared.ChunkPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ShardedPlayer {
    
    UUID getUuid();
    String getUsername();
    
    // Position
    CompletableFuture<Vec3d> getPosition();
    CompletableFuture<Void> teleport(Vec3d position);

    /**
     * @deprecated Use {@link #teleport(Vec3d)} instead
     */
    @Deprecated
    default CompletableFuture<Void> teleportAsync(Vec3d position) {
        return teleport(position);
    }
    
    // Player state
    CompletableFuture<Double> getHealth();
    CompletableFuture<Void> setHealth(double health);
    CompletableFuture<Double> getMaxHealth();
    CompletableFuture<ShardedInventory> getInventory();

    /**
     * Gets the player's current game mode.
     * @return the game mode, typically "survival", "creative", "adventure", or "spectator"
     */
    CompletableFuture<String> getGameMode();

    /**
     * Sets the player's game mode.
     * @param mode the game mode, typically "survival", "creative", "adventure", or "spectator"
     */
    CompletableFuture<Void> setGameMode(String mode);
    
    // Communication
    CompletableFuture<Void> sendMessage(Component message);
    CompletableFuture<Void> sendActionBar(Component message);
    CompletableFuture<Void> sendTitle(Title title);
    CompletableFuture<Void> playSound(String sound, float volume, float pitch);
    
    // Chunk view
    CompletableFuture<Set<ChunkPos>> getViewableChunks();
    
    // Connection
    CompletableFuture<Boolean> isOnline();
    CompletableFuture<Void> kick(Component reason);
}
