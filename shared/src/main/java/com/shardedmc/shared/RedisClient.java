package com.shardedmc.shared;

import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RedisClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RedisClient.class);
    
    private final io.lettuce.core.RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final RedisAsyncCommands<String, String> asyncCommands;
    private final RedisCommands<String, String> syncCommands;
    
    public RedisClient(String host, int port) {
        this(RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(Duration.ofSeconds(5))
                .build());
    }
    
    public RedisClient(RedisURI uri) {
        this.client = io.lettuce.core.RedisClient.create(uri);
        this.connection = client.connect();
        this.pubSubConnection = client.connectPubSub();
        this.asyncCommands = connection.async();
        this.syncCommands = connection.sync();
        
        logger.info("Connected to Redis at {}:{}", uri.getHost(), uri.getPort());
    }
    
    // Async operations
    public CompletableFuture<Long> hsetAsync(String key, Map<String, String> fieldValues) {
        return asyncCommands.hset(key, fieldValues).toCompletableFuture();
    }
    
    public CompletableFuture<Boolean> hsetAsync(String key, String field, String value) {
        return asyncCommands.hset(key, field, value).toCompletableFuture();
    }
    
    public CompletableFuture<Map<String, String>> hgetallAsync(String key) {
        return asyncCommands.hgetall(key).toCompletableFuture();
    }
    
    public CompletableFuture<String> hgetAsync(String key, String field) {
        return asyncCommands.hget(key, field).toCompletableFuture();
    }
    
    public CompletableFuture<Long> hdelAsync(String key, String... fields) {
        return asyncCommands.hdel(key, fields).toCompletableFuture();
    }
    
    public CompletableFuture<String> setAsync(String key, String value) {
        return asyncCommands.set(key, value).toCompletableFuture();
    }
    
    public CompletableFuture<String> setexAsync(String key, long seconds, String value) {
        return asyncCommands.setex(key, seconds, value).toCompletableFuture();
    }
    
    public CompletableFuture<String> getAsync(String key) {
        return asyncCommands.get(key).toCompletableFuture();
    }
    
    public CompletableFuture<Long> delAsync(String key) {
        return asyncCommands.del(key).toCompletableFuture();
    }
    
    public CompletableFuture<Boolean> existsAsync(String key) {
        return asyncCommands.exists(key).toCompletableFuture().thenApply(count -> count > 0);
    }
    
    // Pub/Sub
    public void subscribe(String channel, Consumer<String> messageHandler) {
        pubSubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String subscribedChannel, String message) {
                if (subscribedChannel.equals(channel)) {
                    messageHandler.accept(message);
                }
            }
        });
        pubSubConnection.sync().subscribe(channel);
    }
    
    public void publish(String channel, String message) {
        syncCommands.publish(channel, message);
    }
    
    public CompletableFuture<Long> publishAsync(String channel, String message) {
        return asyncCommands.publish(channel, message).toCompletableFuture();
    }
    
    // Sync operations (for simple reads)
    public Map<String, String> hgetall(String key) {
        return syncCommands.hgetall(key);
    }
    
    public String hget(String key, String field) {
        return syncCommands.hget(key, field);
    }
    
    // Pipelining for batch operations
    public void pipeline(Consumer<RedisAsyncCommands<String, String>> operations) {
        var commands = connection.async();
        commands.setAutoFlushCommands(false);
        try {
            operations.accept(commands);
            commands.flushCommands();
        } finally {
            commands.setAutoFlushCommands(true);
        }
    }
    
    @Override
    public void close() {
        connection.close();
        pubSubConnection.close();
        client.shutdown();
        logger.info("Redis connection closed");
    }
}
