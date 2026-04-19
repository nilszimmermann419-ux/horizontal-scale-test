package com.shardedmc.coordinator;

import com.shardedmc.proto.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ProductionCoordinatorService extends CoordinatorServiceGrpc.CoordinatorServiceImplBase {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionCoordinatorService.class);
    
    private final ProductionCoordinatorServer server;
    
    public ProductionCoordinatorService(ProductionCoordinatorServer server) {
        this.server = server;
    }
    
    @Override
    public void registerShard(ShardInfo request, StreamObserver<RegistrationResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        try {
            String shardId = request.getShardId();
            String host = request.getAddress();
            int port = request.getPort();
            
            server.getStructuredLogger().logShardEvent(shardId, "shard_register", 
                "Shard registering", Map.of("host", host, "port", String.valueOf(port)));
            
            ProductionCoordinatorServer.ShardInfo shard = new ProductionCoordinatorServer.ShardInfo(
                shardId, host, port);
            shard.setLastHeartbeat(System.currentTimeMillis());
            server.getRegisteredShards().put(shardId, shard);
            
            RegistrationResponse response = RegistrationResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Shard registered successfully")
                .addAllAssignedRegions(request.getRegionsList())
                .build();
            
            server.getMetrics().recordGrpcCall("RegisterShard", "success", 
                System.currentTimeMillis() - startTime);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOGGER.error("Failed to register shard", e);
            server.getMetrics().recordGrpcCall("RegisterShard", "failure", 
                System.currentTimeMillis() - startTime);
            
            RegistrationResponse response = RegistrationResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void sendHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        String shardId = request.getShardId();
        
        ProductionCoordinatorServer.ShardInfo shard = server.getRegisteredShards().get(shardId);
        if (shard != null) {
            shard.setLastHeartbeat(System.currentTimeMillis());
            shard.setPlayerCount(request.getPlayerCount());
            shard.setHealthy(true);
            
            server.getMetrics().recordShardHealth(shardId, true, request.getLoad());
            
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                .setHealthy(true)
                .setShouldShutdown(false)
                .build();
            
            server.getMetrics().recordGrpcCall("Heartbeat", "success", 
                System.currentTimeMillis() - startTime);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            server.getMetrics().recordGrpcCall("Heartbeat", "failure", 
                System.currentTimeMillis() - startTime);
            
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                .setHealthy(false)
                .setShouldShutdown(false)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void requestPlayerTransfer(TransferRequest request, 
            StreamObserver<TransferResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        String playerId = request.getPlayerUuid();
        String sourceShard = request.getSourceShardId();
        String targetShard = request.getTargetShardId();
        
        server.getStructuredLogger().logPlayerEvent(playerId, "handoff_request",
            "Player handoff requested", Map.of("source", sourceShard, "target", targetShard));
        
        // Validate both shards exist
        if (!server.getRegisteredShards().containsKey(sourceShard) ||
            !server.getRegisteredShards().containsKey(targetShard)) {
            
            TransferResponse response = TransferResponse.newBuilder()
                .setAccepted(false)
                .setMessage("Source or target shard not found")
                .build();
            
            server.getMetrics().recordPlayerHandoff(sourceShard, targetShard, 
                System.currentTimeMillis() - startTime, false);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }
        
        // Approve handoff
        TransferResponse response = TransferResponse.newBuilder()
            .setAccepted(true)
            .setMessage("Transfer approved")
            .build();
        
        server.getMetrics().recordPlayerHandoff(sourceShard, targetShard, 
            System.currentTimeMillis() - startTime, true);
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void confirmPlayerTransfer(TransferConfirmation request,
            StreamObserver<ConfirmationResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        server.getStructuredLogger().logPlayerEvent(request.getPlayerUuid(), "transfer_confirmed",
            "Transfer confirmed", Map.of("success", String.valueOf(request.getSuccess())));
        
        ConfirmationResponse response = ConfirmationResponse.newBuilder()
            .setAcknowledged(true)
            .build();
        
        server.getMetrics().recordGrpcCall("ConfirmPlayerTransfer", "success", 
            System.currentTimeMillis() - startTime);
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void requestChunkLoad(ChunkLoadRequest request,
            StreamObserver<ChunkLoadResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        String shardId = request.getShardId();
        ChunkPos pos = request.getChunkPos();
        String key = pos.getX() + "," + pos.getZ();
        
        ProductionCoordinatorServer.ChunkAllocation allocation = server.getChunkAllocations().get(key);
        String ownerShardId = allocation != null ? allocation.getShardId() : shardId;
        
        ChunkLoadResponse response = ChunkLoadResponse.newBuilder()
            .setSuccess(true)
            .setOwnerShardId(ownerShardId)
            .build();
        
        server.getMetrics().recordGrpcCall("RequestChunkLoad", "success", 
            System.currentTimeMillis() - startTime);
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void requestChunkUnload(ChunkUnloadRequest request,
            StreamObserver<ChunkUnloadResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        ChunkUnloadResponse response = ChunkUnloadResponse.newBuilder()
            .setSuccess(true)
            .build();
        
        server.getMetrics().recordGrpcCall("RequestChunkUnload", "success", 
            System.currentTimeMillis() - startTime);
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void syncEntityState(EntityStateSync request, 
            StreamObserver<SyncResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        // Process entity state sync
        int entityCount = request.getEntitiesCount();
        server.getMetrics().incrementCounter("entity.sync", "count", String.valueOf(entityCount));
        
        SyncResponse response = SyncResponse.newBuilder()
            .setSuccess(true)
            .setSyncedCount(entityCount)
            .build();
        
        server.getMetrics().recordGrpcCall("SyncEntityState", "success", 
            System.currentTimeMillis() - startTime);
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}