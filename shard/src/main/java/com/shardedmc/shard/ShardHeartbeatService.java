package com.shardedmc.shard;

import com.shardedmc.shard.coordinator.CoordinatorClient;
import com.shardedmc.shard.region.RegionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShardHeartbeatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShardHeartbeatService.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 5;
    private static final long RECONNECT_DELAY_SECONDS = 10;

    private final CoordinatorClient coordinatorClient;
    private final WorldManager worldManager;
    private final RegionManager regionManager;
    private final String shardId;
    private ScheduledExecutorService heartbeatExecutor;
    private volatile boolean running = false;
    private volatile boolean registered = false;

    public ShardHeartbeatService(CoordinatorClient coordinatorClient, WorldManager worldManager,
                                 RegionManager regionManager, String shardId) {
        this.coordinatorClient = coordinatorClient;
        this.worldManager = worldManager;
        this.regionManager = regionManager;
        this.shardId = shardId;
    }

    public void start() {
        if (running) {
            return;
        }

        LOGGER.info("Starting heartbeat service for shard {}", shardId);
        this.running = true;

        // Connect to coordinator
        coordinatorClient.connect();

        // Register with coordinator
        registered = coordinatorClient.register("shard-" + shardId, 25565, 2000);
        if (!registered) {
            LOGGER.error("Failed to register with coordinator, will retry");
        }

        // Start heartbeat loop
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shard-heartbeat");
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info("Heartbeat service started");
    }

    public void stop() {
        if (!running) {
            return;
        }

        LOGGER.info("Stopping heartbeat service");
        this.running = false;

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }

        // Unregister from coordinator
        if (registered) {
            coordinatorClient.unregister();
            registered = false;
        }
    }

    public void sendHeartbeat() {
        if (!running) {
            return;
        }

        try {
            // Auto-reconnect if connection lost
            if (!coordinatorClient.isConnected()) {
                LOGGER.warn("Connection to coordinator lost, attempting reconnect...");
                try {
                    coordinatorClient.reconnect();
                    registered = coordinatorClient.register("shard-" + shardId, 25565, 2000);
                    if (!registered) {
                        LOGGER.error("Re-registration failed, will retry on next heartbeat");
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to reconnect to coordinator", e);
                    return;
                }
            }

            int playerCount = worldManager.getPlayerCount();
            double load = calculateLoad();
            double cpuUsage = getCpuUsage();
            double memoryUsage = getMemoryUsage();

            boolean accepted = coordinatorClient.heartbeat(playerCount, load, cpuUsage, memoryUsage, true);

            if (accepted) {
                LOGGER.debug("Heartbeat accepted for shard {}", shardId);
            } else {
                LOGGER.warn("Heartbeat not accepted by coordinator");
            }
        } catch (Exception e) {
            LOGGER.error("Heartbeat failed for shard {}", shardId, e);
        }
    }

    private double calculateLoad() {
        int playerCount = worldManager.getPlayerCount();
        int capacity = 2000;
        return Math.min(1.0, (double) playerCount / capacity);
    }

    private double getCpuUsage() {
        // TODO: Implement actual CPU monitoring
        return 0.0;
    }

    private double getMemoryUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed();
        long maxMemory = heapUsage.getMax();
        if (maxMemory <= 0) {
            maxMemory = heapUsage.getCommitted();
        }
        return maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isRegistered() {
        return registered;
    }
}
