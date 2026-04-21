package com.shardedmc.shard;

import com.shardedmc.shard.coordinator.CoordinatorClient;
import com.shardedmc.shard.entity.EntityManager;
import com.shardedmc.shard.events.ShardEventBus;
import com.shardedmc.shard.lighting.LightingEngine;
import com.shardedmc.shard.player.PlayerStateManager;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.storage.ChunkStorage;
import com.shardedmc.shard.world.WorldManager;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownHook implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownHook.class);

    private final String shardId;
    private final CoordinatorClient coordinatorClient;
    private final Connection natsConnection;
    private final ShardEventBus eventBus;
    private final RegionManager regionManager;
    private final WorldManager worldManager;
    private final ShardHeartbeatService heartbeatService;
    private final EntityManager entityManager;
    private final PlayerStateManager playerStateManager;
    private final LightingEngine lightingEngine;
    private final ChunkStorage chunkStorage;
    private volatile boolean shutdownComplete = false;

    public ShutdownHook(String shardId, CoordinatorClient coordinatorClient, Connection natsConnection,
                        ShardEventBus eventBus, RegionManager regionManager, WorldManager worldManager,
                        ShardHeartbeatService heartbeatService, EntityManager entityManager,
                        PlayerStateManager playerStateManager, LightingEngine lightingEngine,
                        ChunkStorage chunkStorage) {
        this.shardId = shardId;
        this.coordinatorClient = coordinatorClient;
        this.natsConnection = natsConnection;
        this.eventBus = eventBus;
        this.regionManager = regionManager;
        this.worldManager = worldManager;
        this.heartbeatService = heartbeatService;
        this.entityManager = entityManager;
        this.playerStateManager = playerStateManager;
        this.lightingEngine = lightingEngine;
        this.chunkStorage = chunkStorage;
    }

    @Override
    public void run() {
        shutdown();
    }

    public void shutdown() {
        if (shutdownComplete) {
            LOGGER.warn("Shutdown already in progress or complete");
            return;
        }

        LOGGER.info("=== Starting graceful shutdown for shard {} ===", shardId);

        try {
            // 1. Stop accepting new connections/players
            LOGGER.info("Step 1: Stopping heartbeat service");
            if (heartbeatService != null) {
                heartbeatService.stop();
            }

            // 2. Save all chunks
            LOGGER.info("Step 2: Saving all chunks");
            if (regionManager != null) {
                regionManager.shutdown();
            }

            // 3. Save all player states
            LOGGER.info("Step 3: Saving all player states");
            if (worldManager != null) {
                worldManager.stop();
            }

            // 4. Stop entity manager
            LOGGER.info("Step 4: Stopping entity manager");
            if (entityManager != null) {
                entityManager.stop();
            }

            // 5. Stop event bus
            LOGGER.info("Step 5: Stopping event bus");
            if (eventBus != null) {
                eventBus.stop();
            }

            // 6. Shutdown player state manager
            LOGGER.info("Step 6: Shutting down player state manager");
            if (playerStateManager != null) {
                playerStateManager.shutdown();
            }

            // 7. Shutdown lighting engine
            LOGGER.info("Step 7: Shutting down lighting engine");
            if (lightingEngine != null) {
                lightingEngine.shutdown();
            }

            // 8. Unregister from coordinator
            LOGGER.info("Step 8: Unregistering from coordinator");
            if (coordinatorClient != null) {
                coordinatorClient.unregister();
                coordinatorClient.close();
            }

            // 9. Close NATS connection
            LOGGER.info("Step 9: Closing NATS connection");
            if (natsConnection != null) {
                try {
                    natsConnection.close();
                    LOGGER.info("NATS connection closed");
                } catch (Exception e) {
                    LOGGER.error("Error closing NATS connection", e);
                }
            }

            // 10. Close storage connections
            LOGGER.info("Step 10: Closing storage connections");
            if (chunkStorage != null) {
                chunkStorage.shutdown();
            }

            LOGGER.info("=== Graceful shutdown complete for shard {} ===", shardId);
            shutdownComplete = true;

        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
            // Force exit if graceful shutdown fails
            System.exit(1);
        }
    }

    public boolean isShutdownComplete() {
        return shutdownComplete;
    }
}
