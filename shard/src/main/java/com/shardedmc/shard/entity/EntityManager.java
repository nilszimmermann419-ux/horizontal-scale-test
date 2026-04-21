package com.shardedmc.shard.entity;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shardedmc.v2.EventsProto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class EntityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityManager.class);

    private final String shardId;
    private final Connection natsConnection;
    private final Map<UUID, RegionEntity> entities = new ConcurrentHashMap<>();
    private final Set<UUID> ownedEntities = ConcurrentHashMap.newKeySet();
    private final ExecutorService entityExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "entity-manager");
        t.setDaemon(true);
        return t;
    });
    private Dispatcher dispatcher;
    private volatile boolean running = false;

    // NATS subjects
    private static final String WORLD_ENTITIES_PREFIX = "world.entities.";

    public EntityManager(String shardId, Connection natsConnection) {
        this.shardId = shardId;
        this.natsConnection = natsConnection;
    }

    public void start() {
        LOGGER.info("Starting entity manager");
        this.running = true;

        // Subscribe to entity events
        dispatcher = natsConnection.createDispatcher(this::handleEntityMessage);
        dispatcher.subscribe(WORLD_ENTITIES_PREFIX + ">");
    }

    public RegionEntity spawnEntity(EntityType entityType, Pos position, String regionId) {
        Instance instance = MinecraftServer.getInstanceManager().getInstances().iterator().next();
        Entity entity = new Entity(entityType);
        entity.setInstance(instance, position);

        RegionEntity regionEntity = new RegionEntity(entity, regionId, shardId);
        entities.put(entity.getUuid(), regionEntity);
        ownedEntities.add(entity.getUuid());

        LOGGER.info("Spawned entity {} of type {} in region {}", entity.getUuid(), entityType, regionId);

        // Publish spawn event
        publishEntitySpawn(regionEntity);

        return regionEntity;
    }

    public void removeEntity(UUID entityUuid) {
        RegionEntity regionEntity = entities.remove(entityUuid);
        if (regionEntity != null) {
            ownedEntities.remove(entityUuid);
            regionEntity.entity().remove();

            LOGGER.info("Removed entity {} from region {}", entityUuid, regionEntity.regionId());

            // Publish removal event
            publishEntityRemove(regionEntity);
        }
    }

    public void transferEntity(UUID entityUuid, String targetRegionId, String targetShardId) {
        RegionEntity regionEntity = entities.get(entityUuid);
        if (regionEntity == null) {
            LOGGER.warn("Cannot transfer unknown entity {}", entityUuid);
            return;
        }

        LOGGER.info("Transferring entity {} from region {} to region {} on shard {}",
                entityUuid, regionEntity.regionId(), targetRegionId, targetShardId);

        // Update entity's region info
        RegionEntity updatedEntity = regionEntity.withRegion(targetRegionId, targetShardId);
        entities.put(entityUuid, updatedEntity);

        // If transferring to another shard, remove from ownership
        if (!shardId.equals(targetShardId)) {
            ownedEntities.remove(entityUuid);
        } else {
            ownedEntities.add(entityUuid);
        }

        // Publish transfer event
        publishEntityTransfer(updatedEntity, targetRegionId, targetShardId);
    }

    public void tickAll() {
        // Only tick entities we own
        for (UUID entityUuid : ownedEntities) {
            RegionEntity regionEntity = entities.get(entityUuid);
            if (regionEntity != null) {
                tickEntity(regionEntity);
            }
        }
    }

    private void tickEntity(RegionEntity regionEntity) {
        Entity entity = regionEntity.entity();
        Point pos = entity.getPosition();

        // Check if entity crossed region boundary
        String currentRegion = calculateRegionId(pos);
        if (!currentRegion.equals(regionEntity.regionId())) {
            LOGGER.debug("Entity {} crossed from region {} to region {}",
                    entity.getUuid(), regionEntity.regionId(), currentRegion);

            // Determine target shard for new region (simplified - would query coordinator)
            String targetShard = determineShardForRegion(currentRegion);
            transferEntity(entity.getUuid(), currentRegion, targetShard);
        }

        // Publish position update for owned entities periodically
        if (regionEntity.shouldSync()) {
            publishEntityMove(regionEntity);
            regionEntity.markSynced();
        }
    }

    private void handleEntityMessage(Message msg) {
        if (!running) return;

        entityExecutor.submit(() -> {
            try {
                String subject = msg.getSubject();
                byte[] data = msg.getData();

                if (subject.startsWith(WORLD_ENTITIES_PREFIX)) {
                    handleRemoteEntityEvent(data);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling entity message", e);
            }
        });
    }

    private void handleRemoteEntityEvent(byte[] data) {
        try {
            EventsProto.WorldEvent worldEvent = EventsProto.WorldEvent.parseFrom(data);

            if (worldEvent.hasEntitySpawn()) {
                handleRemoteEntitySpawn(worldEvent.getEntitySpawn());
            } else if (worldEvent.hasEntityMove()) {
                handleRemoteEntityMove(worldEvent.getEntityMove());
            }
        } catch (Exception e) {
            LOGGER.error("Error handling remote entity event", e);
        }
    }

    private void handleRemoteEntitySpawn(EventsProto.EntitySpawnEvent event) {
        UUID entityUuid = UUID.fromString(event.getUuid());

        // Don't process our own events
        if (entities.containsKey(entityUuid)) {
            return;
        }

        // Create a remote entity representation (ghost entity for cross-shard visibility)
        LOGGER.debug("Received remote entity spawn: {} at {},{},{}",
                event.getUuid(), event.getX(), event.getY(), event.getZ());

        // In a full implementation, this would create a visual representation
        // of the entity from another shard for players near the border
    }

    private void handleRemoteEntityMove(EventsProto.EntityMoveEvent event) {
        UUID entityUuid = UUID.fromString(event.getUuid());

        // If we own this entity, ignore (we're the source of truth)
        if (ownedEntities.contains(entityUuid)) {
            return;
        }

        LOGGER.debug("Received remote entity move: {} to {},{},{}",
                event.getUuid(), event.getX(), event.getY(), event.getZ());

        // Update ghost entity position if visible
    }

    private void publishEntitySpawn(RegionEntity regionEntity) {
        Pos pos = regionEntity.entity().getPosition();
        EventsProto.EntitySpawnEvent spawnEvent = EventsProto.EntitySpawnEvent.newBuilder()
                .setUuid(regionEntity.entity().getUuid().toString())
                .setType(regionEntity.entity().getEntityType().toString())
                .setX(pos.x())
                .setY(pos.y())
                .setZ(pos.z())
                .build();

        EventsProto.WorldEvent worldEvent = EventsProto.WorldEvent.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setShardId(shardId)
                .setEntitySpawn(spawnEvent)
                .build();

        String subject = WORLD_ENTITIES_PREFIX + regionEntity.regionId();
        publishEvent(subject, worldEvent.toByteArray());
    }

    private void publishEntityMove(RegionEntity regionEntity) {
        Pos pos = regionEntity.entity().getPosition();
        EventsProto.EntityMoveEvent moveEvent = EventsProto.EntityMoveEvent.newBuilder()
                .setUuid(regionEntity.entity().getUuid().toString())
                .setX(pos.x())
                .setY(pos.y())
                .setZ(pos.z())
                .build();

        EventsProto.WorldEvent worldEvent = EventsProto.WorldEvent.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setShardId(shardId)
                .setEntityMove(moveEvent)
                .build();

        String subject = WORLD_ENTITIES_PREFIX + regionEntity.regionId();
        publishEvent(subject, worldEvent.toByteArray());
    }

    private void publishEntityRemove(RegionEntity regionEntity) {
        EventsProto.WorldEvent worldEvent = EventsProto.WorldEvent.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setShardId(shardId)
                .build();

        String subject = WORLD_ENTITIES_PREFIX + regionEntity.regionId() + ".remove." + regionEntity.entity().getUuid();
        publishEvent(subject, worldEvent.toByteArray());
    }

    private void publishEntityTransfer(RegionEntity regionEntity, String targetRegionId, String targetShardId) {
        EventsProto.WorldEvent worldEvent = EventsProto.WorldEvent.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setShardId(shardId)
                .build();

        String subject = WORLD_ENTITIES_PREFIX + "transfer." + regionEntity.entity().getUuid() + "." + targetShardId;
        publishEvent(subject, worldEvent.toByteArray());
    }

    private void publishEvent(String subject, byte[] data) {
        if (!running || natsConnection == null) return;

        try {
            natsConnection.publish(subject, data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish entity event to {}", subject, e);
        }
    }

    private String calculateRegionId(Point pos) {
        int chunkX = (int) Math.floor(pos.x() / 16);
        int chunkZ = (int) Math.floor(pos.z() / 16);
        int regionX = Math.floorDiv(chunkX, 4); // Assuming region size of 4 chunks
        int regionZ = Math.floorDiv(chunkZ, 4);
        return regionX + "." + regionZ;
    }

    private String determineShardForRegion(String regionId) {
        // In a real implementation, this would query the coordinator
        // For now, assume same shard
        return shardId;
    }

    public RegionEntity getEntity(UUID entityUuid) {
        return entities.get(entityUuid);
    }

    public boolean isOwned(UUID entityUuid) {
        return ownedEntities.contains(entityUuid);
    }

    public int getEntityCount() {
        return entities.size();
    }

    public int getOwnedEntityCount() {
        return ownedEntities.size();
    }

    public void stop() {
        LOGGER.info("Stopping entity manager");
        this.running = false;

        if (dispatcher != null) {
            dispatcher.unsubscribe(WORLD_ENTITIES_PREFIX + ">");
        }

        // Remove all entities
        for (RegionEntity regionEntity : entities.values()) {
            regionEntity.entity().remove();
        }
        entities.clear();
        ownedEntities.clear();

        entityExecutor.shutdown();
        try {
            if (!entityExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                entityExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            entityExecutor.shutdownNow();
        }
    }
}
