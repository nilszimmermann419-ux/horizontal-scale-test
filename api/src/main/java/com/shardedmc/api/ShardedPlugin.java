package com.shardedmc.api;

public interface ShardedPlugin {
    
    PluginInfo DEFAULT_INFO = new PluginInfo("unknown", "1.0.0", "Unknown");
    
    void onEnable(ShardedPluginContext context);
    
    void onDisable();
    
    default PluginInfo getInfo() {
        return DEFAULT_INFO;
    }
}
