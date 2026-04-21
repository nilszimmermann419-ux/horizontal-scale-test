package com.shardedmc.coordinator;

import com.shardedmc.shared.ChunkPos;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MultiPaper-style Load Balancer and Proxy.
 * Routes players to the correct shard based on their position and chunk ownership.
 * Also acts as a Minecraft proxy for seamless connections.
 */
public class LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);
    
    private final int proxyPort;
    private final ChunkManager chunkManager;
    private final ShardRegistry shardRegistry;
    
    private ServerSocket proxySocket;
    private ExecutorService executorService;
    private boolean running = false;
    
    // Player routing cache: player UUID -> shard address
    private final Map<String, ShardRoute> playerRoutes = new ConcurrentHashMap<>();
    
    // Active connections
    private final Map<String, ProxyConnection> activeConnections = new ConcurrentHashMap<>();
    
    public LoadBalancer(int proxyPort, ChunkManager chunkManager, ShardRegistry shardRegistry) {
        this.proxyPort = proxyPort;
        this.chunkManager = chunkManager;
        this.shardRegistry = shardRegistry;
    }
    
    public void start() {
        running = true;
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, r -> {
            Thread t = new Thread(r, "load-balancer");
            t.setDaemon(true);
            return t;
        });
        
        try {
            proxySocket = new ServerSocket(proxyPort);
            logger.info("Load Balancer proxy started on port {}", proxyPort);
            
            // Accept incoming Minecraft connections
            while (running) {
                try {
                    Socket clientSocket = proxySocket.accept();
                    executorService.submit(() -> handleClientConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        logger.error("Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start proxy", e);
        }
    }
    
    /**
     * Handle a new Minecraft client connection
     */
    private void handleClientConnection(Socket clientSocket) {
        try {
            logger.debug("New connection from {}", clientSocket.getInetAddress());
            
            // Read handshake packet to determine version and target
            InputStream clientIn = clientSocket.getInputStream();
            
            // Read packet length (varint)
            int packetLength = readVarInt(clientIn);
            if (packetLength <= 0) {
                clientSocket.close();
                return;
            }
            
            // Read packet data
            byte[] packetData = new byte[packetLength];
            int totalRead = 0;
            while (totalRead < packetLength) {
                int read = clientIn.read(packetData, totalRead, packetLength - totalRead);
                if (read == -1) {
                    clientSocket.close();
                    return;
                }
                totalRead += read;
            }
            
            // Parse handshake
            ByteBuffer buffer = ByteBuffer.wrap(packetData);
            int packetId = readVarInt(buffer);
            int protocolVersion = readVarInt(buffer);
            String serverAddress = readString(buffer);
            int serverPort = buffer.getShort() & 0xFFFF;
            int nextState = readVarInt(buffer);
            
            logger.debug("Handshake: protocol={}, address={}, port={}, state={}", 
                    protocolVersion, serverAddress, serverPort, nextState);
            
            if (nextState == 1) {
                // Status request - return server list info
                handleStatusRequest(clientSocket, clientIn);
            } else if (nextState == 2) {
                // Login - route to appropriate shard
                handleLoginRequest(clientSocket, clientIn, serverAddress);
            }
            
        } catch (IOException e) {
            logger.error("Error handling client connection", e);
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * Route player to best shard based on their last known position
     */
    public ShardRoute routePlayer(String playerUuid, ChunkPos chunkPos) {
        // Check if already routed
        ShardRoute existing = playerRoutes.get(playerUuid);
        if (existing != null && shardRegistry.isShardHealthy(existing.shardId())) {
            return existing;
        }
        
        // Find shard that owns this chunk
        if (chunkPos != null) {
            String owner = chunkManager.getChunkOwner(chunkPos);
            if (owner != null && shardRegistry.isShardHealthy(owner)) {
                ShardRegistry.ShardInfo shard = shardRegistry.getShard(owner).orElse(null);
                if (shard != null) {
                    ShardRoute route = new ShardRoute(owner, shard.address(), shard.port());
                    playerRoutes.put(playerUuid, route);
                    return route;
                }
            }
        }
        
        // Fallback: route to least loaded shard
        return routeToLeastLoadedShard(playerUuid);
    }
    
    /**
     * Route to least loaded healthy shard
     */
    public ShardRoute routeToLeastLoadedShard(String playerUuid) {
        List<ShardRegistry.ShardInfo> healthyShards = shardRegistry.getHealthyShards();
        if (healthyShards.isEmpty()) {
            return null;
        }
        
        // Find shard with lowest player count
        ShardRegistry.ShardInfo bestShard = healthyShards.stream()
                .min(Comparator.comparingInt(ShardRegistry.ShardInfo::playerCount))
                .orElse(healthyShards.get(0));
        
        ShardRoute route = new ShardRoute(bestShard.shardId(), bestShard.address(), bestShard.port());
        playerRoutes.put(playerUuid, route);
        return route;
    }
    
    /**
     * Handle player transfer between shards
     */
    public ShardRoute transferPlayer(String playerUuid, ChunkPos targetChunk) {
        // Remove old route
        playerRoutes.remove(playerUuid);
        
        // Route to new shard
        return routePlayer(playerUuid, targetChunk);
    }
    
    /**
     * Handle status request (server list ping)
     */
    private void handleStatusRequest(Socket clientSocket, InputStream clientIn) throws IOException {
        // For now, just close the connection
        // In production, return server status JSON
        clientSocket.close();
    }
    
    /**
     * Handle login request - route to shard
     */
    private void handleLoginRequest(Socket clientSocket, InputStream clientIn, String address) throws IOException {
        // Read login start packet
        int packetLength = readVarInt(clientIn);
        if (packetLength <= 0) {
            clientSocket.close();
            return;
        }
        
        byte[] packetData = new byte[packetLength];
        int totalRead = 0;
        while (totalRead < packetLength) {
            int read = clientIn.read(packetData, totalRead, packetLength - totalRead);
            if (read == -1) {
                clientSocket.close();
                return;
            }
            totalRead += read;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        int packetId = readVarInt(buffer);
        
        if (packetId == 0x00) {
            // Login start
            String username = readString(buffer);
            logger.info("Player {} logging in", username);
            
            // Route to best shard
            ShardRoute route = routeToLeastLoadedShard(username);
            if (route == null) {
                logger.error("No available shards for player {}", username);
                clientSocket.close();
                return;
            }
            
            // Validate target shard still exists
            var shardOpt = shardRegistry.getShard(route.shardId());
            if (shardOpt.isEmpty()) {
                logger.error("Target shard {} no longer exists for player {}", route.shardId(), username);
                clientSocket.close();
                return;
            }
            
            // Connect to target shard
            try {
                Socket shardSocket = new Socket();
                shardSocket.connect(new InetSocketAddress(route.address(), route.port()), 5000);
                
                // Forward all traffic between client and shard
                ProxyConnection connection = new ProxyConnection(
                        username, clientSocket, shardSocket, this);
                activeConnections.put(username, connection);
                connection.start();
                
                logger.info("Routed player {} to shard {} at {}:{}", 
                        username, route.shardId(), route.address(), route.port());
                
            } catch (IOException e) {
                logger.error("Failed to connect to shard for player {}", username, e);
                clientSocket.close();
            }
        }
    }
    
    /**
     * Read a Minecraft VarInt from InputStream
     */
    private int readVarInt(InputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = in.read();
            if (b == -1) return -1;
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too big");
            }
        } while ((b & 0x80) != 0);
        return result;
    }
    
    /**
     * Read a Minecraft VarInt from ByteBuffer
     */
    private int readVarInt(ByteBuffer buffer) {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = buffer.get();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }
    
    /**
     * Read a Minecraft string from ByteBuffer
     */
    private String readString(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
    
    public void stop() {
        running = false;
        
        // Close all connections
        activeConnections.values().forEach(ProxyConnection::stop);
        activeConnections.clear();
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        try {
            if (proxySocket != null) {
                proxySocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing proxy socket", e);
        }
        
        logger.info("Load Balancer stopped");
    }
    
    /**
     * Get routing stats
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeConnections", activeConnections.size());
        stats.put("cachedRoutes", playerRoutes.size());
        return stats;
    }
    
    public void removeConnection(String playerUuid) {
        activeConnections.remove(playerUuid);
        playerRoutes.remove(playerUuid);
    }
    
    public record ShardRoute(String shardId, String address, int port) {}
}
