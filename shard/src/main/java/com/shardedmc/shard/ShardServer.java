package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Shard Server that participates in the distributed world.
 * Respects chunk ownership and syncs changes with the master.
 */
public class ShardServer {
    private static final Logger logger = LoggerFactory.getLogger(ShardServer.class);
    
    private final String shardId;
    private final int port;
    private final int capacity;
    private final String masterAddress;
    private final String redisHost;
    private final int redisPort;
    
    private MasterClient masterClient;
    private RedisClient redisClient;
    private InstanceContainer instance;
    private AdvancedWorldGenerator worldGenerator;
    private WorldSyncManager worldSync;
    private DimensionManager dimensionManager;
    private com.shardedmc.shard.vanilla.PortalHandler portalHandler;
    
    // Entity management systems
    private EntityLimiter entityLimiter;
    private EntityActivationManager entityActivationManager;
    private VillagerOptimizer villagerOptimizer;
    private ItemDespawnManager itemDespawnManager;
    
    // Redstone & hopper optimization
    private RedstoneLagDetector redstoneLagDetector;
    private OptimizedRedstone optimizedRedstone;
    private HopperOptimizer hopperOptimizer;
    
    // Performance monitoring systems (Tasks 11-13)
    private ChunkPregenerator chunkPregenerator;
    private PerformanceMonitor performanceMonitor;
    private AutoRestart autoRestart;
    
    // Chunks this shard owns - using Long keys to avoid ChunkPos allocation in hot paths
    private final Set<Long> ownedChunks = ConcurrentHashMap.newKeySet();
    
    // Chunks this shard subscribes to (read-only) - using Long keys to avoid ChunkPos allocation
    private final Set<Long> subscribedChunks = ConcurrentHashMap.newKeySet();
    
    // Scheduled executor for background tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public ShardServer(String shardId, int port, int capacity,
                       String masterAddress, String redisHost, int redisPort) {
        this.shardId = shardId;
        this.port = port;
        this.capacity = capacity;
        this.masterAddress = masterAddress;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }
    
    public void start() throws IOException {
        logger.info("Starting Shard {} on port {}", shardId, port);
        
        // Connect to master
        masterClient = new MasterClient(masterAddress);
        redisClient = new RedisClient(redisHost, redisPort);
        
        // Register with master
        masterClient.registerShard(shardId, "localhost", port, capacity)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Shard {} registered with master", shardId);
                    } else {
                        logger.error("Failed to register shard {} with master", shardId);
                    }
                });
        
        // Initialize MinecraftServer
        MinecraftServer minecraftServer = MinecraftServer.init();
        
        // Set up shared chunk loader so all shards see the same world
        long worldSeed = 12345L;
        SharedChunkLoader chunkLoader = new SharedChunkLoader(redisClient, "main", worldSeed);
        
        // Initialize dimension manager with all dimensions
        dimensionManager = new DimensionManager(chunkLoader);
        dimensionManager.initializeDimensions();
        
        // Get the default (overworld) instance
        instance = dimensionManager.getDefaultInstance();
        
        // Initialize lighting engine
        LightingEngine lightingEngine = new LightingEngine(instance);
        chunkLoader.setLightingEngine(lightingEngine);
        
        // Keep generator reference for spawn calculations
        worldGenerator = new AdvancedWorldGenerator(worldSeed);
        
        // Initialize comprehensive world sync
        worldSync = new WorldSyncManager(redisClient, shardId);
        
        // Initialize entity management systems
        entityLimiter = new EntityLimiter();
        entityActivationManager = new EntityActivationManager();
        villagerOptimizer = new VillagerOptimizer();
        itemDespawnManager = new ItemDespawnManager();
        
        // Register entity management event handlers
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        entityLimiter.register(globalEventHandler);
        entityActivationManager.register(globalEventHandler);
        villagerOptimizer.register(globalEventHandler);
        itemDespawnManager.register(globalEventHandler);
        
        // Set up event handlers
        setupEventHandlers();
        
        // Register vanilla mechanics (combat, blocks, crafting, etc.)
        VanillaMechanics mechanics = new VanillaMechanics();
        mechanics.register(MinecraftServer.getGlobalEventHandler());
        
        // Set up portal handler for dimension transitions
        portalHandler = new com.shardedmc.shard.vanilla.PortalHandler(dimensionManager);
        portalHandler.register(MinecraftServer.getGlobalEventHandler());
        
        // Register redstone lag detector (Task 6)
        redstoneLagDetector = new RedstoneLagDetector();
        redstoneLagDetector.register(MinecraftServer.getGlobalEventHandler());
        
        // Register optimized redstone engine (Task 7)
        optimizedRedstone = new OptimizedRedstone();
        optimizedRedstone.setLagDetector(redstoneLagDetector);
        optimizedRedstone.register(MinecraftServer.getGlobalEventHandler());
        
        // Register hopper optimizer (Task 5)
        hopperOptimizer = new HopperOptimizer();
        hopperOptimizer.register(MinecraftServer.getGlobalEventHandler());
        
        logger.info("Redstone and farm optimization systems registered for shard {}", shardId);
        
        // Register performance monitoring systems (Tasks 11-13)
        chunkPregenerator = new ChunkPregenerator(redisClient, shardId, instance);
        chunkPregenerator.register();
        
        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.register(MinecraftServer.getGlobalEventHandler());
        
        autoRestart = new AutoRestart(performanceMonitor, 12 * 60); // 12 hours in minutes
        autoRestart.register(MinecraftServer.getGlobalEventHandler());
        
        logger.info("Performance monitoring systems registered for shard {}", shardId);
        
        // Register debug commands
        DebugCommands.registerAll();
        
        // Initialize NPC manager and connect to debug commands
        com.shardedmc.shard.vanilla.NPCManager npcManager = new com.shardedmc.shard.vanilla.NPCManager();
        DebugCommands.setNPCManager(npcManager);
        
        // Start heartbeat
        startHeartbeat();
        
        // Start server
        minecraftServer.start("0.0.0.0", port);
        
        logger.info("Shard {} started on port {}", shardId, port);
    }
    
    private void setupEventHandlers() {
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        
        // Player configuration
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            
            // Calculate spawn position
            int spawnX = 0;
            int spawnZ = 0;
            int spawnY = worldGenerator.getTerrainHeightAt(spawnX, spawnZ) + 2;
            
            Pos spawnPos = new Pos(spawnX + 0.5, spawnY, spawnZ + 0.5);
            event.getPlayer().setRespawnPoint(spawnPos);
        });
        
        // Player spawn
        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            
            // Request ownership of spawn chunk
            int chunkX = player.getPosition().chunkX();
            int chunkZ = player.getPosition().chunkZ();
            requestChunkOwnership(chunkX, chunkZ);
            
            // Give starter items
            VanillaFeatures.giveStarterItems(player);
            
            logger.info("Player {} spawned on shard {}", player.getUsername(), shardId);
        });
        
        // Chunk loading
        globalEventHandler.addListener(PlayerChunkLoadEvent.class, event -> {
            int chunkX = event.getChunkX();
            int chunkZ = event.getChunkZ();
            
            // Request ownership if not already owned
            long chunkKey = getChunkKey(chunkX, chunkZ);
            if (!ownedChunks.contains(chunkKey)) {
                requestChunkOwnership(chunkX, chunkZ);
            }
        });
        
        // Block breaking
        globalEventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            int chunkX = event.getBlockPosition().chunkX();
            int chunkZ = event.getBlockPosition().chunkZ();
            
            // Check if we own this chunk
            if (!isChunkOwned(chunkX, chunkZ)) {
                event.setCancelled(true);
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "You cannot modify blocks in chunks owned by other shards!"));
                logger.debug("Blocked block break by {} at non-owned chunk {}, {}", 
                        player.getUsername(), chunkX, chunkZ);
                return;
            }
            
            // Sync block change to other shards
            worldSync.syncBlockChange(
                    (int) event.getBlockPosition().x(),
                    (int) event.getBlockPosition().y(),
                    (int) event.getBlockPosition().z(),
                    Block.AIR
            );
        });
        
        // Block placing
        globalEventHandler.addListener(PlayerBlockPlaceEvent.class, event -> {
            int chunkX = event.getBlockPosition().chunkX();
            int chunkZ = event.getBlockPosition().chunkZ();
            
            // Check if we own this chunk
            if (!isChunkOwned(chunkX, chunkZ)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                        "You cannot modify blocks in chunks owned by other shards!"));
                return;
            }
            
            // Sync block change
            worldSync.syncBlockChange(
                    (int) event.getBlockPosition().x(),
                    (int) event.getBlockPosition().y(),
                    (int) event.getBlockPosition().z(),
                    event.getBlock()
            );
        });
        
        // Player movement (check for shard boundary crossing)
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            int chunkX = event.getNewPosition().chunkX();
            int chunkZ = event.getNewPosition().chunkZ();
            
            // If entering a chunk owned by another shard, request transfer
            long chunkKey = getChunkKey(chunkX, chunkZ);
            if (!isChunkOwned(chunkX, chunkZ) && !subscribedChunks.contains(chunkKey)) {
                // Subscribe to this chunk for read-only access
                subscribedChunks.add(chunkKey);
            }
        });
        
        // Player disconnect
        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            logger.info("Player {} disconnected from shard {}", 
                    event.getPlayer().getUsername(), shardId);
        });
    }
    
    private void requestChunkOwnership(int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        
        masterClient.requestChunkOwnership(shardId, chunkX, chunkZ)
                .thenAccept(owned -> {
                    if (owned) {
                        ownedChunks.add(chunkKey);
                        subscribedChunks.remove(chunkKey);
                        logger.debug("Shard {} acquired ownership of chunk {}, {}", 
                                shardId, chunkX, chunkZ);
                    } else {
                        // Another shard owns this chunk, subscribe to it
                        subscribedChunks.add(chunkKey);
                        logger.debug("Shard {} subscribed to chunk {}, {}", 
                                shardId, chunkX, chunkZ);
                    }
                });
    }
    
    private boolean isChunkOwned(int chunkX, int chunkZ) {
        return ownedChunks.contains(getChunkKey(chunkX, chunkZ));
    }
    
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int playerCount = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
                masterClient.sendHeartbeat(shardId, 0.0, playerCount);
            } catch (Exception e) {
                logger.error("Heartbeat failed", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    public void stop() {
        logger.info("Shutting down shard {}", shardId);
        scheduler.shutdown();
        
        if (masterClient != null) {
            masterClient.close();
        }
        if (redisClient != null) {
            redisClient.close();
        }
        
        MinecraftServer.stopCleanly();
    }
    
    public static void main(String[] args) throws IOException {
        String shardId = System.getenv().getOrDefault("SHARD_ID", "shard-" + UUID.randomUUID().toString().substring(0, 8));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "25565"));
        int capacity = Integer.parseInt(System.getenv().getOrDefault("CAPACITY", "100"));
        String masterAddress = System.getenv().getOrDefault("MASTER_ADDRESS", "localhost:50051");
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        
        ShardServer server = new ShardServer(shardId, port, capacity, masterAddress, redisHost, redisPort);
        server.start();
    }
    
}
