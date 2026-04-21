package com.shardedmc.integration;

import com.shardedmc.coordinator.CoordinatorServer;
import com.shardedmc.shared.RedisClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class FullSystemTest {
    
    @Container
    public GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    
    private CoordinatorServer coordinator;
    private RedisClient redisClient;
    
    @BeforeEach
    void setUp() throws IOException {
        String redisHost = redis.getHost();
        int redisPort = redis.getMappedPort(6379);
        
        // Start coordinator
        coordinator = new CoordinatorServer(50051, 8080, redisHost, redisPort);
        coordinator.start();
        
        redisClient = new RedisClient(redisHost, redisPort);
    }
    
    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.stop();
        }
        if (redisClient != null) {
            redisClient.close();
        }
    }
    
    @Test
    void testCoordinatorStarts() {
        assertNotNull(coordinator);
    }
    
    @Test
    void testRedisConnection() {
        redisClient.setAsync("test:key", "test:value").join();
        String value = redisClient.getAsync("test:key").join();
        
        assertEquals("test:value", value);
    }
    
}
