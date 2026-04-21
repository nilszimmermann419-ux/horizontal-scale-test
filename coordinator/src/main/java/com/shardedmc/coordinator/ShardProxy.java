package com.shardedmc.coordinator;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple proxy that routes players to the correct shard.
 * Based on player position, routes to the shard that owns the chunk.
 * 
 * For production, replace with Velocity or implement full proxy protocol.
 */
public class ShardProxy {
    private static final Logger logger = LoggerFactory.getLogger(ShardProxy.class);
    
    private final int port;
    private final PlayerRouter playerRouter;
    private final ShardRegistry shardRegistry;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    public ShardProxy(int port, PlayerRouter playerRouter, ShardRegistry shardRegistry) {
        this.port = port;
        this.playerRouter = playerRouter;
        this.shardRegistry = shardRegistry;
    }
    
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new MinecraftHandshakeHandler()
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            
            ChannelFuture f = b.bind(port).sync();
            logger.info("Shard proxy started on port {}", port);
            
        } catch (InterruptedException e) {
            logger.error("Proxy interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
    
    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("Shard proxy stopped");
    }
    
    /**
     * Route a player to a specific shard.
     */
    public void routePlayer(String playerUuid, String targetShardId) {
        playerShardMap.put(playerUuid, targetShardId);
        logger.info("Player {} routed to shard {}", playerUuid, targetShardId);
    }
    
    /**
     * Get the shard that should handle a player based on chunk coordinates.
     */
    public String getShardForPosition(int chunkX, int chunkZ) {
        return playerRouter.getTargetShard(chunkX, chunkZ);
    }
    
    /**
     * Simple handler that reads Minecraft handshake to determine target shard.
     * In production, use proper Minecraft protocol parsing.
     */
    private class MinecraftHandshakeHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // For now, just route to first available shard
            // In production, parse handshake and route based on player position
            String targetShard = shardRegistry.getHealthyShards().stream()
                    .sorted((a, b) -> Integer.compare(a.playerCount(), b.playerCount()))
                    .findFirst()
                    .map(ShardRegistry.ShardInfo::shardId)
                    .orElse(null);
            
            if (targetShard != null) {
                // Forward connection to target shard
                forwardToShard(ctx, targetShard);
            } else {
                ctx.close();
            }
        }
        
        private void forwardToShard(ChannelHandlerContext clientCtx, String shardId) {
            ShardRegistry.ShardInfo shard = shardRegistry.getShard(shardId).orElse(null);
            if (shard == null) {
                clientCtx.close();
                return;
            }
            
            // Create connection to backend shard
            // This is simplified - in production use proper proxy implementation
            logger.debug("Forwarding connection to shard {} at {}:{}", 
                    shardId, shard.address(), shard.port());
        }
    }
    
}
