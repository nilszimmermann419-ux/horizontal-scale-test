package com.shardedmc.coordinator;

import com.shardedmc.shared.ChunkPos;
import com.shardedmc.shared.RedisClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class ChunkAllocationTest {
    
    @Container
    public GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    
    private RedisClient redisClient;
    private ShardRegistry shardRegistry;
    private ChunkAllocationManager chunkAllocation;
    
    @BeforeEach
    void setUp() {
        String redisHost = redis.getHost();
        int redisPort = redis.getMappedPort(6379);
        redisClient = new RedisClient(redisHost, redisPort);
        shardRegistry = new ShardRegistry(redisClient);
        chunkAllocation = new ChunkAllocationManager(redisClient, shardRegistry);
    }
    
    @Test
    void testAllocateRegionsForShard() {
        // Register a shard
        shardRegistry.registerShard("shard-1", "localhost", 25565, 100).join();
        
        // Allocate regions
        List<ChunkPos> regions = chunkAllocation.allocateRegionsForShard("shard-1", 4);
        
        assertEquals(4, regions.size());
        
        // Verify each region is assigned
        for (ChunkPos region : regions) {
            var owner = chunkAllocation.getShardForChunk(region.x() * 16, region.z() * 16).join();
            assertTrue(owner.isPresent());
            assertEquals("shard-1", owner.get());
        }
    }
    
    @Test
    void testGetShardForChunk() {
        shardRegistry.registerShard("shard-1", "localhost", 25565, 100).join();
        chunkAllocation.allocateRegionsForShard("shard-1", 1);
        
        var owner = chunkAllocation.getShardForChunk(0, 0).join();
        assertTrue(owner.isPresent());
        assertEquals("shard-1", owner.get());
    }
    
    @Test
    void testMultipleShards() {
        shardRegistry.registerShard("shard-1", "localhost", 25565, 100).join();
        shardRegistry.registerShard("shard-2", "localhost", 25566, 100).join();
        
        chunkAllocation.allocateRegionsForShard("shard-1", 2);
        chunkAllocation.allocateRegionsForShard("shard-2", 2);
        
        List<ChunkPos> shard1Regions = chunkAllocation.getRegionsForShard("shard-1");
        List<ChunkPos> shard2Regions = chunkAllocation.getRegionsForShard("shard-2");
        
        assertEquals(2, shard1Regions.size());
        assertEquals(2, shard2Regions.size());
    }
}
