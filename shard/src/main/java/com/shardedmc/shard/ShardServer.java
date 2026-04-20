package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
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
    
    // Chunks this shard owns
    private final Set<ChunkPos> ownedChunks = ConcurrentHashMap.newKeySet();
    
    // Chunks this shard subscribes to (read-only)
    private final Set<ChunkPos> subscribedChunks = ConcurrentHashMap.newKeySet();
    
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
        
        // Create instance
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instance = instanceManager.createInstanceContainer();
        
        // Set up shared chunk loader so all shards see the same world
        long worldSeed = 12345L;
        SharedChunkLoader chunkLoader = new SharedChunkLoader(redisClient, "main", worldSeed);
        instance.setChunkLoader(chunkLoader);
        
        // Initialize lighting engine
        LightingEngine lightingEngine = new LightingEngine(instance);
        chunkLoader.setLightingEngine(lightingEngine);
        
        // Keep generator reference for spawn calculations
        worldGenerator = new AdvancedWorldGenerator(worldSeed);
        
        // Initialize comprehensive world sync
        worldSync = new WorldSyncManager(redisClient, shardId);
        
        // Set up event handlers
        setupEventHandlers();
        
        // Register vanilla mechanics (combat, blocks, crafting, etc.)
        VanillaMechanics mechanics = new VanillaMechanics();
        mechanics.register(MinecraftServer.getGlobalEventHandler());
        
        // Register debug commands
        DebugCommands.registerAll();
        
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
            if (!ownedChunks.contains(new ChunkPos(chunkX, chunkZ))) {
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
            if (!isChunkOwned(chunkX, chunkZ) && !subscribedChunks.contains(new ChunkPos(chunkX, chunkZ))) {
                // Subscribe to this chunk for read-only access
                subscribedChunks.add(new ChunkPos(chunkX, chunkZ));
            }
        });
        
        // Player disconnect
        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            logger.info("Player {} disconnected from shard {}", 
                    event.getPlayer().getUsername(), shardId);
        });
    }
    
    private void requestChunkOwnership(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        
        masterClient.requestChunkOwnership(shardId, chunkX, chunkZ)
                .thenAccept(owned -> {
                    if (owned) {
                        ownedChunks.add(pos);
                        subscribedChunks.remove(pos);
                        logger.debug("Shard {} acquired ownership of chunk {}, {}", 
                                shardId, chunkX, chunkZ);
                    } else {
                        // Another shard owns this chunk, subscribe to it
                        subscribedChunks.add(pos);
                        logger.debug("Shard {} subscribed to chunk {}, {}", 
                                shardId, chunkX, chunkZ);
                    }
                });
    }
    
    private boolean isChunkOwned(int chunkX, int chunkZ) {
        return ownedChunks.contains(new ChunkPos(chunkX, chunkZ));
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
    
    // Simple ChunkPos record for internal use
    private record ChunkPos(int x, int z) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkPos chunkPos = (ChunkPos) o;
            return x == chunkPos.x && z == chunkPos.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}
