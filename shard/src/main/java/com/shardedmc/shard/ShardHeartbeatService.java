package com.shardedmc.shard;

import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shard.ShardCoordinatorClient;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShardHeartbeatService {
    private static final Logger logger = LoggerFactory.getLogger(ShardHeartbeatService.class);
    private static final long HEARTBEAT_INTERVAL_MS = 1000;
    
    private final ShardCoordinatorClient coordinatorClient;
    private final String shardId;
    private final int capacity;
    private final ScheduledExecutorService scheduler;
    private final MemoryMXBean memoryMXBean;
    
    public ShardHeartbeatService(ShardCoordinatorClient coordinatorClient, String shardId, int capacity) {
        this.coordinatorClient = coordinatorClient;
        this.shardId = shardId;
        this.capacity = capacity;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shard-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }
    
    public void start() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("Started heartbeat service for shard {}", shardId);
    }
    
    private void sendHeartbeat() {
        try {
            int playerCount = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            double load = (double) playerCount / capacity;
            int memoryUsage = (int) (memoryMXBean.getHeapMemoryUsage().getUsed() / 1024 / 1024);
            
            // TODO: Get actual region assignments
            List<ChunkPos> regions = List.of();
            
            coordinatorClient.sendHeartbeat(shardId, load, playerCount, regions)
                    .thenAccept(response -> {
                        if (response.getShouldShutdown()) {
                            logger.warn("Coordinator requested shutdown");
                            MinecraftServer.stopCleanly();
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("Failed to send heartbeat", ex);
                        return null;
                    });
            
        } catch (Exception e) {
            logger.error("Error in heartbeat", e);
        }
    }
    
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        logger.info("Stopped heartbeat service for shard {}", shardId);
    }
}
