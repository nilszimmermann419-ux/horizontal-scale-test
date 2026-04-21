package com.shardedmc.plugin;

import com.shardedmc.api.ShardedPlugin;
import com.shardedmc.api.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ShardedPluginManager {
    private static final Logger logger = LoggerFactory.getLogger(ShardedPluginManager.class);
    
    private final Map<String, LoadedPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, Object> services = new ConcurrentHashMap<>();
    private final ClassLoader parentClassLoader;
    
    public ShardedPluginManager(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }
    
    public CompletableFuture<ShardedPlugin> loadPlugin(Path jarPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Loading plugin from: {}", jarPath);
                
                PluginClassLoader classLoader = new PluginClassLoader(jarPath, parentClassLoader);
                
                // Find plugin main class
                String mainClass = findMainClass(classLoader);
                if (mainClass == null) {
                    throw new IllegalArgumentException("No plugin main class found in " + jarPath);
                }
                
                Class<?> clazz = classLoader.loadClass(mainClass);
                if (!ShardedPlugin.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("Main class does not implement ShardedPlugin: " + mainClass);
                }
                
                ShardedPlugin plugin = (ShardedPlugin) clazz.getDeclaredConstructor().newInstance();
                PluginInfo info = plugin.getInfo();
                
                if (plugins.containsKey(info.name())) {
                    throw new IllegalStateException("Plugin " + info.name() + " is already loaded");
                }
                
                plugins.put(info.name(), new LoadedPlugin(plugin, classLoader, info));
                logger.info("Loaded plugin: {} v{}", info.name(), info.version());
                
                return plugin;
            } catch (Exception e) {
                logger.error("Failed to load plugin from: {}", jarPath, e);
                throw new RuntimeException("Failed to load plugin", e);
            }
        });
    }
    
    private String findMainClass(PluginClassLoader classLoader) {
        try (JarFile jarFile = classLoader.getJarFile()) {
            JarEntry entry = jarFile.getJarEntry("plugin.yml");
            if (entry != null) {
                var properties = new Properties();
                properties.load(jarFile.getInputStream(entry));
                return properties.getProperty("main");
            }
            
            // Fallback: scan for ShardedPlugin implementation
            return jarFile.stream()
                    .filter(e -> e.getName().endsWith(".class"))
                    .map(e -> e.getName().replace('/', '.').replace(".class", ""))
                    .filter(className -> {
                        try {
                            Class<?> clazz = classLoader.loadClass(className);
                            return ShardedPlugin.class.isAssignableFrom(clazz) && !clazz.isInterface();
                        } catch (ClassNotFoundException e) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            logger.error("Failed to find main class", e);
            return null;
        }
    }
    
    public CompletableFuture<Void> enablePlugin(String name) {
        return CompletableFuture.runAsync(() -> {
            LoadedPlugin loaded = plugins.get(name);
            if (loaded == null) {
                throw new IllegalArgumentException("Plugin not found: " + name);
            }
            
            logger.info("Enabling plugin: {}", name);
            // Plugin context will be provided by the shard
            // loaded.plugin().onEnable(context);
        });
    }
    
    public CompletableFuture<Void> disablePlugin(String name) {
        return CompletableFuture.runAsync(() -> {
            LoadedPlugin loaded = plugins.remove(name);
            if (loaded != null) {
                logger.info("Disabling plugin: {}", name);
                try {
                    loaded.plugin().onDisable();
                    loaded.classLoader().close();
                } catch (Exception e) {
                    logger.error("Error disabling plugin: {}", name, e);
                }
            }
        });
    }
    
    public List<ShardedPlugin> getLoadedPlugins() {
        return plugins.values().stream()
                .map(LoadedPlugin::plugin)
                .toList();
    }
    
    public Optional<ShardedPlugin> getPlugin(String name) {
        return Optional.ofNullable(plugins.get(name)).map(LoadedPlugin::plugin);
    }
    
    public void registerService(Class<?> serviceClass, Object service) {
        services.put(serviceClass.getName(), service);
    }
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getService(Class<T> serviceClass) {
        return Optional.ofNullable((T) services.get(serviceClass.getName()));
    }
    
    public CompletableFuture<Void> disableAll() {
        List<CompletableFuture<Void>> futures = plugins.keySet().stream()
                .map(this::disablePlugin)
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    public List<ShardedPlugin> loadPlugins(File pluginsDir) {
        List<ShardedPlugin> loaded = new ArrayList<>();
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return loaded;
        }
        File[] jars = pluginsDir.listFiles(f -> f.getName().endsWith(".jar"));
        if (jars == null) return loaded;
        for (File jar : jars) {
            try {
                ShardedPlugin plugin = loadPlugin(jar.toPath()).join();
                loaded.add(plugin);
            } catch (Exception e) {
                logger.error("Failed to load plugin from: {}", jar, e);
            }
        }
        return loaded;
    }
    
    private record LoadedPlugin(ShardedPlugin plugin, PluginClassLoader classLoader, PluginInfo info) {}
}
