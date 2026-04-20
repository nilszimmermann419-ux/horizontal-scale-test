package com.shardedmc.coordinator;

import com.shardedmc.proto.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Master Server that coordinates all shards.
 * Manages chunk ownership, player routing, and world persistence.
 * 
 * Architecture based on MultiPaper's master server design.
 */
public class MasterServer {
    private static final Logger logger = LoggerFactory.getLogger(MasterServer.class);
    
    private final int port;
    private Server grpcServer;
    
    // Core managers
    private final ChunkOwnershipManager ownershipManager;
    private final ChunkLockManager lockManager;
    private final AnvilFormatStorage worldStorage;
    private final PlayerRouter playerRouter;
    private final ShardRegistry shardRegistry;
    private final MinecraftProxy proxy;
    
    public MasterServer(int port) {
        this.port = port;
        this.ownershipManager = new ChunkOwnershipManager();
        this.lockManager = new ChunkLockManager();
        this.worldStorage = new AnvilFormatStorage("main");
        this.playerRouter = new PlayerRouter(ownershipManager);
        this.shardRegistry = new ShardRegistry(null); // No Redis for now
        this.proxy = new MinecraftProxy(25577, shardRegistry); // Proxy on 25577
    }
    
    public void start() throws IOException {
        logger.info("Starting Master Server on port {}", port);
        
        grpcServer = ServerBuilder.forPort(port)
                .addService(new MasterServiceImpl())
                .build()
                .start();
        
        // Start proxy
        proxy.start();
        
        logger.info("Master Server started on port {}", port);
        logger.info("Minecraft proxy available on port 25577");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
    
    public void stop() {
        logger.info("Shutting down Master Server");
        
        if (grpcServer != null) {
            try {
                grpcServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
            }
        }
        
        logger.info("Master Server shut down");
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }
    
    /**
     * gRPC service implementation for shard communication.
     */
    private class MasterServiceImpl extends CoordinatorServiceGrpc.CoordinatorServiceImplBase {
        
        @Override
        public void registerShard(ShardInfo request, StreamObserver<RegistrationResponse> responseObserver) {
            String shardId = request.getShardId();
            logger.info("Shard {} registered from {}:{}", 
                    shardId, request.getAddress(), request.getPort());
            
            // Register in local registry (skip Redis since it's null)
            shardRegistry.registerShard(shardId, request.getAddress(), request.getPort(), request.getCapacity());
            
            RegistrationResponse response = RegistrationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Shard registered successfully")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void sendHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
            String shardId = request.getShardId();
            
            // Update shard info in registry
            ShardRegistry.ShardInfo info = shardRegistry.getShard(shardId).orElse(null);
            if (info != null) {
                shardRegistry.registerShard(shardId, info.address(), info.port(), info.capacity());
            }
            
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setHealthy(true)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void requestChunkLoad(ChunkLoadRequest request, StreamObserver<ChunkLoadResponse> responseObserver) {
            String shardId = request.getShardId();
            int chunkX = request.getChunkPos().getX();
            int chunkZ = request.getChunkPos().getZ();
            
            // Check if shard owns this chunk or if it's unowned
            boolean owned = ownershipManager.requestOwnership(shardId, chunkX, chunkZ);
            String owner = ownershipManager.getOwner(chunkX, chunkZ);
            
            ChunkLoadResponse.Builder responseBuilder = ChunkLoadResponse.newBuilder();
            
            if (owned || (owner != null && owner.equals(shardId))) {
                // Shard owns this chunk
                responseBuilder.setSuccess(true)
                        .setOwnerShardId(shardId);
            } else {
                // Another shard owns this chunk
                responseBuilder.setSuccess(true)
                        .setOwnerShardId(owner);
            }
            
            // Subscribe the requesting shard to this chunk
            ownershipManager.subscribe(shardId, chunkX, chunkZ);
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        }
        
        @Override
        public void requestChunkLock(LockRequest request, 
                                      StreamObserver<LockResponse> responseObserver) {
            String shardId = request.getShardId();
            int chunkX = request.getChunkPos().getX();
            int chunkZ = request.getChunkPos().getZ();
            
            boolean locked = lockManager.acquireLock(shardId, chunkX, chunkZ).join();
            String owner = lockManager.getLockHolder(chunkX, chunkZ);
            
            responseObserver.onNext(LockResponse.newBuilder()
                    .setSuccess(locked)
                    .setOwnerShardId(owner != null ? owner : "")
                    .build());
            responseObserver.onCompleted();
        }
        
        @Override
        public void requestPlayerTransfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
            String playerUuid = request.getPlayerUuid();
            String sourceShard = request.getSourceShardId();
            String targetShard = request.getTargetShardId();
            
            logger.info("Processing player transfer: {} from {} to {}", 
                    playerUuid, sourceShard, targetShard);
            
            // Update player location
            playerRouter.removePlayer(playerUuid);
            
            TransferResponse response = TransferResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage("Transfer approved")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = Integer.parseInt(System.getenv().getOrDefault("MASTER_PORT", "50051"));
        MasterServer server = new MasterServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}
