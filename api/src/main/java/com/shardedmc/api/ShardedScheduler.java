package com.shardedmc.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

public interface ShardedScheduler {
    
    ScheduledFuture<?> runTask(Runnable task);
    
    ScheduledFuture<?> runTaskLater(Runnable task, Duration delay);
    
    ScheduledFuture<?> runTaskTimer(Runnable task, Duration delay, Duration period);
    
    CompletableFuture<Void> runAsync(Runnable task);
    
    void shutdown();
}
