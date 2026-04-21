package com.shardedmc.shard.player;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PlayerStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStateManager.class);
    private static final Gson GSON = new Gson();
    private static final String PLAYER_STATE_PREFIX = "player:state:";
    private static final long STATE_TTL_SECONDS = 300; // 5 minutes TTL

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> redisConnection;
    private final RedisAsyncCommands<String, String> redisAsync;

    public PlayerStateManager(String redisUrl) {
        this.redisClient = RedisClient.create(redisUrl);
        this.redisConnection = redisClient.connect();
        this.redisAsync = redisConnection.async();
    }

    public CompletableFuture<Void> saveState(Player player) {
        UUID playerUuid = player.getUuid();

        JsonObject state = new JsonObject();
        state.addProperty("uuid", playerUuid.toString());
        state.addProperty("username", player.getUsername());

        // Position
        Pos pos = player.getPosition();
        JsonObject position = new JsonObject();
        position.addProperty("x", pos.x());
        position.addProperty("y", pos.y());
        position.addProperty("z", pos.z());
        position.addProperty("yaw", pos.yaw());
        position.addProperty("pitch", pos.pitch());
        state.add("position", position);

        // Health and food
        state.addProperty("health", player.getHealth());
        state.addProperty("food", player.getFood());
        state.addProperty("saturation", player.getFoodSaturation());
        state.addProperty("gamemode", player.getGameMode().name());

        // Inventory
        JsonArray inventory = serializeInventory(player);
        state.add("inventory", inventory);

        String stateJson = GSON.toJson(state);
        String key = PLAYER_STATE_PREFIX + playerUuid;

        return redisAsync.setex(key, STATE_TTL_SECONDS, stateJson)
                .thenAccept(result -> {
                    LOGGER.info("Saved player state for {} ({} bytes)", player.getUsername(), stateJson.length());
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to save player state for {}", player.getUsername(), throwable);
                    return null;
                });
    }

    public CompletableFuture<PlayerState> loadState(UUID playerUuid) {
        String key = PLAYER_STATE_PREFIX + playerUuid;

        return redisAsync.get(key)
                .thenApply(stateJson -> {
                    if (stateJson == null || stateJson.isEmpty()) {
                        LOGGER.info("No saved state found for player {}", playerUuid);
                        return null;
                    }

                    try {
                        JsonObject state = GSON.fromJson(stateJson, JsonObject.class);
                        PlayerState playerState = deserializeState(state);
                        LOGGER.info("Loaded player state for {}", playerUuid);
                        return playerState;
                    } catch (Exception e) {
                        LOGGER.error("Failed to deserialize player state for {}", playerUuid, e);
                        return null;
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to load player state for {}", playerUuid, throwable);
                    return null;
                });
    }

    public CompletableFuture<Void> transferOut(Player player, String targetShardId) {
        LOGGER.info("Transferring out player {} to shard {}", player.getUsername(), targetShardId);

        // Save current state
        return saveState(player)
                .thenCompose(v -> {
                    // Mark player as transferring
                    String transferKey = "player:transferring:" + player.getUuid();
                    return redisAsync.setex(transferKey, 60, targetShardId);
                })
                .thenAccept(result -> {
                    LOGGER.info("Player {} ready for transfer to shard {}", player.getUsername(), targetShardId);
                });
    }

    public CompletableFuture<PlayerState> transferIn(UUID playerUuid) {
        LOGGER.info("Transferring in player {}", playerUuid);

        // Load state from Redis
        return loadState(playerUuid)
                .thenApply(state -> {
                    if (state == null) {
                        LOGGER.warn("No state found for transferring player {}", playerUuid);
                    }
                    return state;
                });
    }

    public CompletableFuture<Void> deleteState(UUID playerUuid) {
        String key = PLAYER_STATE_PREFIX + playerUuid;
        return redisAsync.del(key)
                .thenAccept(result -> {
                    LOGGER.debug("Deleted player state for {}", playerUuid);
                });
    }

    private JsonArray serializeInventory(Player player) {
        JsonArray inventory = new JsonArray();

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItemStack(slot);
            if (item.isAir()) continue;

            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("slot", slot);
            itemJson.addProperty("material", item.material().name());
            itemJson.addProperty("amount", item.amount());

            // Serialize NBT if present
            if (item.hasNbt()) {
                String nbtBase64 = java.util.Base64.getEncoder().encodeToString(item.getNbt().asBinary());
                itemJson.addProperty("nbt", nbtBase64);
            }

            inventory.add(itemJson);
        }

        return inventory;
    }

    private PlayerState deserializeState(JsonObject state) {
        PlayerState playerState = new PlayerState();

        playerState.uuid = UUID.fromString(state.get("uuid").getAsString());
        playerState.username = state.get("username").getAsString();

        // Position
        JsonObject pos = state.getAsJsonObject("position");
        playerState.position = new Pos(
                pos.get("x").getAsDouble(),
                pos.get("y").getAsDouble(),
                pos.get("z").getAsDouble(),
                pos.get("yaw").getAsFloat(),
                pos.get("pitch").getAsFloat()
        );

        // Stats
        playerState.health = state.get("health").getAsFloat();
        playerState.food = state.get("food").getAsInt();
        playerState.saturation = state.get("saturation").getAsFloat();
        playerState.gamemode = GameMode.valueOf(state.get("gamemode").getAsString());

        // Inventory
        JsonArray inventory = state.getAsJsonArray("inventory");
        playerState.inventory = new ItemStack[36]; // Standard inventory size
        for (int i = 0; i < playerState.inventory.length; i++) {
            playerState.inventory[i] = ItemStack.AIR;
        }

        for (int i = 0; i < inventory.size(); i++) {
            JsonObject itemJson = inventory.get(i).getAsJsonObject();
            int slot = itemJson.get("slot").getAsInt();
            String materialName = itemJson.get("material").getAsString();
            int amount = itemJson.get("amount").getAsInt();

            Material material = Material.fromNamespaceId(materialName);
            if (material != null) {
                ItemStack item = ItemStack.of(material, amount);

                // Deserialize NBT if present
                if (itemJson.has("nbt")) {
                    String nbtBase64 = itemJson.get("nbt").getAsString();
                    byte[] nbtBytes = java.util.Base64.getDecoder().decode(nbtBase64);
                    // Note: Full NBT deserialization would require more complex handling
                    // This is a simplified version
                }

                if (slot >= 0 && slot < playerState.inventory.length) {
                    playerState.inventory[slot] = item;
                }
            }
        }

        return playerState;
    }

    public void shutdown() {
        LOGGER.info("Shutting down player state manager");
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    public static class PlayerState {
        public UUID uuid;
        public String username;
        public Pos position;
        public float health;
        public int food;
        public float saturation;
        public GameMode gamemode;
        public ItemStack[] inventory;
    }
}
