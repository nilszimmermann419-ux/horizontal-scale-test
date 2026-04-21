package com.shardedmc.api;

import com.shardedmc.shared.Vec3d;
import com.shardedmc.shared.ChunkPos;
import net.kyori.adventure.text.Component;
import net.minestom.server.instance.block.Block;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ShardedWorld {
    
    // Block operations
    CompletableFuture<Void> setBlock(int x, int y, int z, Block block);
    CompletableFuture<Block> getBlock(int x, int y, int z);
    CompletableFuture<Boolean> breakBlock(int x, int y, int z);
    
    /**
     * Sets a block at the given position.
     * Note: Vec3d coordinates are truncated to integers (fractional parts discarded).
     */
    default CompletableFuture<Void> setBlock(Vec3d position, Block block) {
        return setBlock((int) position.x(), (int) position.y(), (int) position.z(), block);
    }

    /**
     * Gets the block at the given position.
     * Note: Vec3d coordinates are truncated to integers (fractional parts discarded).
     */
    default CompletableFuture<Block> getBlock(Vec3d position) {
        return getBlock((int) position.x(), (int) position.y(), (int) position.z());
    }
    
    // Entity operations
    CompletableFuture<ShardedEntity> spawnEntity(String type, Vec3d position);
    CompletableFuture<ShardedEntity> getEntity(UUID uuid);
    CompletableFuture<Void> removeEntity(UUID uuid);
    CompletableFuture<Set<ShardedEntity>> getEntitiesInChunk(ChunkPos chunk);
    
    // World properties
    CompletableFuture<Long> getTime();
    CompletableFuture<Void> setTime(long time);

    /**
     * Gets the current weather.
     * @return the weather, typically "clear", "rain", or "thunder"
     */
    CompletableFuture<String> getWeather();

    /**
     * Sets the weather.
     * @param weather the weather to set, typically "clear", "rain", or "thunder"
     */
    CompletableFuture<Void> setWeather(String weather);
    
    // Chunk operations
    CompletableFuture<Boolean> isChunkLoaded(int chunkX, int chunkZ);
    CompletableFuture<Void> loadChunk(int chunkX, int chunkZ);
    CompletableFuture<Void> unloadChunk(int chunkX, int chunkZ);
    
    // Broadcasting
    void broadcastMessage(Component message);
    void broadcastEvent(ShardedEvent event);
    void playSound(String sound, Vec3d position, float volume, float pitch);
    
    // Event registration
    void registerEventHandler(ShardedEventHandler<?> handler);
    void unregisterEventHandler(ShardedEventHandler<?> handler);
    
    // Players
    CompletableFuture<Set<ShardedPlayer>> getOnlinePlayers();
    CompletableFuture<ShardedPlayer> getPlayer(UUID uuid);
    CompletableFuture<ShardedPlayer> getPlayer(String username);
}
