package com.shardedmc.coordinator;

import com.shardedmc.proto.CoordinatorServiceGrpc;
import com.shardedmc.proto.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class CoordinatorServiceImpl extends CoordinatorServiceGrpc.CoordinatorServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorServiceImpl.class);
    
    private final ShardRegistry shardRegistry;
    private final ChunkManager chunkManager;
    private final PlayerRoutingService playerRouting;
    
    public CoordinatorServiceImpl(ShardRegistry shardRegistry, ChunkManager chunkManager, 
                                   PlayerRoutingService playerRouting) {
        this.shardRegistry = shardRegistry;
        this.chunkManager = chunkManager;
        this.playerRouting = playerRouting;
    }
    
    @Override
    public void registerShard(ShardInfo request, StreamObserver<RegistrationResponse> responseObserver) {
        List<com.shardedmc.shared.ChunkPos> regions = request.getRegionsList().stream()
                .map(r -> new com.shardedmc.shared.ChunkPos(r.getX(), r.getZ()))
                .collect(Collectors.toList());
        
        shardRegistry.registerShard(request.getShardId(), request.getAddress(), 
                        request.getPort(), request.getCapacity())
                .thenAccept(v -> {
                    List<com.shardedmc.shared.ChunkPos> assigned = chunkManager.allocateRegionsForShard(
                            request.getShardId(), 4); // Allocate 4 regions initially
                    
                    RegistrationResponse response = RegistrationResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("Shard registered successfully")
                            .addAllAssignedRegions(assigned.stream()
                                    .map(r -> ChunkPos.newBuilder().setX(r.x()).setZ(r.z()).build())
                                    .collect(Collectors.toList()))
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                })
                .exceptionally(ex -> {
                    logger.error("Failed to register shard", ex);
                    responseObserver.onError(ex);
                    return null;
                });
    }
    
    @Override
    public void sendHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        List<com.shardedmc.shared.ChunkPos> regions = request.getRegionsList().stream()
                .map(r -> new com.shardedmc.shared.ChunkPos(r.getX(), r.getZ()))
                .collect(Collectors.toList());
        
        shardRegistry.updateHeartbeat(request.getShardId(), request.getLoad(), 
                        request.getPlayerCount(), regions)
                .thenAccept(v -> {
                    HeartbeatResponse response = HeartbeatResponse.newBuilder()
                            .setHealthy(true)
                            .setShouldShutdown(false)
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                })
                .exceptionally(ex -> {
                    logger.error("Heartbeat error", ex);
                    responseObserver.onError(ex);
                    return null;
                });
    }
    
    @Override
    public void requestPlayerTransfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        logger.info("Player transfer request: {} from {} to {}", 
                request.getPlayerUuid(), request.getSourceShardId(), request.getTargetShardId());
        
        // Verify target shard exists and has capacity
        var targetShard = shardRegistry.getShard(request.getTargetShardId());
        if (targetShard.isEmpty() || !targetShard.get().hasCapacity()) {
            TransferResponse response = TransferResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage("Target shard unavailable or full")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }
        
        TransferResponse response = TransferResponse.newBuilder()
                .setAccepted(true)
                .setMessage("Transfer approved")
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void confirmPlayerTransfer(TransferConfirmation request, StreamObserver<ConfirmationResponse> responseObserver) {
        if (request.getSuccess()) {
            playerRouting.updatePlayerLocation(request.getPlayerUuid(), 
                            request.getTargetShardId(), new com.shardedmc.shared.ChunkPos(0, 0))
                    .thenAccept(v -> {
                        ConfirmationResponse response = ConfirmationResponse.newBuilder()
                                .setAcknowledged(true)
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    })
                    .exceptionally(ex -> {
                        logger.error("Failed to confirm player transfer", ex);
                        responseObserver.onError(ex);
                        return null;
                    });
        } else {
            ConfirmationResponse response = ConfirmationResponse.newBuilder()
                    .setAcknowledged(true)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void requestChunkLoad(ChunkLoadRequest request, StreamObserver<ChunkLoadResponse> responseObserver) {
        chunkManager.getShardForChunk(request.getChunkPos().getX(), request.getChunkPos().getZ())
                .thenAccept(ownerOpt -> {
                    ChunkLoadResponse response = ChunkLoadResponse.newBuilder()
                            .setSuccess(ownerOpt.isPresent())
                            .setOwnerShardId(ownerOpt.orElse(""))
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                })
                .exceptionally(ex -> {
                    logger.error("Failed to get chunk owner", ex);
                    responseObserver.onError(ex);
                    return null;
                });
    }
    
    @Override
    public void requestChunkUnload(ChunkUnloadRequest request, StreamObserver<ChunkUnloadResponse> responseObserver) {
        ChunkUnloadResponse response = ChunkUnloadResponse.newBuilder()
                .setSuccess(true)
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void syncEntityState(EntityStateSync request, StreamObserver<SyncResponse> responseObserver) {
        SyncResponse response = SyncResponse.newBuilder()
                .setSuccess(true)
                .setSyncedCount(request.getEntitiesCount())
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
