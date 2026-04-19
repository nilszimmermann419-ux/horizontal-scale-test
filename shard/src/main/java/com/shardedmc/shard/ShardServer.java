package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChunkLoadEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class ShardServer {
    private static final Logger logger = LoggerFactory.getLogger(ShardServer.class);
    
    private final String shardId;
    private final int port;
    private final int capacity;
    private final String coordinatorHost;
    private final int coordinatorPort;
    private final String redisHost;
    private final int redisPort;
    
    private ShardCoordinatorClient coordinatorClient;
    private RedisClient redisClient;
    private PlayerBoundaryMonitor boundaryMonitor;
    private CrossShardEventHandler crossShardHandler;
    private ShardHeartbeatService heartbeatService;
    private ShardDebugGUI debugGUI;
    private VanillaFeatures vanillaFeatures;
    private LightingEngine lightingEngine;
    private InstanceContainer instance;
    private AdvancedWorldGenerator worldGenerator;
    
    // Spawn location
    private static final int SPAWN_X = 0;
    private static final int SPAWN_Z = 0;
    private int spawnY = 65; // Will be calculated
    
    public ShardServer(String shardId, int port, int capacity, 
                       String coordinatorHost, int coordinatorPort,
                       String redisHost, int redisPort) {
        this.shardId = shardId;
        this.port = port;
        this.capacity = capacity;
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }
    
    public void start() throws IOException {
        logger.info("Starting Shard Server: {} on port {}", shardId, port);
        
        // Initialize MinecraftServer
        MinecraftServer minecraftServer = MinecraftServer.init();
        
        // Connect to services
        coordinatorClient = new ShardCoordinatorClient(coordinatorHost, coordinatorPort);
        redisClient = new RedisClient(redisHost, redisPort);
        
        // Create instance
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instance = instanceManager.createInstanceContainer();
        
        // Set up world generator
        worldGenerator = new AdvancedWorldGenerator();
        instance.setGenerator(worldGenerator);
        
        // Initialize lighting engine
        lightingEngine = new LightingEngine(instance);
        logger.info("Lighting engine initialized");
        
        // Calculate spawn Y based on terrain
        spawnY = worldGenerator.getTerrainHeightAt(SPAWN_X, SPAWN_Z) + 2;
        logger.info("Calculated spawn height: Y={}", spawnY);
        
        // Create safe spawn platform
        createSafeSpawn();
        
        // Register with coordinator
        registerWithCoordinator();
        
        // Set up event handlers
        setupEventHandlers();
        
        // Start services
        boundaryMonitor = new PlayerBoundaryMonitor(coordinatorClient, shardId);
        crossShardHandler = new CrossShardEventHandler(redisClient, instance);
        heartbeatService = new ShardHeartbeatService(coordinatorClient, shardId, capacity);
        debugGUI = new ShardDebugGUI(shardId, port, capacity, coordinatorClient);
        vanillaFeatures = new VanillaFeatures(instance);
        
        boundaryMonitor.registerEvents(MinecraftServer.getGlobalEventHandler());
        crossShardHandler.startListening();
        heartbeatService.start();
        debugGUI.register(MinecraftServer.getGlobalEventHandler());
        vanillaFeatures.register(MinecraftServer.getGlobalEventHandler(), lightingEngine);
        
        // Start periodic lighting initialization for loaded chunks
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Chunk chunk : instance.getChunks()) {
                if (chunk != null) {
                    lightingEngine.initializeChunk(chunk.getChunkX(), chunk.getChunkZ());
                }
            }
        }).repeat(TaskSchedule.tick(20)).schedule();
        
        // Start server
        minecraftServer.start("0.0.0.0", port);
        
        logger.info("Shard Server {} started successfully", shardId);
        logger.info("Players can connect to localhost:{}", port);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
    
    /**
     * Create a safe spawn platform to ensure players don't spawn in blocks
     */
    private void createSafeSpawn() {
        logger.info("Creating safe spawn platform at ({}, {}, {})", SPAWN_X, spawnY, SPAWN_Z);
        
        // Create a 5x5 platform at spawn
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                // Place stone platform
                instance.setBlock(SPAWN_X + x, spawnY - 2, SPAWN_Z + z, Block.STONE);
                
                // Clear area above platform
                for (int y = spawnY - 1; y <= spawnY + 3; y++) {
                    instance.setBlock(SPAWN_X + x, y, SPAWN_Z + z, Block.AIR);
                }
            }
        }
        
        // Add a marker block at center
        instance.setBlock(SPAWN_X, spawnY - 2, SPAWN_Z, Block.BEACON);
        
        logger.info("Safe spawn platform created at Y={}", spawnY - 2);
    }
    
    private void registerWithCoordinator() {
        coordinatorClient.registerShard(shardId, "localhost", port, capacity, List.of())
                .thenAccept(response -> {
                    if (response.getSuccess()) {
                        logger.info("Registered with coordinator. Assigned {} regions", 
                                response.getAssignedRegionsCount());
                    } else {
                        logger.error("Failed to register with coordinator: {}", response.getMessage());
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Registration error", ex);
                    return null;
                });
    }
    
    private void setupEventHandlers() {
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            
            // Set spawn point on our safe platform
            Pos spawnPos = new Pos(SPAWN_X + 0.5, spawnY, SPAWN_Z + 0.5);
            event.getPlayer().setRespawnPoint(spawnPos);
            
            logger.info("Player {} configured on shard {} at spawn {}", 
                    event.getPlayer().getUsername(), shardId, spawnPos);
        });
        
        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            
            // Teleport to safe spawn (just in case)
            Pos safeSpawn = new Pos(SPAWN_X + 0.5, spawnY, SPAWN_Z + 0.5);
            player.teleport(safeSpawn);
            
            // Give starter items
            VanillaFeatures.giveStarterItems(player);
            
            logger.info("Player {} spawned at {} on shard {}", 
                    player.getUsername(), safeSpawn, shardId);
        });
        
        // Initialize lighting when chunks load
        globalEventHandler.addListener(PlayerChunkLoadEvent.class, event -> {
            if (lightingEngine != null) {
                lightingEngine.initializeChunk(
                        event.getChunkX(),
                        event.getChunkZ()
                );
            }
        });
        
        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            boundaryMonitor.removePlayer(event.getPlayer().getUuid());
            debugGUI.removePlayer(event.getPlayer().getUuid());
            logger.info("Player disconnected: {} from shard {}", event.getPlayer().getUsername(), shardId);
        });
    }
    
    public void stop() {
        logger.info("Shutting down Shard Server: {}", shardId);
        
        if (heartbeatService != null) {
            heartbeatService.stop();
        }
        
        if (coordinatorClient != null) {
            coordinatorClient.close();
        }
        
        if (redisClient != null) {
            redisClient.close();
        }
        
        MinecraftServer.stopCleanly();
        logger.info("Shard Server {} shut down", shardId);
    }
    
    public static void main(String[] args) throws IOException {
        String shardId = System.getenv().getOrDefault("SHARD_ID", "shard-" + UUID.randomUUID().toString().substring(0, 8));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "25565"));
        int capacity = Integer.parseInt(System.getenv().getOrDefault("CAPACITY", "100"));
        String coordinatorHost = System.getenv().getOrDefault("COORDINATOR_HOST", "localhost");
        int coordinatorPort = Integer.parseInt(System.getenv().getOrDefault("COORDINATOR_PORT", "50051"));
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        
        ShardServer server = new ShardServer(shardId, port, capacity, 
                coordinatorHost, coordinatorPort, redisHost, redisPort);
        server.start();
    }
}
