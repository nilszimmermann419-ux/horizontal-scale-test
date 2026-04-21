package com.shardedmc.shard.player;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.nats.client.Connection;
import io.nats.client.Message;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PlayerTransferHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTransferHandler.class);

    private final String shardId;
    private final Connection natsConnection;
    private final PlayerStateManager playerStateManager;
    private final RedisAsyncCommands<String, String> redisAsync;
    private final Map<UUID, TransferRequest> pendingTransfers = new ConcurrentHashMap<>();
    private final ExecutorService transferExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "player-transfer");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = false;

    // NATS subjects
    private static final String TRANSFER_INITIATE_PREFIX = "transfer.initiate.";
    private static final String TRANSFER_RECEIVE_PREFIX = "transfer.receive.";
    private static final String TRANSFER_CONFIRM_PREFIX = "transfer.confirm.";

    public PlayerTransferHandler(String shardId, Connection natsConnection,
                                  PlayerStateManager playerStateManager, String redisUrl) {
        this.shardId = shardId;
        this.natsConnection = natsConnection;
        this.playerStateManager = playerStateManager;

        RedisClient redisClient = RedisClient.create(redisUrl);
        StatefulRedisConnection<String, String> redisConnection = redisClient.connect();
        this.redisAsync = redisConnection.async();
    }

    public void start() {
        LOGGER.info("Starting player transfer handler");
        this.running = true;

        // Subscribe to transfer events
        natsConnection.createDispatcher(this::handleTransferMessage)
                .subscribe(TRANSFER_INITIATE_PREFIX + shardId)
                .subscribe(TRANSFER_RECEIVE_PREFIX + shardId)
                .subscribe(TRANSFER_CONFIRM_PREFIX + shardId);
    }

    public CompletableFuture<Boolean> initiateTransfer(Player player, String targetShardId) {
        UUID playerUuid = player.getUuid();
        LOGGER.info("Initiating transfer for player {} to shard {}", player.getUsername(), targetShardId);

        // Create transfer request
        TransferRequest request = new TransferRequest(playerUuid, shardId, targetShardId,
                System.currentTimeMillis(), TransferStatus.INITIATED);
        pendingTransfers.put(playerUuid, request);

        // Save player state and prepare for transfer
        return playerStateManager.transferOut(player, targetShardId)
                .thenCompose(v -> {
                    // Publish transfer initiation to target shard
                    String subject = TRANSFER_INITIATE_PREFIX + targetShardId;
                    String message = playerUuid + ":" + shardId + ":" + targetShardId;

                    return CompletableFuture.runAsync(() -> {
                        natsConnection.publish(subject, message.getBytes(StandardCharsets.UTF_8));
                    }, transferExecutor);
                })
                .thenApply(v -> {
                    request.status = TransferStatus.WAITING_FOR_TARGET;
                    LOGGER.info("Transfer initiated for player {} to shard {}", player.getUsername(), targetShardId);
                    return true;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to initiate transfer for player {}", player.getUsername(), throwable);
                    pendingTransfers.remove(playerUuid);
                    return false;
                });
    }

    public CompletableFuture<Boolean> receiveTransfer(UUID playerUuid, String sourceShardId) {
        LOGGER.info("Received transfer request for player {} from shard {}", playerUuid, sourceShardId);

        // Load player state from Redis
        return playerStateManager.loadState(playerUuid)
                .thenCompose(state -> {
                    if (state == null) {
                        LOGGER.error("No state found for transferring player {}", playerUuid);
                        return CompletableFuture.completedFuture(false);
                    }

                    // Create transfer request for incoming player
                    TransferRequest request = new TransferRequest(playerUuid, sourceShardId, shardId,
                            System.currentTimeMillis(), TransferStatus.RECEIVED);
                    pendingTransfers.put(playerUuid, request);

                    // Publish confirmation back to source shard
                    String subject = TRANSFER_RECEIVE_PREFIX + sourceShardId;
                    String message = playerUuid + ":" + shardId + ":accepted";

                    return CompletableFuture.runAsync(() -> {
                        natsConnection.publish(subject, message.getBytes(StandardCharsets.UTF_8));
                    }, transferExecutor).thenApply(v -> {
                        request.status = TransferStatus.CONFIRMED;
                        LOGGER.info("Accepted transfer for player {} from shard {}", playerUuid, sourceShardId);
                        return true;
                    });
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to receive transfer for player {}", playerUuid, throwable);
                    return false;
                });
    }

    public CompletableFuture<Boolean> confirmTransfer(UUID playerUuid, String targetShardId, boolean success) {
        LOGGER.info("Confirming transfer for player {} to shard {}: {}", playerUuid, targetShardId, success);

        TransferRequest request = pendingTransfers.get(playerUuid);
        if (request == null) {
            LOGGER.warn("No pending transfer found for player {}", playerUuid);
            return CompletableFuture.completedFuture(false);
        }

        if (success) {
            request.status = TransferStatus.COMPLETED;

            // Remove player from this shard
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid);
            if (player != null) {
                player.kick("Transferred to another shard");
            }

            // Clean up state after a delay (in case transfer fails and needs rollback)
            transferExecutor.schedule(() -> {
                playerStateManager.deleteState(playerUuid);
                pendingTransfers.remove(playerUuid);
            }, 30, TimeUnit.SECONDS);

            LOGGER.info("Transfer completed for player {} to shard {}", playerUuid, targetShardId);
        } else {
            request.status = TransferStatus.FAILED;
            pendingTransfers.remove(playerUuid);
            LOGGER.error("Transfer failed for player {} to shard {}", playerUuid, targetShardId);
        }

        return CompletableFuture.completedFuture(success);
    }

    public void applyPlayerState(Player player, PlayerStateManager.PlayerState state) {
        LOGGER.info("Applying state for player {}", player.getUsername());

        // Set position
        if (state.position != null) {
            player.teleport(state.position);
        }

        // Set health and food
        player.setHealth(state.health);
        player.setFood(state.food);
        player.setFoodSaturation(state.saturation);

        // Set gamemode
        if (state.gamemode != null) {
            player.setGameMode(state.gamemode);
        }

        // Restore inventory
        if (state.inventory != null) {
            for (int i = 0; i < state.inventory.length; i++) {
                if (state.inventory[i] != null && !state.inventory[i].isAir()) {
                    player.getInventory().setItemStack(i, state.inventory[i]);
                }
            }
        }

        LOGGER.info("State applied for player {}", player.getUsername());
    }

    private void handleTransferMessage(Message msg) {
        if (!running) return;

        transferExecutor.submit(() -> {
            try {
                String subject = msg.getSubject();
                String data = new String(msg.getData(), StandardCharsets.UTF_8);
                String[] parts = data.split(":");

                if (parts.length < 2) {
                    LOGGER.warn("Invalid transfer message format: {}", data);
                    return;
                }

                UUID playerUuid = UUID.fromString(parts[0]);
                String shardId = parts[1];

                if (subject.startsWith(TRANSFER_INITIATE_PREFIX)) {
                    // Another shard wants to transfer a player to us
                    if (parts.length >= 3) {
                        String sourceShard = parts[1];
                        String targetShard = parts[2];
                        if (this.shardId.equals(targetShard)) {
                            receiveTransfer(playerUuid, sourceShard);
                        }
                    }
                } else if (subject.startsWith(TRANSFER_RECEIVE_PREFIX)) {
                    // Target shard received our transfer request
                    if (parts.length >= 3) {
                        String status = parts[2];
                        if ("accepted".equals(status)) {
                            LOGGER.info("Transfer accepted for player {} by shard {}", playerUuid, shardId);
                        }
                    }
                } else if (subject.startsWith(TRANSFER_CONFIRM_PREFIX)) {
                    // Transfer completion confirmation
                    if (parts.length >= 3) {
                        boolean success = Boolean.parseBoolean(parts[2]);
                        confirmTransfer(playerUuid, shardId, success);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error handling transfer message", e);
            }
        });
    }

    public TransferStatus getTransferStatus(UUID playerUuid) {
        TransferRequest request = pendingTransfers.get(playerUuid);
        return request != null ? request.status : TransferStatus.NONE;
    }

    public void stop() {
        LOGGER.info("Stopping player transfer handler");
        this.running = false;

        transferExecutor.shutdown();
        try {
            if (!transferExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                transferExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            transferExecutor.shutdownNow();
        }
    }

    private static class TransferRequest {
        final UUID playerUuid;
        final String sourceShard;
        final String targetShard;
        final long startTime;
        volatile TransferStatus status;

        TransferRequest(UUID playerUuid, String sourceShard, String targetShard,
                        long startTime, TransferStatus status) {
            this.playerUuid = playerUuid;
            this.sourceShard = sourceShard;
            this.targetShard = targetShard;
            this.startTime = startTime;
            this.status = status;
        }
    }

    public enum TransferStatus {
        NONE,
        INITIATED,
        WAITING_FOR_TARGET,
        RECEIVED,
        CONFIRMED,
        COMPLETED,
        FAILED
    }
}
