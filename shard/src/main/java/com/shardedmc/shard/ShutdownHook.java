package com.shardedmc.shard;

import com.shardedmc.shard.coordinator.CoordinatorClient;
import com.shardedmc.shard.events.ShardEventBus;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.world.WorldManager;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ShutdownHook implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownHook.class);

    private final String shardId;
    private final CoordinatorClient coordinatorClient;
    private final Connection natsConnection;
    private final ShardEventBus eventBus;
    private final RegionManager regionManager;
    private final WorldManager worldManager;
    private final ShardHeartbeatService heartbeatService;
    private volatile boolean shutdownComplete = false;

    public ShutdownHook(String shardId, CoordinatorClient coordinatorClient, Connection natsConnection,
                        ShardEventBus eventBus, RegionManager regionManager, WorldManager worldManager,
                        ShardHeartbeatService heartbeatService) {
        this.shardId = shardId;
        this.coordinatorClient = coordinatorClient;
        this.natsConnection = natsConnection;
        this.eventBus = eventBus;
        this.regionManager = regionManager;
        this.worldManager = worldManager;
        this.heartbeatService = heartbeatService;
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

            // 4. Stop event bus
            LOGGER.info("Step 4: Stopping event bus");
            if (eventBus != null) {
                eventBus.stop();
            }

            // 5. Unregister from coordinator
            LOGGER.info("Step 5: Unregistering from coordinator");
            if (coordinatorClient != null) {
                coordinatorClient.unregister();
                coordinatorClient.close();
            }

            // 6. Close NATS connection
            LOGGER.info("Step 6: Closing NATS connection");
            if (natsConnection != null) {
                try {
                    natsConnection.close();
                    LOGGER.info("NATS connection closed");
                } catch (Exception e) {
                    LOGGER.error("Error closing NATS connection", e);
                }
            }

            // 7. Close Redis connections (handled by ChunkStorage)
            LOGGER.info("Step 7: Closing storage connections");
            // Redis and MinIO connections are managed by ChunkStorage
            // They should be closed when ChunkStorage is garbage collected or explicitly closed

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
