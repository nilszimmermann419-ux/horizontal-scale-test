package com.shardedmc.shard.api;

import com.shardedmc.api.*;
import com.shardedmc.plugin.ShardedPluginManager;
import com.shardedmc.shard.ShardCoordinatorClient;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

public class ShardedPluginContextImpl implements ShardedPluginContext {
    
    private final ShardedWorld world;
    private final ShardedScheduler scheduler;
    private final Logger logger;
    private final Path dataDirectory;
    private final ShardedPluginManager pluginManager;
    
    public ShardedPluginContextImpl(Instance instance, ShardCoordinatorClient coordinatorClient,
                                     String shardId, Path dataDirectory, ShardedPluginManager pluginManager) {
        this.world = new ShardedWorldImpl(instance, coordinatorClient, shardId);
        this.scheduler = new ShardedSchedulerImpl();
        this.logger = LoggerFactory.getLogger("Plugin");
        this.dataDirectory = dataDirectory;
        this.pluginManager = pluginManager;
    }
    
    @Override
    public ShardedWorld getWorld() {
        return world;
    }
    
    @Override
    public ShardedScheduler getScheduler() {
        return scheduler;
    }
    
    @Override
    public Logger getLogger() {
        return logger;
    }
    
    @Override
    public Path getDataDirectory() {
        return dataDirectory;
    }
    
    @Override
    public <T> T getConfig(Class<T> configClass) {
        throw new UnsupportedOperationException("Config not yet implemented");
    }
    
    @Override
    public void saveConfig(Object config) {
        throw new UnsupportedOperationException("Config not yet implemented");
    }
    
    @Override
    public Optional<ShardedPlugin> getPlugin(String name) {
        return pluginManager.getPlugin(name);
    }
    
    @Override
    public void registerService(Class<?> serviceClass, Object service) {
        pluginManager.registerService(serviceClass, service);
    }
    
    @Override
    public <T> Optional<T> getService(Class<T> serviceClass) {
        return pluginManager.getService(serviceClass);
    }
}
