package com.shardedmc.api;

public interface ShardedEventHandler<T extends ShardedEvent> {
    
    Class<T> getEventType();
    
    void handle(T event);
}
