package com.shardedmc.shard;

import com.shardedmc.proto.*;
import com.shardedmc.shared.PlayerState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ShardCoordinatorClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ShardCoordinatorClient.class);
    
    private final ManagedChannel channel;
    private final CoordinatorServiceGrpc.CoordinatorServiceFutureStub futureStub;
    private final CoordinatorServiceGrpc.CoordinatorServiceBlockingStub blockingStub;
    
    public ShardCoordinatorClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxRetryAttempts(3)
                .build();
        this.futureStub = CoordinatorServiceGrpc.newFutureStub(channel);
        this.blockingStub = CoordinatorServiceGrpc.newBlockingStub(channel);
    }
    
    public CompletableFuture<RegistrationResponse> registerShard(String shardId, String address, 
                                                                      int port, int capacity,
                                                                      List<com.shardedmc.shared.ChunkPos> regions) {
        ShardInfo request = ShardInfo.newBuilder()
                .setShardId(shardId)
                .setAddress(address)
                .setPort(port)
                .setCapacity(capacity)
                .addAllRegions(regions.stream()
                        .map(r -> ChunkPos.newBuilder().setX(r.x()).setZ(r.z()).build())
                        .collect(Collectors.toList()))
                .build();
        
        return CompletableFuture.supplyAsync(() -> blockingStub.registerShard(request));
    }
    
    public CompletableFuture<HeartbeatResponse> sendHeartbeat(String shardId, double load, int playerCount,
                                                                   List<com.shardedmc.shared.ChunkPos> regions) {
        HeartbeatRequest request = HeartbeatRequest.newBuilder()
                .setShardId(shardId)
                .setLoad(load)
                .setPlayerCount(playerCount)
                .addAllRegions(regions.stream()
                        .map(r -> ChunkPos.newBuilder().setX(r.x()).setZ(r.z()).build())
                        .collect(Collectors.toList()))
                .build();
        
        return CompletableFuture.supplyAsync(() -> blockingStub.sendHeartbeat(request));
    }
    
    public CompletableFuture<TransferResponse> requestPlayerTransfer(String playerUuid, String sourceShardId,
                                                                         String targetShardId, PlayerState playerState) {
        TransferRequest request = TransferRequest.newBuilder()
                .setPlayerUuid(playerUuid)
                .setSourceShardId(sourceShardId)
                .setTargetShardId(targetShardId)
                .setPlayerState(convertPlayerState(playerState))
                .build();
        
        return CompletableFuture.supplyAsync(() -> blockingStub.requestPlayerTransfer(request));
    }
    
    public CompletableFuture<ConfirmationResponse> confirmPlayerTransfer(String playerUuid, String sourceShardId,
                                                                              String targetShardId, boolean success) {
        TransferConfirmation request = TransferConfirmation.newBuilder()
                .setPlayerUuid(playerUuid)
                .setSourceShardId(sourceShardId)
                .setTargetShardId(targetShardId)
                .setSuccess(success)
                .build();
        
        return CompletableFuture.supplyAsync(() -> blockingStub.confirmPlayerTransfer(request));
    }
    
    public CompletableFuture<ChunkLoadResponse> requestChunkLoad(String shardId, int chunkX, int chunkZ) {
        ChunkLoadRequest request = ChunkLoadRequest.newBuilder()
                .setShardId(shardId)
                .setChunkPos(ChunkPos.newBuilder().setX(chunkX).setZ(chunkZ).build())
                .build();
        
        return CompletableFuture.supplyAsync(() -> blockingStub.requestChunkLoad(request));
    }
    
    private com.shardedmc.proto.PlayerState convertPlayerState(PlayerState state) {
        return com.shardedmc.proto.PlayerState.newBuilder()
                .setUuid(state.uuid().toString())
                .setUsername(state.username())
                .setPosition(Vec3d.newBuilder()
                        .setX(state.position().x())
                        .setY(state.position().y())
                        .setZ(state.position().z())
                        .build())
                .setVelocity(Vec3d.newBuilder()
                        .setX(state.velocity().x())
                        .setY(state.velocity().y())
                        .setZ(state.velocity().z())
                        .build())
                .setHealth(state.health())
                .setInventory(com.google.protobuf.ByteString.copyFrom(state.inventory()))
                .setGameMode(state.gameMode())
                .setMetadata(com.google.protobuf.ByteString.copyFrom(state.metadata()))
                .build();
    }
    
    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            channel.shutdownNow();
        }
    }
}
