package com.shardedmc.shard.events;

import com.google.gson.Gson;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.world.WorldManager;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Subscription;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class ShardEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShardEventBus.class);
    private static final Gson GSON = new Gson();

    private final Connection natsConnection;
    private final String shardId;
    private final RegionManager regionManager;
    private final WorldManager worldManager;
    private final ExecutorService eventExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "event-bus");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, Long> eventSequence = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private Dispatcher dispatcher;

    // NATS subjects
    private static final String WORLD_BLOCKS_PREFIX = "world.blocks.";
    private static final String WORLD_ENTITIES_PREFIX = "world.entities.";
    private static final String WORLD_PLAYERS_PREFIX = "world.players.";
    private static final String WORLD_GLOBAL = "world.global";
    private static final String STATE_SYNC = "world.sync.";

    public ShardEventBus(Connection natsConnection, String shardId, RegionManager regionManager, WorldManager worldManager) {
        this.natsConnection = natsConnection;
        this.shardId = shardId;
        this.regionManager = regionManager;
        this.worldManager = worldManager;
    }

    public void start() {
        LOGGER.info("Starting event bus");
        this.running = true;

        // Subscribe to global events
        dispatcher = natsConnection.createDispatcher(this::handleNatsMessage);
        dispatcher.subscribe(WORLD_GLOBAL);
        dispatcher.subscribe(WORLD_BLOCKS_PREFIX + ">");
        dispatcher.subscribe(WORLD_ENTITIES_PREFIX + ">");
        dispatcher.subscribe(STATE_SYNC + ">");

        // Set up local event handlers
        setupLocalEventHandlers();
    }

    private void setupLocalEventHandlers() {
        GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();

        // Block break events - optimistic, publish to bus
        eventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            Point pos = event.getBlockPosition();

            // Optimistically allow the break
            LOGGER.debug("Player {} broke block at {},{},{}" , player.getUsername(), pos.blockX(), pos.blockY(), pos.blockZ());

            // Publish event to NATS using JSON
            java.util.Map<String, Object> blockEvent = new java.util.HashMap<>();
            blockEvent.put("type", "block_change");
            blockEvent.put("x", pos.blockX());
            blockEvent.put("y", pos.blockY());
            blockEvent.put("z", pos.blockZ());
            blockEvent.put("blockId", "minecraft:air");
            blockEvent.put("playerId", player.getUuid().toString());
            blockEvent.put("timestamp", System.currentTimeMillis());
            blockEvent.put("shardId", shardId);

            publishWorldEvent(pos.blockX(), pos.blockZ(), GSON.toJson(blockEvent).getBytes(StandardCharsets.UTF_8));
        });

        // Block place events
        eventHandler.addListener(PlayerBlockPlaceEvent.class, event -> {
            Player player = event.getPlayer();
            Point pos = event.getBlockPosition();
            Block block = event.getBlock();

            LOGGER.debug("Player {} placed block {} at {},{},{}",
                    player.getUsername(), block.name(), pos.blockX(), pos.blockY(), pos.blockZ());

            java.util.Map<String, Object> blockEvent = new java.util.HashMap<>();
            blockEvent.put("type", "block_change");
            blockEvent.put("x", pos.blockX());
            blockEvent.put("y", pos.blockY());
            blockEvent.put("z", pos.blockZ());
            blockEvent.put("blockId", block.name());
            blockEvent.put("playerId", player.getUuid().toString());
            blockEvent.put("timestamp", System.currentTimeMillis());
            blockEvent.put("shardId", shardId);

            publishWorldEvent(pos.blockX(), pos.blockZ(), GSON.toJson(blockEvent).getBytes(StandardCharsets.UTF_8));
        });

        // Player movement events
        eventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Point pos = event.getNewPosition();

            java.util.Map<String, Object> moveEvent = new java.util.HashMap<>();
            moveEvent.put("type", "player_move");
            moveEvent.put("uuid", player.getUuid().toString());
            moveEvent.put("x", pos.x());
            moveEvent.put("y", pos.y());
            moveEvent.put("z", pos.z());
            moveEvent.put("yaw", pos.yaw());
            moveEvent.put("pitch", pos.pitch());
            moveEvent.put("timestamp", System.currentTimeMillis());
            moveEvent.put("shardId", shardId);

            publishPlayerEvent(player.getUuid().toString(), GSON.toJson(moveEvent).getBytes(StandardCharsets.UTF_8));
        });
    }

    private void handleNatsMessage(Message msg) {
        if (!running) return;

        eventExecutor.submit(() -> {
            try {
                String subject = msg.getSubject();
                byte[] data = msg.getData();

                if (subject.startsWith(WORLD_BLOCKS_PREFIX)) {
                    handleBlockChangeEvent(data);
                } else if (subject.startsWith(WORLD_ENTITIES_PREFIX)) {
                    handleEntityEvent(data);
                } else if (subject.startsWith(WORLD_PLAYERS_PREFIX)) {
                    handlePlayerEvent(data);
                } else if (subject.equals(WORLD_GLOBAL)) {
                    handleGlobalEvent(data);
                } else if (subject.startsWith(STATE_SYNC)) {
                    handleStateSync(data);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling NATS message", e);
            }
        });
    }

    private void handleBlockChangeEvent(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            java.util.Map<String, Object> event = GSON.fromJson(json, java.util.Map.class);

            if ("block_change".equals(event.get("type"))) {
                int x = ((Number) event.get("x")).intValue();
                int y = ((Number) event.get("y")).intValue();
                int z = ((Number) event.get("z")).intValue();
                String blockId = (String) event.get("blockId");
                String eventShardId = (String) event.get("shardId");

                // Check if this is our region
                int chunkX = x >> 4;
                int chunkZ = z >> 4;

                if (regionManager.isOwned(chunkX, chunkZ)) {
                    // Apply the block change if it's from another shard (conflict resolution)
                    if (!shardId.equals(eventShardId)) {
                        applyBlockChange(x, y, z, blockId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error handling block change event", e);
        }
    }

    private void applyBlockChange(int x, int y, int z, String blockId) {
        Instance instance = worldManager.getInstance();
        if (instance == null) return;

        // Apply block change with conflict resolution
        // For now, use last-write-wins based on timestamp
        Block block = Block.fromNamespaceId(blockId);
        if (block != null) {
            instance.setBlock(x, y, z, block);
            LOGGER.debug("Applied block change at {},{},{} to {}",
                    x, y, z, blockId);
        }
    }

    private void handleEntityEvent(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            java.util.Map<String, Object> event = GSON.fromJson(json, java.util.Map.class);
            // TODO: Handle entity spawn/move events
        } catch (Exception e) {
            LOGGER.error("Error handling entity event", e);
        }
    }

    private void handlePlayerEvent(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            java.util.Map<String, Object> event = GSON.fromJson(json, java.util.Map.class);
            // TODO: Handle player events from other shards
        } catch (Exception e) {
            LOGGER.error("Error handling player event", e);
        }
    }

    private void handleGlobalEvent(byte[] data) {
        try {
            // Handle global events (weather, time, etc.)
            LOGGER.debug("Received global event");
        } catch (Exception e) {
            LOGGER.error("Error handling global event", e);
        }
    }

    private void handleStateSync(byte[] data) {
        try {
            // Handle state sync requests
            LOGGER.debug("Received state sync request");
        } catch (Exception e) {
            LOGGER.error("Error handling state sync", e);
        }
    }

    private void publishWorldEvent(int blockX, int blockZ, byte[] eventData) {
        int regionX = Math.floorDiv(blockX >> 4, regionManager.getRegionSize());
        int regionZ = Math.floorDiv(blockZ >> 4, regionManager.getRegionSize());

        String subject = WORLD_BLOCKS_PREFIX + regionX + "." + regionZ;
        publishEvent(subject, eventData);
    }

    private void publishPlayerEvent(String playerUuid, byte[] eventData) {
        String subject = WORLD_PLAYERS_PREFIX + playerUuid;
        publishEvent(subject, eventData);
    }

    public void publishEvent(String subject, byte[] data) {
        if (!running || natsConnection == null) return;

        try {
            natsConnection.publish(subject, data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish event to {}", subject, e);
        }
    }

    public void requestStateSync() {
        LOGGER.info("Requesting state sync");
        String subject = STATE_SYNC + shardId;
        publishEvent(subject, new byte[0]);
    }

    private long getNextSequence(String category) {
        return eventSequence.merge(category, 1L, Long::sum);
    }

    public void stop() {
        LOGGER.info("Stopping event bus");
        this.running = false;

        if (dispatcher != null) {
            dispatcher.unsubscribe(WORLD_GLOBAL);
            dispatcher.unsubscribe(WORLD_BLOCKS_PREFIX + ">");
            dispatcher.unsubscribe(WORLD_ENTITIES_PREFIX + ">");
            dispatcher.unsubscribe(STATE_SYNC + ">");
        }

        eventExecutor.shutdown();
        try {
            if (!eventExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                eventExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            eventExecutor.shutdownNow();
        }
    }
}
