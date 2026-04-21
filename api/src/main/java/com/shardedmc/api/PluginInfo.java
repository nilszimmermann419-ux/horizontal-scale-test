package com.shardedmc.api;

public record PluginInfo(String name, String version, String author) {
    
    public boolean isValid() {
        return name != null && !name.isBlank()
                && version != null && !version.isBlank()
                && author != null && !author.isBlank();
    }
}
