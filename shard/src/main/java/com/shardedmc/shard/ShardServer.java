package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import com.shardedmc.shared.ChunkPos;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;

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
    private InstanceContainer instance;
    
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
        
        // Set up enhanced world generator with varied terrain
        EnhancedWorldGenerator worldGenerator = new EnhancedWorldGenerator();
        instance.setGenerator(worldGenerator);
        
        // Register with coordinator
        registerWithCoordinator();
        
        // Set up event handlers
        setupEventHandlers();
        
        // Start services
        boundaryMonitor = new PlayerBoundaryMonitor(coordinatorClient, shardId);
        crossShardHandler = new CrossShardEventHandler(redisClient, instance);
        heartbeatService = new ShardHeartbeatService(coordinatorClient, shardId, capacity);
        debugGUI = new ShardDebugGUI(shardId, port, capacity, coordinatorClient);
        
        boundaryMonitor.registerEvents(MinecraftServer.getGlobalEventHandler());
        crossShardHandler.startListening();
        heartbeatService.start();
        debugGUI.register(MinecraftServer.getGlobalEventHandler());
        
        // Start server
        minecraftServer.start("0.0.0.0", port);
        
        logger.info("Shard Server {} started successfully", shardId);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
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
            event.getPlayer().setRespawnPoint(new Pos(0, 42, 0));
            logger.info("Player logged in: {} on shard {}", event.getPlayer().getUsername(), shardId);
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
