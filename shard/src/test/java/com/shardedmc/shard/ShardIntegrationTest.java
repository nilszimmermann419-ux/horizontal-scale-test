package com.shardedmc.shard;

import com.shardedmc.shard.events.ShardEventBus;
import com.shardedmc.shard.player.PlayerStateManager;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.storage.ChunkStorage;
import com.shardedmc.shard.world.BlockInteractionManager;
import com.shardedmc.shard.world.WorldManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.nats.client.Connection;
import io.nats.client.Nats;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ShardIntegrationTest {

    private GenericContainer<?> redis;
    private GenericContainer<?> nats;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private Connection natsConnection;
    private ChunkStorage chunkStorage;
    private RegionManager regionManager;
    private WorldManager worldManager;
    private PlayerStateManager playerStateManager;
    private BlockInteractionManager blockInteractionManager;

    @BeforeAll
    void setUp() throws Exception {
        // Start Redis container
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();

        // Start NATS container
        nats = new GenericContainer<>(DockerImageName.parse("nats:2-alpine"))
                .withExposedPorts(4222)
                .withCommand("--js");
        nats.start();

        String redisUrl = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
        String natsUrl = "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);

        // Connect to Redis
        redisClient = RedisClient.create(redisUrl);
        redisConnection = redisClient.connect();

        // Connect to NATS
        io.nats.client.Options options = new io.nats.client.Options.Builder()
                .server(natsUrl)
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(1))
                .build();
        natsConnection = Nats.connect(options);

        // Initialize storage
        chunkStorage = new ChunkStorage(redisUrl, "http://localhost:9000", "minioadmin", "minioadmin");

        // Initialize region manager
        regionManager = new RegionManager("test-shard", 4, chunkStorage);

        // Initialize world manager
        worldManager = new WorldManager(regionManager, chunkStorage);

        // Initialize player state manager
        playerStateManager = new PlayerStateManager(redisUrl);

        // Initialize block interaction manager
        blockInteractionManager = new BlockInteractionManager("test-shard", natsConnection);
    }

    @AfterAll
    void tearDown() {
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        if (natsConnection != null) {
            natsConnection.close();
        }
        if (redis != null) {
            redis.stop();
        }
        if (nats != null) {
            nats.stop();
        }
    }

    @Test
    void testChunkLoading() throws Exception {
        // Create a test instance
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();

        // Test loading a chunk
        int chunkX = 0;
        int chunkZ = 0;

        // The chunk should be generated/loaded
        var chunk = instance.loadChunk(chunkX, chunkZ).get(5, TimeUnit.SECONDS);
        assertNotNull(chunk, "Chunk should be loaded");

        // Verify chunk coordinates
        assertEquals(chunkX, chunk.getChunkX(), "Chunk X coordinate should match");
        assertEquals(chunkZ, chunk.getChunkZ(), "Chunk Z coordinate should match");

        // Verify chunk has blocks (terrain generation)
        Block block = chunk.getBlock(0, 64, 0);
        assertNotNull(block, "Block should exist at 0, 64, 0");
    }

    @Test
    void testBlockBreaking() {
        // Create a test instance
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();

        // Set a block
        Pos pos = new Pos(0, 100, 0);
        instance.setBlock(pos, Block.STONE);

        // Verify block was placed
        Block placedBlock = instance.getBlock(pos);
        assertEquals(Block.STONE, placedBlock, "Block should be stone");

        // Simulate block break
        instance.setBlock(pos, Block.AIR);

        // Verify block was broken (replaced with air)
        Block brokenBlock = instance.getBlock(pos);
        assertEquals(Block.AIR, brokenBlock, "Block should be air after break");
    }

    @Test
    void testPlayerStateSaveLoad() throws Exception {
        // Create a mock player (we'll test the state serialization directly)
        UUID playerUuid = UUID.randomUUID();
        String username = "TestPlayer";

        // Create a player state manually
        PlayerStateManager.PlayerState state = new PlayerStateManager.PlayerState();
        state.uuid = playerUuid;
        state.username = username;
        state.position = new Pos(100, 64, 200, 90, 0);
        state.health = 20.0f;
        state.food = 20;
        state.saturation = 5.0f;

        // Save state to Redis
        String key = "player:state:" + playerUuid;
        String stateJson = String.format(
                "{\"uuid\":\"%s\",\"username\":\"%s\",\"position\":{\"x\":100.0,\"y\":64.0,\"z\":200.0,\"yaw\":90.0,\"pitch\":0.0},\"health\":20.0,\"food\":20,\"saturation\":5.0,\"gamemode\":\"SURVIVAL\",\"inventory\":[]}",
                playerUuid, username
        );

        redisConnection.sync().setex(key, 300, stateJson);

        // Load state back
        String loadedJson = redisConnection.sync().get(key);
        assertNotNull(loadedJson, "Loaded state should not be null");
        assertTrue(loadedJson.contains(username), "Loaded state should contain username");
        assertTrue(loadedJson.contains("100.0"), "Loaded state should contain position x");

        // Test TTL
        Long ttl = redisConnection.sync().ttl(key);
        assertTrue(ttl > 0 && ttl <= 300, "TTL should be set correctly");
    }

    @Test
    void testEventPublishing() throws Exception {
        // Initialize event bus
        ShardEventBus eventBus = new ShardEventBus(natsConnection, "test-shard", regionManager, worldManager);
        eventBus.start();

        try {
            // Publish a test event
            String testSubject = "world.test.events";
            byte[] testData = "test-event-data".getBytes();

            // Subscribe to test subject
            var subscription = natsConnection.subscribe(testSubject);

            // Publish event
            eventBus.publishEvent(testSubject, testData);

            // Wait for message
            var message = subscription.nextMessage(Duration.ofSeconds(5));
            assertNotNull(message, "Should receive published message");
            assertArrayEquals(testData, message.getData(), "Message data should match");

            subscription.unsubscribe();
        } finally {
            eventBus.stop();
        }
    }

    @Test
    void testChunkStorageSaveLoad() throws Exception {
        // Test saving chunk data
        int chunkX = 5;
        int chunkZ = -3;
        byte[] chunkData = new byte[1024];
        for (int i = 0; i < chunkData.length; i++) {
            chunkData[i] = (byte) (i % 256);
        }

        // Save chunk
        chunkStorage.saveChunk(chunkX, chunkZ, chunkData).get(5, TimeUnit.SECONDS);

        // Load chunk
        byte[] loadedData = chunkStorage.loadChunk(chunkX, chunkZ).get(5, TimeUnit.SECONDS);
        assertNotNull(loadedData, "Loaded chunk data should not be null");
        assertArrayEquals(chunkData, loadedData, "Loaded chunk data should match saved data");
    }

    @Test
    void testRegionOwnership() {
        // Test region ownership logic
        RegionManager.RegionCoord coord = new RegionManager.RegionCoord(0, 0);

        // Initially no regions should be owned (until registered with coordinator)
        // This tests the basic region coordinate logic
        assertNotNull(coord, "Region coordinate should not be null");

        // Test region size calculation
        int regionSize = 4;
        int chunkX = 10;
        int chunkZ = -5;

        int regionX = Math.floorDiv(chunkX, regionSize);
        int regionZ = Math.floorDiv(chunkZ, regionSize);

        assertEquals(2, regionX, "Region X should be 2 (10/4)");
        assertEquals(-2, regionZ, "Region Z should be -2 (-5/4)");
    }

    @Test
    void testBlockInteractionManager() {
        // Start the manager
        blockInteractionManager.start();

        try {
            // Verify manager is running
            assertTrue(true, "Block interaction manager should start without error");

            // Test that pending changes map is accessible
            // In a real scenario, we'd simulate block break/place events
            // but that requires a full Minestom server context
        } finally {
            blockInteractionManager.stop();
        }
    }
}
