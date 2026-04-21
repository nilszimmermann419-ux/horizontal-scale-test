package com.shardedmc.coordinator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages proxying data between a Minecraft client and a shard server.
 */
public class ProxyConnection {
    private static final Logger logger = LoggerFactory.getLogger(ProxyConnection.class);
    
    private final String playerName;
    private final Socket clientSocket;
    private final Socket shardSocket;
    private final LoadBalancer loadBalancer;
    
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 4, r -> {
        Thread t = new Thread(r, "proxy-worker");
        t.setDaemon(true);
        return t;
    });
    
    private volatile boolean running = false;
    
    public ProxyConnection(String playerName, Socket clientSocket, Socket shardSocket, LoadBalancer loadBalancer) {
        this.playerName = playerName;
        this.clientSocket = clientSocket;
        this.shardSocket = shardSocket;
        this.loadBalancer = loadBalancer;
    }
    
    public void start() {
        running = true;
        
        // Client -> Shard
        SHARED_EXECUTOR.submit(() -> pipeData(clientSocket, shardSocket, "client-to-shard"));
        
        // Shard -> Client
        SHARED_EXECUTOR.submit(() -> pipeData(shardSocket, clientSocket, "shard-to-client"));
    }
    
    private void pipeData(Socket from, Socket to, String direction) {
        try {
            InputStream fromIn = from.getInputStream();
            OutputStream toOut = to.getOutputStream();
            
            byte[] buffer = new byte[8192];
            int read;
            
            while (running && (read = fromIn.read(buffer)) != -1) {
                toOut.write(buffer, 0, read);
                toOut.flush();
            }
            
        } catch (IOException e) {
            if (running) {
                logger.debug("Proxy {} connection closed for player {}", direction, playerName);
            }
        } finally {
            stop();
        }
    }
    
    public void stop() {
        running = false;
        
        try {
            clientSocket.close();
        } catch (IOException ignored) {}
        
        try {
            shardSocket.close();
        } catch (IOException ignored) {}
        
        loadBalancer.removeConnection(playerName);
        
        logger.debug("Proxy connection closed for player {}", playerName);
    }
}
