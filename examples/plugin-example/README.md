# ShardedMC Plugin Development

This example demonstrates how to write a plugin for ShardedMC using the Minestom-based shard server.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Event Handlers](#event-handlers)
3. [Custom Commands](#custom-commands)
4. [API Usage](#api-usage)
5. [Complete Example](#complete-example)

## Getting Started

### Plugin Structure

A ShardedMC plugin is a Java module that hooks into the Minestom event system and provides custom functionality:

```
my-plugin/
├── build.gradle
├── src/
│   └── main/
│       └── java/
│           └── com/example/
│               ├── MyPlugin.java
│               ├── commands/
│               │   └── HomeCommand.java
│               └── listeners/
│                   ├── PlayerJoinListener.java
│                   └── BlockBreakListener.java
```

### build.gradle

```gradle
plugins {
    id 'java'
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.Minestom:Minestom:master-SNAPSHOT'
    compileOnly project(':shard')  // ShardedMC shard API
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
```

### Plugin Entry Point

```java
package com.example;

import com.shardedmc.shard.api.Plugin;
import com.shardedmc.shard.api.ShardAPI;
import net.minestom.server.event.GlobalEventHandler;

public class MyPlugin extends Plugin {
    
    @Override
    public void onEnable(ShardAPI api) {
        // Called when the plugin is enabled
        getLogger().info("MyPlugin enabled!");
        
        // Register event listeners
        GlobalEventHandler eventHandler = api.getEventHandler();
        eventHandler.addListener(new PlayerJoinListener(this));
        eventHandler.addListener(new BlockBreakListener(this));
        
        // Register commands
        api.getCommandManager().register(new HomeCommand());
    }
    
    @Override
    public void onDisable() {
        // Called when the plugin is disabled
        getLogger().info("MyPlugin disabled!");
    }
}
```

### plugin.json

Place this in `src/main/resources/plugin.json`:

```json
{
  "name": "MyPlugin",
  "version": "1.0.0",
  "main": "com.example.MyPlugin",
  "authors": ["YourName"],
  "dependencies": []
}
```

## Event Handlers

### Player Events

```java
package com.example.listeners;

import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.EventListener;

public class PlayerJoinListener implements EventListener<PlayerLoginEvent> {
    
    private final MyPlugin plugin;
    
    public PlayerJoinListener(MyPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public Class<PlayerLoginEvent> getEventType() {
        return PlayerLoginEvent.class;
    }
    
    @Override
    public Result run(PlayerLoginEvent event) {
        var player = event.getPlayer();
        
        // Send welcome message
        player.sendMessage("Welcome to the server, " + player.getUsername() + "!");
        
        // Log to plugin logger
        plugin.getLogger().info("Player joined: {} (UUID: {})", 
            player.getUsername(), 
            player.getUuid());
        
        return Result.SUCCESS;
    }
}
```

### Block Events

```java
package com.example.listeners;

import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.EventListener;
import net.minestom.server.instance.block.Block;

public class BlockBreakListener implements EventListener<PlayerBlockBreakEvent> {
    
    private final MyPlugin plugin;
    
    public BlockBreakListener(MyPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public Class<PlayerBlockBreakEvent> getEventType() {
        return PlayerBlockBreakEvent.class;
    }
    
    @Override
    public Result run(PlayerBlockBreakEvent event) {
        var player = event.getPlayer();
        var block = event.getBlock();
        
        // Cancel breaking diamond ore without permission
        if (block == Block.DIAMOND_ORE) {
            if (!player.hasPermission("myplugin.mine.diamond")) {
                player.sendMessage("You don't have permission to mine diamond ore!");
                event.setCancelled(true);
                return Result.SUCCESS;
            }
        }
        
        // Log block breaks
        plugin.getLogger().debug("{} broke {} at {}", 
            player.getUsername(), 
            block.name(),
            event.getBlockPosition());
        
        return Result.SUCCESS;
    }
}
```

### Custom Events

You can also create and fire custom events:

```java
// Define custom event
public class PlayerLevelUpEvent implements Event {
    private final Player player;
    private final int newLevel;
    
    public PlayerLevelUpEvent(Player player, int newLevel) {
        this.player = player;
        this.newLevel = newLevel;
    }
    
    public Player getPlayer() { return player; }
    public int getNewLevel() { return newLevel; }
}

// Fire the event
PlayerLevelUpEvent event = new PlayerLevelUpEvent(player, 10);
EventDispatcher.call(event);

// Listen for the event
eventHandler.addListener(EventListener.builder(PlayerLevelUpEvent.class)
    .handler(e -> {
        e.getPlayer().sendMessage("Congratulations! You reached level " + e.getNewLevel());
    })
    .build());
```

## Custom Commands

### Basic Command

```java
package com.example.commands;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class HomeCommand extends Command {
    
    public HomeCommand() {
        super("home", "h");
        
        // /home - teleport to home
        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command!");
                return;
            }
            
            teleportToHome(player);
        });
        
        // /home set - set home location
        var setArg = ArgumentType.Literal("set");
        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command!");
                return;
            }
            
            setHome(player);
        }, setArg);
        
        // /home <player> - teleport to another player's home (admin)
        var playerArg = ArgumentType.Entity("target").onlyPlayers(true);
        addSyntax((sender, context) -> {
            if (!sender.hasPermission("myplugin.home.others")) {
                sender.sendMessage("You don't have permission!");
                return;
            }
            
            var target = context.get(playerArg);
            if (target instanceof Player targetPlayer) {
                teleportToHome(targetPlayer);
            }
        }, playerArg);
    }
    
    private void teleportToHome(Player player) {
        // In a real plugin, load from persistent storage
        player.sendMessage("Teleporting to home...");
        // player.teleport(homeLocation);
    }
    
    private void setHome(Player player) {
        // In a real plugin, save to persistent storage
        player.sendMessage("Home set!");
    }
}
```

### Command with Arguments

```java
package com.example.commands;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class WarpCommand extends Command {
    
    public WarpCommand() {
        super("warp", "w");
        
        var warpName = ArgumentType.String("name");
        
        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /warp <name>");
        });
        
        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can warp!");
                return;
            }
            
            String name = context.get(warpName);
            warpPlayer(player, name);
        }, warpName);
    }
    
    private void warpPlayer(Player player, String warpName) {
        // Look up warp location from storage
        // player.teleport(warpLocation);
        player.sendMessage("Warped to " + warpName + "!");
    }
}
```

## API Usage

### Shard API

```java
public class MyPlugin extends Plugin {
    
    private ShardAPI api;
    
    @Override
    public void onEnable(ShardAPI api) {
        this.api = api;
        
        // Access the instance (world)
        Instance instance = api.getInstance();
        
        // Access the event bus
        EventBus eventBus = api.getEventBus();
        
        // Publish events to other shards
        eventBus.publish("world.global", new TimeChangeEvent(1000));
        
        // Access player manager
        PlayerManager playerManager = api.getPlayerManager();
        
        // Get online players
        Collection<Player> players = playerManager.getOnlinePlayers();
        
        // Send message to all players
        players.forEach(p -> p.sendMessage("Server-wide announcement!"));
    }
}
```

### Cross-Shard Communication

```java
// Publish an event to all shards
api.getEventBus().publish("world.global", new CustomEvent(data));

// Subscribe to events from other shards
api.getEventBus().subscribe("world.blocks.*", event -> {
    // Handle block changes from other shards
    BlockChangeEvent blockEvent = (BlockChangeEvent) event;
    updateLocalState(blockEvent);
});

// Send a message to a specific shard
api.getCoordinator().sendToShard("shard-2", new RegionTransferRequest(playerUUID));
```

### Player Data

```java
// Get player data from Redis cache
PlayerData data = api.getPlayerData(player.getUuid());

// Update player data
data.setCustomProperty("kills", data.getInt("kills") + 1);
api.savePlayerData(player.getUuid(), data);

// Get chunk owner from coordinator
String shardId = api.getCoordinator().getChunkOwner(chunkX, chunkZ);
```

### Scheduler

```java
// Run task after delay
api.getScheduler().scheduleTask(() -> {
    player.sendMessage("Welcome back!");
}, TaskSchedule.seconds(5), TaskSchedule.stop());

// Run repeating task
api.getScheduler().scheduleTask(() -> {
    // Broadcast server status every minute
    broadcastStatus();
}, TaskSchedule.minutes(1), TaskSchedule.forever());

// Run async task
api.getScheduler().scheduleTask(() -> {
    // Heavy computation that shouldn't block the main thread
    processLargeDataset();
}, TaskSchedule.nextTick(), TaskSchedule.stop());
```

## Complete Example

Here's a complete example of a simple economy plugin:

```java
package com.example.economy;

import com.shardedmc.shard.api.Plugin;
import com.shardedmc.shard.api.ShardAPI;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerBlockBreakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyPlugin extends Plugin {
    
    private ShardAPI api;
    private final Map<UUID, Double> balances = new HashMap<>();
    
    @Override
    public void onEnable(ShardAPI api) {
        this.api = api;
        
        // Register event listeners
        api.getEventHandler().addListener(EventListener.builder(PlayerBlockBreakEvent.class)
            .handler(event -> {
                Player player = event.getPlayer();
                // Give money for mining
                addBalance(player.getUuid(), 1.0);
                player.sendMessage("+$1 for mining! Balance: $" + getBalance(player.getUuid()));
            })
            .build());
        
        // Register commands
        api.getCommandManager().register(new BalanceCommand(this));
        api.getCommandManager().register(new PayCommand(this));
        
        getLogger().info("Economy plugin enabled!");
    }
    
    @Override
    public void onDisable() {
        // Save balances to persistent storage
        getLogger().info("Economy plugin disabled!");
    }
    
    public double getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0.0);
    }
    
    public void addBalance(UUID playerId, double amount) {
        balances.merge(playerId, amount, Double::sum);
    }
    
    public boolean removeBalance(UUID playerId, double amount) {
        double current = getBalance(playerId);
        if (current < amount) return false;
        balances.put(playerId, current - amount);
        return true;
    }
    
    public boolean transfer(UUID from, UUID to, double amount) {
        if (!removeBalance(from, amount)) return false;
        addBalance(to, amount);
        return true;
    }
}

class BalanceCommand extends Command {
    
    public BalanceCommand(EconomyPlugin plugin) {
        super("balance", "bal", "money");
        
        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can check balance!");
                return;
            }
            
            double balance = plugin.getBalance(player.getUuid());
            player.sendMessage("Your balance: $" + String.format("%.2f", balance));
        });
    }
}

class PayCommand extends Command {
    
    public PayCommand(EconomyPlugin plugin) {
        super("pay");
        
        var targetArg = ArgumentType.Entity("target").onlyPlayers(true);
        var amountArg = ArgumentType.Double("amount");
        
        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /pay <player> <amount>");
        });
        
        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can pay!");
                return;
            }
            
            Player target = context.get(targetArg);
            double amount = context.get(amountArg);
            
            if (amount <= 0) {
                player.sendMessage("Amount must be positive!");
                return;
            }
            
            if (plugin.transfer(player.getUuid(), target.getUuid(), amount)) {
                player.sendMessage("Paid $" + amount + " to " + target.getUsername());
                target.sendMessage("Received $" + amount + " from " + player.getUsername());
            } else {
                player.sendMessage("Insufficient funds!");
            }
        }, targetArg, amountArg);
    }
}
```

## Building and Deploying

### Build Plugin

```bash
./gradlew build
```

### Deploy Plugin

Copy the built JAR to the shard's plugins directory:

```bash
cp build/libs/my-plugin-1.0.0.jar /path/to/shard/plugins/
```

Or in Docker:

```dockerfile
FROM shardedmc/shard:latest
COPY build/libs/my-plugin-1.0.0.jar /app/plugins/
```

### Hot Reload

ShardedMC supports hot-reloading plugins in development:

```bash
# Touch the plugin JAR to trigger reload
touch /app/plugins/my-plugin-1.0.0.jar
```

## Best Practices

1. **Event-Driven**: Use events instead of polling for cross-shard communication
2. **Async Operations**: Use the scheduler for I/O-bound operations
3. **Error Handling**: Always handle errors gracefully, don't crash the shard
4. **Memory Management**: Clean up resources in `onDisable()`
5. **Permissions**: Always check permissions for admin commands
6. **Persistence**: Use the API's data layer for persistent storage
7. **Testing**: Write unit tests using Minestom's test framework

## API Reference

See [docs/API.md](../API.md) for the complete API documentation.

See [docs/ARCHITECTURE_v2.md](../ARCHITECTURE_v2.md) for architecture details.
