package com.shardedmc.api;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

public interface ShardedPluginContext {

    /**
     * @return the world, never null
     */
    ShardedWorld getWorld();

    /**
     * @return the scheduler, never null
     */
    ShardedScheduler getScheduler();

    /**
     * @return the logger, never null
     */
    Logger getLogger();

    /**
     * @return the data directory, never null
     */
    Path getDataDirectory();

    /**
     * @return the config, or null if not loaded
     */
    <T> T getConfig(Class<T> configClass);

    /**
     * @param config the config to save, must not be null
     */
    void saveConfig(Object config);

    /**
     * @return the plugin if found, empty if not found
     */
    Optional<ShardedPlugin> getPlugin(String name);

    /**
     * @param serviceClass the service class, must not be null
     * @param service the service instance, must not be null
     */
    void registerService(Class<?> serviceClass, Object service);

    /**
     * @return the service if registered, empty if not found
     */
    <T> Optional<T> getService(Class<T> serviceClass);
}
