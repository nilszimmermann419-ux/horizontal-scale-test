package com.shardedmc.shard.api;

import com.shardedmc.api.ShardedScheduler;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

public class ShardedSchedulerImpl implements ShardedScheduler {
    
    @Override
    public ScheduledFuture<?> runTask(Runnable task) {
        Task minestomTask = MinecraftServer.getSchedulerManager().submitTask(() -> {
            task.run();
            return TaskSchedule.stop();
        });
        
        return new MinestomScheduledFuture(minestomTask);
    }
    
    @Override
    public ScheduledFuture<?> runTaskLater(Runnable task, Duration delay) {
        Task minestomTask = MinecraftServer.getSchedulerManager().buildTask(task)
                .delay(TaskSchedule.duration(delay))
                .schedule();
        
        return new MinestomScheduledFuture(minestomTask);
    }
    
    @Override
    public ScheduledFuture<?> runTaskTimer(Runnable task, Duration delay, Duration period) {
        Task minestomTask = MinecraftServer.getSchedulerManager().buildTask(task)
                .delay(TaskSchedule.duration(delay))
                .repeat(TaskSchedule.duration(period))
                .schedule();
        
        return new MinestomScheduledFuture(minestomTask);
    }
    
    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task);
    }
    
    @Override
    public void shutdown() {
        // Minestom scheduler doesn't need explicit shutdown
    }
    
    private record MinestomScheduledFuture(Task task) implements ScheduledFuture<Void> {
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            task.cancel();
            return true;
        }
        
        @Override
        public boolean isCancelled() {
            return !task.isAlive();
        }
        
        @Override
        public boolean isDone() {
            return !task.isAlive();
        }
        
        @Override
        public Void get() {
            return null;
        }
        
        @Override
        public Void get(long timeout, java.util.concurrent.TimeUnit unit) {
            return null;
        }
        
        @Override
        public long getDelay(java.util.concurrent.TimeUnit unit) {
            return 0;
        }
        
        @Override
        public int compareTo(java.util.concurrent.Delayed o) {
            return Long.compare(getDelay(java.util.concurrent.TimeUnit.NANOSECONDS), 
                    o.getDelay(java.util.concurrent.TimeUnit.NANOSECONDS));
        }
    }
}
