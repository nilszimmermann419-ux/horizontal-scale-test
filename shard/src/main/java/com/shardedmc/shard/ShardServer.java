package com.shardedmc.shard;

import com.shardedmc.shard.coordinator.CoordinatorClient;
import com.shardedmc.shard.debug.DebugCommands;
import com.shardedmc.shard.entity.EntityManager;
import com.shardedmc.shard.events.ShardEventBus;
import com.shardedmc.shard.lighting.LightingEngine;
import com.shardedmc.shard.player.PlayerStateManager;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.storage.ChunkStorage;
import com.shardedmc.shard.world.WorldManager;
import io.nats.client.Connection;
import io.nats.client.Nats;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ShardServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShardServer.class);

    private final ShardConfig config;
    private final String shardId;

    private Connection natsConnection;
    private ChunkStorage chunkStorage;
    private CoordinatorClient coordinatorClient;
    private RegionManager regionManager;
    private WorldManager worldManager;
    private EntityManager entityManager;
    private ShardEventBus eventBus;
    private PlayerStateManager playerStateManager;
    private LightingEngine lightingEngine;
    private ShardHeartbeatService heartbeatService;
    private ShutdownHook shutdownHook;
    private volatile boolean running = false;

    // Metrics
    private final AtomicLong totalPacketsIn = new AtomicLong(0);
    private final AtomicLong totalPacketsOut = new AtomicLong(0);
    private final AtomicLong tickCounter = new AtomicLong(0);
    private ScheduledExecutorService metricsExecutor;

    public ShardServer() {
        this.config = new ShardConfig();
        this.shardId = config.getShardId();
        LOGGER.info("Shard {} initializing...", shardId);
    }

    public void start() throws Exception {
        LOGGER.info("Starting Shard {} on port {}", shardId, config.getShardPort());

        // 1. Initialize Minestom
        MinecraftServer minecraftServer = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(256);

        // 2. Connect to infrastructure (Redis, NATS, MinIO)
        connectToNats();
        initializeStorage();

        // 3. Connect to coordinator
        connectToCoordinator();

        // 4. Initialize region manager
        this.regionManager = new RegionManager(shardId, config.getRegionSize(), chunkStorage);
        LOGGER.info("Region manager initialized with region size {}", config.getRegionSize());

        // 5. Initialize world manager
        this.worldManager = new WorldManager(regionManager, chunkStorage);
        LOGGER.info("World manager initialized");

        // 6. Initialize entity manager
        this.entityManager = new EntityManager(shardId, natsConnection);
        LOGGER.info("Entity manager initialized");

        // 7. Initialize event bus
        this.eventBus = new ShardEventBus(natsConnection, shardId, regionManager, worldManager);
        this.eventBus.start();
        LOGGER.info("Event bus started");

        // 8. Initialize player state manager
        this.playerStateManager = new PlayerStateManager(config.getRedisUrl());
        LOGGER.info("Player state manager initialized");

        // 9. Initialize lighting engine
        this.lightingEngine = new LightingEngine(worldManager.getInstance());
        LOGGER.info("Lighting engine initialized");

        // 10. Initialize block interaction manager
        initializeBlockInteractionManager();

        // 11. Initialize crafting, inventory, damage systems
        initializeGameplaySystems();

        // 12. Register event listeners
        registerEventListeners();

        // 13. Register debug commands
        DebugCommands debugCommands = new DebugCommands(shardId, regionManager, worldManager);
        debugCommands.register();
        LOGGER.info("Debug commands registered");

        // Start world manager (registers player login/movement handlers)
        this.worldManager.start();

        // Start entity manager
        this.entityManager.start();

        // 14. Start heartbeat service
        this.heartbeatService = new ShardHeartbeatService(coordinatorClient, worldManager, regionManager, shardId);
        this.heartbeatService.start();
        LOGGER.info("Heartbeat service started");

        // 15. Start metrics collection
        startMetricsCollection();
        LOGGER.info("Metrics collection started");

        // 16. Add shutdown hook
        this.shutdownHook = new ShutdownHook(shardId, coordinatorClient, natsConnection,
                eventBus, regionManager, worldManager, heartbeatService, entityManager,
                playerStateManager, lightingEngine, chunkStorage);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        LOGGER.info("Shutdown hook registered");

        // Start server
        minecraftServer.start("0.0.0.0", config.getShardPort());
        this.running = true;

        LOGGER.info("Shard {} started successfully on port {}", shardId, config.getShardPort());
    }

    private void connectToNats() throws IOException, InterruptedException {
        String natsUrl = config.getNatsUrl();
        LOGGER.info("Connecting to NATS at {}", natsUrl);
        io.nats.client.Options options = new io.nats.client.Options.Builder()
                .server(natsUrl)
                .connectionName("shard-" + shardId)
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(1))
                .build();
        this.natsConnection = Nats.connect(options);
        LOGGER.info("Connected to NATS");
    }

    private void initializeStorage() {
        LOGGER.info("Initializing storage (Redis + MinIO)");
        this.chunkStorage = new ChunkStorage(
                config.getRedisUrl(),
                config.getMinioUrl(),
                config.getMinioAccessKey(),
                config.getMinioSecretKey()
        );
        LOGGER.info("Storage initialized - bucket: {}", config.getMinioBucket());
    }

    private void connectToCoordinator() {
        LOGGER.info("Connecting to coordinator at {}:{}", config.getCoordinatorHost(), config.getCoordinatorPort());
        this.coordinatorClient = new CoordinatorClient(shardId, config.getCoordinatorHost(), config.getCoordinatorPort());
        LOGGER.info("Coordinator client initialized");
    }

    private void initializeBlockInteractionManager() {
        LOGGER.info("Initializing block interaction manager");
        // Block interactions are handled optimistically via the event bus
        // Additional block interaction logic (e.g., redstone, pistons) would be initialized here
    }

    private void initializeGameplaySystems() {
        LOGGER.info("Initializing gameplay systems (crafting, inventory, damage)");
        // TODO: Initialize crafting system
        // TODO: Initialize inventory manager
        // TODO: Initialize damage/combat system
        // These systems will be integrated with Minestom's event pipeline
    }

    private void registerEventListeners() {
        LOGGER.info("Registering event listeners");
        GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();

        // Player login - load state
        eventHandler.addListener(PlayerLoginEvent.class, event -> {
            var future = playerStateManager.loadState(event.getPlayer().getUuid());
            future.thenAccept(state -> {
                if (state != null) {
                    LOGGER.info("Loaded saved state for player {}", event.getPlayer().getUsername());
                    // Apply saved state (position, inventory, etc.)
                    event.getPlayer().teleport(state.position);
                    event.getPlayer().setGameMode(state.gamemode);
                    event.getPlayer().setHealth(state.health);
                    event.getPlayer().setFood(state.food);
                    for (int i = 0; i < state.inventory.length && i < event.getPlayer().getInventory().getSize(); i++) {
                        event.getPlayer().getInventory().setItemStack(i, state.inventory[i]);
                    }
                }
            });
        });

        // Player disconnect - save state
        eventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            playerStateManager.saveState(event.getPlayer()).thenRun(() -> {
                LOGGER.info("Saved player state for {}", event.getPlayer().getUsername());
            });
        });

        // Block break - update lighting
        eventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            var pos = event.getBlockPosition();
            lightingEngine.updateBlockLight(pos.blockX(), pos.blockY(), pos.blockZ(), event.getBlock(), net.minestom.server.instance.block.Block.AIR);
        });

        // Block place - update lighting
        eventHandler.addListener(PlayerBlockPlaceEvent.class, event -> {
            var pos = event.getBlockPosition();
            lightingEngine.updateBlockLight(pos.blockX(), pos.blockY(), pos.blockZ(), net.minestom.server.instance.block.Block.AIR, event.getBlock());
        });
    }

    private void startMetricsCollection() {
        this.metricsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shard-metrics");
            t.setDaemon(true);
            return t;
        });

        metricsExecutor.scheduleAtFixedRate(() -> {
            long ticks = tickCounter.getAndSet(0);
            int players = worldManager.getPlayerCount();
            int entities = entityManager.getEntityCount();
            int regions = regionManager.getOwnedRegions().size();
            long packetsIn = totalPacketsIn.getAndSet(0);
            long packetsOut = totalPacketsOut.getAndSet(0);

            LOGGER.info("Metrics - Players: {}, Entities: {}, Regions: {}, Packets(in/out): {}/{}",
                    players, entities, regions, packetsIn, packetsOut);
        }, 30, 30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (!running) {
            return;
        }
        LOGGER.info("Shutting down shard {}...", shardId);
        this.running = false;

        if (metricsExecutor != null) {
            metricsExecutor.shutdownNow();
        }

        if (shutdownHook != null) {
            shutdownHook.shutdown();
        }

        LOGGER.info("Shard {} shutdown complete", shardId);
    }

    public boolean isRunning() {
        return running;
    }

    public String getShardId() {
        return shardId;
    }

    public ShardConfig getConfig() {
        return config;
    }

    public static void main(String[] args) {
        ShardServer server = new ShardServer();
        try {
            server.start();
        } catch (Exception e) {
            LOGGER.error("Failed to start shard server", e);
            System.exit(1);
        }
    }
}
