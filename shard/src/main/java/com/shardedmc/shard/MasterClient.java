package com.shardedmc.shard;

import com.shardedmc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Client for shards to communicate with the Master Server.
 * Handles chunk ownership requests, block change submissions, and player transfers.
 */
public class MasterClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MasterClient.class);
    
    private final ManagedChannel channel;
    private final CoordinatorServiceGrpc.CoordinatorServiceStub asyncStub;
    private final CoordinatorServiceGrpc.CoordinatorServiceBlockingStub blockingStub;
    
    public MasterClient(String masterAddress) {
        String[] parts = masterAddress.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 50051;
        
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxRetryAttempts(3)
                .build();
        this.asyncStub = CoordinatorServiceGrpc.newStub(channel);
        this.blockingStub = CoordinatorServiceGrpc.newBlockingStub(channel);
    }
    
    /**
     * Request ownership of a chunk from the master.
     */
    public CompletableFuture<Boolean> requestChunkOwnership(String shardId, int chunkX, int chunkZ) {
        ChunkLoadRequest request = ChunkLoadRequest.newBuilder()
                .setShardId(shardId)
                .setChunkPos(ChunkPos.newBuilder().setX(chunkX).setZ(chunkZ).build())
                .build();
        
        return CompletableFuture.supplyAsync(() -> {
            ChunkLoadResponse response = blockingStub.requestChunkLoad(request);
            return response.getSuccess() && response.getOwnerShardId().equals(shardId);
        });
    }
    
    /**
     * Request a lock on a chunk before modifying it.
     */
    public CompletableFuture<Boolean> requestChunkLock(String shardId, int chunkX, int chunkZ) {
        // This would use a custom lock request message
        // For now, assume ownership implies lock
        return requestChunkOwnership(shardId, chunkX, chunkZ);
    }
    
    /**
     * Submit a block change to the master for approval and broadcasting.
     */
    public void submitBlockChange(String shardId, int x, int y, int z, Block block) {
        // This would use a custom block change message
        // For now, log it
        logger.debug("Shard {} submitted block change at {}, {}, {} to {}", 
                shardId, x, y, z, block.name());
    }
    
    /**
     * Request player transfer to another shard.
     */
    public CompletableFuture<Boolean> requestPlayerTransfer(String playerUuid, 
                                                              String sourceShard, 
                                                              String targetShard) {
        TransferRequest request = TransferRequest.newBuilder()
                .setPlayerUuid(playerUuid)
                .setSourceShardId(sourceShard)
                .setTargetShardId(targetShard)
                .build();
        
        return CompletableFuture.supplyAsync(() -> {
            TransferResponse response = blockingStub.requestPlayerTransfer(request);
            return response.getAccepted();
        });
    }
    
    /**
     * Register shard with master.
     */
    public CompletableFuture<Boolean> registerShard(String shardId, String address, int port, int capacity) {
        ShardInfo request = ShardInfo.newBuilder()
                .setShardId(shardId)
                .setAddress(address)
                .setPort(port)
                .setCapacity(capacity)
                .build();
        
        return CompletableFuture.supplyAsync(() -> {
            RegistrationResponse response = blockingStub.registerShard(request);
            return response.getSuccess();
        });
    }
    
    /**
     * Send heartbeat to master.
     */
    public void sendHeartbeat(String shardId, double load, int playerCount) {
        HeartbeatRequest request = HeartbeatRequest.newBuilder()
                .setShardId(shardId)
                .setLoad(load)
                .setPlayerCount(playerCount)
                .build();
        
        blockingStub.sendHeartbeat(request);
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
