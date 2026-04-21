package com.shardedmc.shard;

import com.shardedmc.shard.coordinator.CoordinatorClient;
import com.shardedmc.shard.debug.DebugCommands;
import com.shardedmc.shard.events.ShardEventBus;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.storage.ChunkStorage;
import com.shardedmc.shard.world.WorldManager;
import io.nats.client.Connection;
import io.nats.client.Nats;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

public class ShardServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShardServer.class);

    private final String shardId;
    private final String coordinatorHost;
    private final int coordinatorPort;
    private final String natsUrl;
    private final String redisUrl;
    private final String minioUrl;
    private final String minioAccessKey;
    private final String minioSecretKey;
    private final int serverPort;
    private final int regionSize;

    private CoordinatorClient coordinatorClient;
    private Connection natsConnection;
    private ShardEventBus eventBus;
    private RegionManager regionManager;
    private ChunkStorage chunkStorage;
    private WorldManager worldManager;
    private ShardHeartbeatService heartbeatService;
    private ShutdownHook shutdownHook;
    private volatile boolean running = false;

    public ShardServer() {
        this.shardId = System.getenv().getOrDefault("SHARD_ID", UUID.randomUUID().toString().substring(0, 8));
        this.coordinatorHost = System.getenv().getOrDefault("COORDINATOR_HOST", "localhost");
        this.coordinatorPort = Integer.parseInt(System.getenv().getOrDefault("COORDINATOR_PORT", "50051"));
        this.natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        this.redisUrl = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");
        this.minioUrl = System.getenv().getOrDefault("MINIO_URL", "http://localhost:9000");
        this.minioAccessKey = System.getenv().getOrDefault("MINIO_ACCESS_KEY", "minioadmin");
        this.minioSecretKey = System.getenv().getOrDefault("MINIO_SECRET_KEY", "minioadmin");
        this.serverPort = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "25565"));
        this.regionSize = Integer.parseInt(System.getenv().getOrDefault("REGION_SIZE", "4"));

        LOGGER.info("Shard {} initializing...", shardId);
    }

    public void start() throws Exception {
        LOGGER.info("Starting Shard {} on port {}", shardId, serverPort);

        // Initialize Minestom
        MinecraftServer minecraftServer = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(256);

        // Connect to NATS
        connectToNats();

        // Initialize storage
        this.chunkStorage = new ChunkStorage(redisUrl, minioUrl, minioAccessKey, minioSecretKey);

        // Initialize region manager
        this.regionManager = new RegionManager(shardId, regionSize, chunkStorage);

        // Initialize world manager
        this.worldManager = new WorldManager(regionManager, chunkStorage);

        // Initialize event bus
        this.eventBus = new ShardEventBus(natsConnection, shardId, regionManager, worldManager);
        this.eventBus.start();

        // Initialize coordinator client
        this.coordinatorClient = new CoordinatorClient(shardId, coordinatorHost, coordinatorPort);

        // Initialize and start heartbeat service
        this.heartbeatService = new ShardHeartbeatService(coordinatorClient, worldManager, regionManager, shardId);
        this.heartbeatService.start();

        // Initialize shutdown hook
        this.shutdownHook = new ShutdownHook(shardId, coordinatorClient, natsConnection,
                eventBus, regionManager, worldManager, heartbeatService);

        // Register debug commands
        DebugCommands debugCommands = new DebugCommands(shardId, regionManager, worldManager);
        debugCommands.register();

        // Start world manager
        this.worldManager.start();

        // Start server
        minecraftServer.start("0.0.0.0", serverPort);
        this.running = true;

        LOGGER.info("Shard {} started successfully", shardId);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void connectToNats() throws IOException, InterruptedException {
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

    public void shutdown() {
        if (!running) {
            return;
        }
        LOGGER.info("Shutting down shard {}...", shardId);
        this.running = false;

        if (shutdownHook != null) {
            shutdownHook.shutdown();
        }

        LOGGER.info("Shard {} shutdown complete", shardId);
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
