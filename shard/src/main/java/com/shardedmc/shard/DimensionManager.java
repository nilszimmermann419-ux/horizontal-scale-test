package com.shardedmc.shard;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all dimensions (Overworld, Nether, End) for a shard.
 * Each dimension has its own InstanceContainer with a shared chunk loader.
 */
public class DimensionManager {
    public enum Dimension {
        OVERWORLD("minecraft:overworld", DimensionType.OVERWORLD),
        NETHER("minecraft:the_nether", DimensionType.THE_NETHER),
        END("minecraft:the_end", DimensionType.THE_END);
        
        public final String id;
        public final RegistryKey<DimensionType> registryKey;
        
        Dimension(String id, RegistryKey<DimensionType> registryKey) {
            this.id = id;
            this.registryKey = registryKey;
        }
    }
    
    private final Map<Dimension, InstanceContainer> instances = new ConcurrentHashMap<>();
    private final SharedChunkLoader chunkLoader;
    
    public DimensionManager(SharedChunkLoader chunkLoader) {
        this.chunkLoader = chunkLoader;
    }
    
    public void initializeDimensions() {
        InstanceManager manager = MinecraftServer.getInstanceManager();
        
        for (Dimension dim : Dimension.values()) {
            InstanceContainer instance = manager.createInstanceContainer(dim.registryKey);
            instance.setChunkLoader(chunkLoader);
            instance.setWorldAge(0);
            
            instances.put(dim, instance);
        }
    }
    
    public InstanceContainer getInstance(Dimension dimension) {
        return instances.get(dimension);
    }
    
    public InstanceContainer getDefaultInstance() {
        return instances.get(Dimension.OVERWORLD);
    }
    
    public Dimension getDimensionForInstance(InstanceContainer instance) {
        for (Map.Entry<Dimension, InstanceContainer> entry : instances.entrySet()) {
            if (entry.getValue() == instance) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    public Dimension getDimensionByName(String name) {
        for (Dimension dim : Dimension.values()) {
            if (dim.id.equals(name) || dim.name().toLowerCase().equals(name.toLowerCase())) {
                return dim;
            }
        }
        return null;
    }
}
