package com.shardedmc.shared;

public final class RedisSchema {
    
    private RedisSchema() {}
    
    public static String shardKey(String shardId) {
        return "shard:" + shardId;
    }
    
    public static String chunkKey(int x, int z) {
        return String.format("chunk:%d:%d", x, z);
    }
    
    public static String playerKey(String uuid) {
        return "player:" + uuid;
    }
    
    public static String entityKey(String uuid) {
        return "entity:" + uuid;
    }
    
    public static String playerPosKey(String uuid) {
        return "player:" + uuid + ":pos";
    }
    
    public static String chunkLockKey(int x, int z) {
        return String.format("chunk_lock:%d:%d", x, z);
    }
    
    // Pub/Sub channels
    public static final String SHARD_EVENTS_CHANNEL = "shard-events";
    public static final String PLAYER_TRANSFERS_CHANNEL = "player-transfers";
    public static final String CHUNK_UPDATES_CHANNEL = "chunk-updates";
    public static final String GLOBAL_EVENTS_CHANNEL = "global-events";
}
