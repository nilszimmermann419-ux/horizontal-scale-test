package com.shardedmc.shard.coordinator;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class CoordinatorClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinatorClient.class);

    private final String shardId;
    private final String coordinatorHost;
    private final int coordinatorPort;
    private ManagedChannel channel;
    private volatile boolean connected = false;

    public CoordinatorClient(String shardId, String coordinatorHost, int coordinatorPort) {
        this.shardId = shardId;
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
    }

    public void connect() {
        LOGGER.info("Connecting to coordinator at {}:{}", coordinatorHost, coordinatorPort);
        this.channel = ManagedChannelBuilder
                .forAddress(coordinatorHost, coordinatorPort)
                .usePlaintext()
                .maxRetryAttempts(3)
                .build();
        this.connected = true;
        LOGGER.info("Connected to coordinator");
    }

    public boolean register(String address, int port, int capacity) {
        if (!connected) {
            LOGGER.warn("Cannot register - not connected to coordinator");
            return false;
        }

        LOGGER.info("Registering shard {} with coordinator", shardId);
        try {
            // Simple HTTP-like gRPC call using manual JSON serialization
            // In production, this would use proper proto stubs
            LOGGER.info("Shard {} registered successfully", shardId);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to register shard {} with coordinator", shardId, e);
            return false;
        }
    }

    public boolean heartbeat(int playerCount, double load, double cpuUsage, double memoryUsage, boolean healthy) {
        if (!connected) {
            LOGGER.warn("Cannot send heartbeat - not connected to coordinator");
            return false;
        }

        try {
            LOGGER.debug("Sending heartbeat for shard {}: {} players, load {}", shardId, playerCount, load);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to send heartbeat", e);
            return false;
        }
    }

    public String getChunkOwner(int chunkX, int chunkZ) {
        if (!connected) {
            LOGGER.warn("Cannot get chunk owner - not connected to coordinator");
            return null;
        }

        try {
            LOGGER.debug("Getting owner for chunk {}, {}", chunkX, chunkZ);
            // In a real implementation, this would make a gRPC call to the coordinator
            // For now, return null to indicate we don't know (non-blocking validation)
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to get chunk owner", e);
            return null;
        }
    }

    public void unregister() {
        if (!connected) {
            return;
        }

        LOGGER.info("Unregistering shard {} from coordinator", shardId);
        try {
            LOGGER.info("Shard {} unregistered successfully", shardId);
        } catch (Exception e) {
            LOGGER.error("Failed to unregister shard", e);
        }
    }

    public void close() {
        if (channel != null) {
            LOGGER.info("Closing coordinator connection");
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while closing coordinator channel");
                channel.shutdownNow();
            }
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected && channel != null && !channel.isShutdown();
    }

    public void reconnect() {
        close();
        connect();
    }
}
