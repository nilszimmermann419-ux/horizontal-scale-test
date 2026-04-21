package com.shardedmc.coordinator;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TCP passthrough proxy for Minecraft connections.
 * Buffers client data until backend connection is established.
 */
public class MinecraftProxy {
    private static final Logger logger = LoggerFactory.getLogger(MinecraftProxy.class);
    
    private final int port;
    private final ShardRegistry shardRegistry;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    public MinecraftProxy(int port, ShardRegistry shardRegistry) {
        this.port = port;
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
                        protected void initChannel(SocketChannel clientChannel) {
                            logger.debug("New client connection from {}", clientChannel.remoteAddress());
                            
                            // Get target shard
                            ShardRegistry.ShardInfo targetShard = getTargetShard();
                            
                            if (targetShard == null) {
                                logger.error("No available shards for connection from {}",
                                        clientChannel.remoteAddress());
                                clientChannel.close();
                                return;
                            }
                            
                            logger.info("Routing connection from {} to shard {} at {}:{}",
                                    clientChannel.remoteAddress(), targetShard.shardId(),
                                    targetShard.address(), targetShard.port());
                            
                            // Buffer for client data received before backend is ready
                            List<ByteBuf> pendingWrites = new ArrayList<>();
                            
                            // Create temp handler instance
                            ChannelInboundHandlerAdapter tempHandler = new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof ByteBuf) {
                                        pendingWrites.add(((ByteBuf) msg).retain());
                                    }
                                }
                            };
                            
                            // Add temp handler to buffer incoming data
                            clientChannel.pipeline().addLast("tempHandler", tempHandler);
                            
                            // Connect to backend
                            Bootstrap bootstrap = new Bootstrap()
                                    .group(workerGroup)
                                    .channel(NioSocketChannel.class)
                                    .option(ChannelOption.TCP_NODELAY, true)
                                    .handler(new ChannelInitializer<SocketChannel>() {
                                        @Override
                                        protected void initChannel(SocketChannel backendChannel) {
                                            logger.debug("Backend connected to {}:{}",
                                                    targetShard.address(), targetShard.port());
                                            
                                            // Flush pending writes
                                            for (ByteBuf buf : pendingWrites) {
                                                backendChannel.writeAndFlush(buf).addListener(f -> {
                                                    if (!f.isSuccess()) {
                                                        ReferenceCountUtil.release(buf);
                                                    }
                                                });
                                            }
                                            pendingWrites.clear();
                                            
                                            // Remove temp handler and add real forwarding
                                            clientChannel.pipeline().remove("tempHandler");
                                            
                                            // Client -> Backend
                                            clientChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                                @Override
                                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                                    if (backendChannel.isActive()) {
                                                        backendChannel.writeAndFlush(msg);
                                                    } else {
                                                        if (msg instanceof ByteBuf) {
                                                            ((ByteBuf) msg).release();
                                                        }
                                                    }
                                                }
                                                
                                                @Override
                                                public void channelInactive(ChannelHandlerContext ctx) {
                                                    if (backendChannel.isActive()) {
                                                        backendChannel.close();
                                                    }
                                                }
                                            });
                                            
                                            // Backend -> Client
                                            backendChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                                @Override
                                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                                    if (clientChannel.isActive()) {
                                                        clientChannel.writeAndFlush(msg);
                                                    } else {
                                                        if (msg instanceof ByteBuf) {
                                                            ((ByteBuf) msg).release();
                                                        }
                                                    }
                                                }
                                                
                                                @Override
                                                public void channelInactive(ChannelHandlerContext ctx) {
                                                    if (clientChannel.isActive()) {
                                                        clientChannel.close();
                                                    }
                                                }
                                            });
                                        }
                                    });
                            
                            bootstrap.connect(targetShard.address(), targetShard.port())
                                    .addListener((ChannelFutureListener) future -> {
                                        if (!future.isSuccess()) {
                                            logger.error("Failed to connect to backend {}:{}",
                                                    targetShard.address(), targetShard.port(),
                                                    future.cause());
                                            // Release pending writes
                                            for (ByteBuf buf : pendingWrites) {
                                                buf.release();
                                            }
                                            clientChannel.close();
                                        }
                                    });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            
            ChannelFuture f = b.bind(port).sync();
            logger.info("Minecraft proxy started on port {}", port);
            
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
        logger.info("Minecraft proxy stopped");
    }
    
    private ShardRegistry.ShardInfo getTargetShard() {
        List<ShardRegistry.ShardInfo> healthyShards = shardRegistry.getHealthyShards();
        
        if (healthyShards.isEmpty()) {
            return null;
        }
        
        return healthyShards.stream()
                .min((a, b) -> Integer.compare(a.playerCount(), b.playerCount()))
                .orElse(healthyShards.get(0));
    }
}
