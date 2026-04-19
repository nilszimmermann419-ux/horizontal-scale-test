package com.shardedmc.shard.api;

import com.shardedmc.api.ShardedEntity;
import com.shardedmc.api.ShardedEvent;
import com.shardedmc.api.ShardedEventHandler;
import com.shardedmc.api.ShardedPlayer;
import com.shardedmc.api.ShardedWorld;
import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shared.Vec3d;
import com.shardedmc.shard.ShardCoordinatorClient;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShardedWorldImpl implements ShardedWorld {
    private static final Logger logger = LoggerFactory.getLogger(ShardedWorldImpl.class);
    
    private final Instance instance;
    private final ShardCoordinatorClient coordinatorClient;
    private final String shardId;
    private final List<ShardedEventHandler<?>> eventHandlers = new CopyOnWriteArrayList<>();
    
    public ShardedWorldImpl(Instance instance, ShardCoordinatorClient coordinatorClient, String shardId) {
        this.instance = instance;
        this.coordinatorClient = coordinatorClient;
        this.shardId = shardId;
    }
    
    @Override
    public CompletableFuture<Void> setBlock(int x, int y, int z, Block block) {
        return CompletableFuture.runAsync(() -> {
            instance.setBlock(x, y, z, block);
            
            // Notify neighboring shards if near boundary
            ChunkPos chunk = ChunkPos.fromBlockPos(x, z);
            // TODO: Check if near boundary and broadcast update
        });
    }
    
    @Override
    public CompletableFuture<Block> getBlock(int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> instance.getBlock(x, y, z));
    }
    
    @Override
    public CompletableFuture<Boolean> breakBlock(int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            Block current = instance.getBlock(x, y, z);
            if (!current.isAir()) {
                instance.setBlock(x, y, z, Block.AIR);
                return true;
            }
            return false;
        });
    }
    
    @Override
    public CompletableFuture<ShardedEntity> spawnEntity(String type, Vec3d position) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EntityType entityType = EntityType.fromKey(type);
                Entity entity = new Entity(entityType);
                entity.setInstance(instance, new net.minestom.server.coordinate.Pos(
                        position.x(), position.y(), position.z()));
                
                return new ShardedEntityImpl(entity);
            } catch (Exception e) {
                logger.error("Failed to spawn entity: {}", type, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<ShardedEntity> getEntity(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Entity entity = instance.getEntityByUuid(uuid);
            if (entity != null) {
                return new ShardedEntityImpl(entity);
            }
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Void> removeEntity(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            Entity entity = instance.getEntityByUuid(uuid);
            if (entity != null) {
                entity.remove();
            }
        });
    }
    
    @Override
    public CompletableFuture<Set<ShardedEntity>> getEntitiesInChunk(ChunkPos chunk) {
        return CompletableFuture.supplyAsync(() -> {
            Set<ShardedEntity> entities = new HashSet<>();
            // TODO: Implement entity lookup by chunk
            return entities;
        });
    }
    
    @Override
    public CompletableFuture<Long> getTime() {
        return CompletableFuture.completedFuture(instance.getWorldAge());
    }
    
    @Override
    public CompletableFuture<Void> setTime(long time) {
        return CompletableFuture.runAsync(() -> instance.setWorldAge(time));
    }
    
    @Override
    public CompletableFuture<String> getWeather() {
        return CompletableFuture.completedFuture("clear"); // Simplified
    }
    
    @Override
    public CompletableFuture<Void> setWeather(String weather) {
        return CompletableFuture.runAsync(() -> {
            // TODO: Implement weather changes
            logger.info("Weather change requested: {}", weather);
        });
    }
    
    @Override
    public CompletableFuture<Boolean> isChunkLoaded(int chunkX, int chunkZ) {
        return CompletableFuture.completedFuture(instance.isChunkLoaded(chunkX, chunkZ));
    }
    
    @Override
    public CompletableFuture<Void> loadChunk(int chunkX, int chunkZ) {
        return CompletableFuture.runAsync(() -> instance.loadChunk(chunkX, chunkZ));
    }
    
    @Override
    public CompletableFuture<Void> unloadChunk(int chunkX, int chunkZ) {
        return CompletableFuture.runAsync(() -> instance.unloadChunk(chunkX, chunkZ));
    }
    
    @Override
    public void broadcastMessage(Component message) {
        instance.getPlayers().forEach(player -> player.sendMessage(message));
    }
    
    @Override
    public void broadcastEvent(ShardedEvent event) {
        for (ShardedEventHandler<?> handler : eventHandlers) {
            if (handler.getEventType().isInstance(event)) {
                @SuppressWarnings("unchecked")
                ShardedEventHandler<ShardedEvent> typedHandler = (ShardedEventHandler<ShardedEvent>) handler;
                typedHandler.handle(event);
            }
        }
    }
    
    @Override
    public void playSound(String sound, Vec3d position, float volume, float pitch) {
        instance.getPlayers().forEach(player -> 
                player.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key(sound),
                        net.kyori.adventure.sound.Sound.Source.MASTER,
                        volume, pitch)));
    }
    
    @Override
    public void registerEventHandler(ShardedEventHandler<?> handler) {
        eventHandlers.add(handler);
    }
    
    @Override
    public void unregisterEventHandler(ShardedEventHandler<?> handler) {
        eventHandlers.remove(handler);
    }
    
    @Override
    public CompletableFuture<Set<ShardedPlayer>> getOnlinePlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Set<ShardedPlayer> players = new HashSet<>();
            instance.getPlayers().forEach(player -> players.add(new ShardedPlayerImpl(player)));
            return players;
        });
    }
    
    @Override
    public CompletableFuture<ShardedPlayer> getPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            net.minestom.server.entity.Player player = instance.getPlayerByUuid(uuid);
            if (player != null) {
                return new ShardedPlayerImpl(player);
            }
            return null;
        });
    }
    
    @Override
    public CompletableFuture<ShardedPlayer> getPlayer(String username) {
        return CompletableFuture.supplyAsync(() -> {
            net.minestom.server.entity.Player player = instance.getPlayers().stream()
                    .filter(p -> p.getUsername().equalsIgnoreCase(username))
                    .findFirst()
                    .orElse(null);
            if (player != null) {
                return new ShardedPlayerImpl(player);
            }
            return null;
        });
    }
}
