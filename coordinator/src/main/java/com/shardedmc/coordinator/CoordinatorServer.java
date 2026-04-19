package com.shardedmc.coordinator;

import com.shardedmc.coordinator.rest.CoordinatorController;
import com.shardedmc.coordinator.rest.RestServer;
import com.shardedmc.proto.CoordinatorServiceGrpc;
import com.shardedmc.shared.RedisClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CoordinatorServer {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorServer.class);
    
    private final int grpcPort;
    private final int restPort;
    private final String redisHost;
    private final int redisPort;
    
    private Server grpcServer;
    private RestServer restServer;
    private RedisClient redisClient;
    private ShardRegistry shardRegistry;
    private ChunkManager chunkManager;
    private PlayerRoutingService playerRouting;
    
    public CoordinatorServer(int grpcPort, int restPort, String redisHost, int redisPort) {
        this.grpcPort = grpcPort;
        this.restPort = restPort;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }
    
    public void start() throws IOException {
        logger.info("Starting Coordinator Server...");
        
        // Initialize Redis
        redisClient = new RedisClient(redisHost, redisPort);
        
        // Initialize services
        shardRegistry = new ShardRegistry(redisClient);
        chunkManager = new ChunkManager(redisClient, shardRegistry);
        playerRouting = new PlayerRoutingService(redisClient, shardRegistry, chunkManager);
        
        // Start gRPC server
        CoordinatorServiceImpl serviceImpl = new CoordinatorServiceImpl(shardRegistry, chunkManager, playerRouting);
        grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(serviceImpl)
                .build()
                .start();
        
        logger.info("gRPC server started on port {}", grpcPort);
        
        // Start REST server
        CoordinatorController controller = new CoordinatorController(shardRegistry, chunkAllocation, playerRouting);
        restServer = new RestServer(restPort);
        restServer.start(controller);
        
        logger.info("Coordinator Server started successfully");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
    
    public void stop() {
        logger.info("Shutting down Coordinator Server...");
        
        if (grpcServer != null) {
            grpcServer.shutdown();
            try {
                grpcServer.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
            }
        }
        
        if (restServer != null) {
            restServer.stop();
        }
        
        if (redisClient != null) {
            redisClient.close();
        }
        
        logger.info("Coordinator Server shut down");
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        int grpcPort = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "50051"));
        int restPort = Integer.parseInt(System.getenv().getOrDefault("REST_PORT", "8080"));
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        
        CoordinatorServer server = new CoordinatorServer(grpcPort, restPort, redisHost, redisPort);
        server.start();
        server.blockUntilShutdown();
    }
}
