package com.shardedmc.api;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

public interface ShardedPluginContext {
    
    ShardedWorld getWorld();
    
    ShardedScheduler getScheduler();
    
    Logger getLogger();
    
    Path getDataDirectory();
    
    <T> T getConfig(Class<T> configClass);
    
    void saveConfig(Object config);
    
    Optional<ShardedPlugin> getPlugin(String name);
    
    void registerService(Class<?> serviceClass, Object service);
    
    <T> Optional<T> getService(Class<T> serviceClass);
}
