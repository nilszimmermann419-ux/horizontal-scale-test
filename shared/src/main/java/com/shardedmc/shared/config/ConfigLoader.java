package com.shardedmc.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigLoader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private final Path configDirectory;
    
    public ConfigLoader(String configDir) {
        this.configDirectory = Paths.get(configDir);
        ensureDirectoryExists();
    }
    
    public ConfigLoader() {
        this("config");
    }
    
    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(configDirectory)) {
                Files.createDirectories(configDirectory);
                LOGGER.info("Created config directory: {}", configDirectory);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory: {}", configDirectory, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> T load(String configName, Class<T> clazz) {
        return (T) configCache.computeIfAbsent(configName, k -> doLoad(k, clazz));
    }
    
    @SuppressWarnings("unchecked")
    private <T> T doLoad(String configName, Class<T> clazz) {
        // Try environment-specific config first
        String env = System.getenv("ENVIRONMENT");
        if (env != null) {
            T envConfig = loadFromFile(configName + "-" + env, clazz);
            if (envConfig != null) {
                LOGGER.info("Loaded {} config for environment: {}", configName, env);
                return envConfig;
            }
        }
        
        // Try default config
        T defaultConfig = loadFromFile(configName, clazz);
        if (defaultConfig != null) {
            return defaultConfig;
        }
        
        // Return default instance
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LOGGER.error("Failed to create default instance of {}", clazz.getName(), e);
            return null;
        }
    }
    
    private <T> T loadFromFile(String configName, Class<T> clazz) {
        // Try YAML first
        Path yamlPath = configDirectory.resolve(configName + ".yml");
        if (!Files.exists(yamlPath)) {
            yamlPath = configDirectory.resolve(configName + ".yaml");
        }
        
        if (Files.exists(yamlPath)) {
            try (InputStream is = Files.newInputStream(yamlPath)) {
                return YAML_MAPPER.readValue(is, clazz);
            } catch (IOException e) {
                LOGGER.error("Failed to load YAML config: {}", yamlPath, e);
            }
        }
        
        // Try JSON
        Path jsonPath = configDirectory.resolve(configName + ".json");
        if (Files.exists(jsonPath)) {
            try (InputStream is = Files.newInputStream(jsonPath)) {
                return JSON_MAPPER.readValue(is, clazz);
            } catch (IOException e) {
                LOGGER.error("Failed to load JSON config: {}", jsonPath, e);
            }
        }
        
        // Try from classpath
        T classpathConfig = loadFromClasspath(configName, clazz);
        if (classpathConfig != null) {
            return classpathConfig;
        }
        
        return null;
    }
    
    private <T> T loadFromClasspath(String configName, Class<T> clazz) {
        String[] extensions = {".yml", ".yaml", ".json"};
        
        for (String ext : extensions) {
            String resourcePath = "config/" + configName + ext;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    if (ext.equals(".json")) {
                        return JSON_MAPPER.readValue(is, clazz);
                    } else {
                        return YAML_MAPPER.readValue(is, clazz);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load classpath config: {}", resourcePath, e);
            }
        }
        
        return null;
    }
    
    public void save(String configName, Object config) {
        Path configPath = configDirectory.resolve(configName + ".yml");
        try {
            YAML_MAPPER.writeValue(configPath.toFile(), config);
            configCache.put(configName, config);
            LOGGER.info("Saved config: {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save config: {}", configPath, e);
        }
    }
    
    public void invalidateCache(String configName) {
        configCache.remove(configName);
        LOGGER.debug("Invalidated config cache: {}", configName);
    }
    
    public void invalidateAll() {
        configCache.clear();
        LOGGER.info("Invalidated all config caches");
    }
}