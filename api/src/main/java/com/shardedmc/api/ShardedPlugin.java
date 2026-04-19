package com.shardedmc.api;

public interface ShardedPlugin {
    
    void onEnable(ShardedPluginContext context);
    
    void onDisable();
    
    default PluginInfo getInfo() {
        return new PluginInfo("unknown", "1.0.0", "Unknown");
    }
}
