# Plugin API Documentation

## Getting Started

Create a plugin by implementing `ShardedPlugin`:

```java
public class MyPlugin implements ShardedPlugin {
    
    @Override
    public void onEnable(ShardedPluginContext context) {
        ShardedWorld world = context.getWorld();
        Logger logger = context.getLogger();
        
        logger.info("MyPlugin enabled!");
        
        // Register event handler
        world.registerEventHandler(new ShardedEventHandler<PlayerJoinEvent>() {
            @Override
            public Class<PlayerJoinEvent> getEventType() {
                return PlayerJoinEvent.class;
            }
            
            @Override
            public void handle(PlayerJoinEvent event) {
                world.broadcastMessage(Component.text("Welcome, " + event.getUsername()));
            }
        });
    }
    
    @Override
    public void onDisable() {
        // Cleanup
    }
    
    @Override
    public PluginInfo getInfo() {
        return new PluginInfo("MyPlugin", "1.0.0", "Your Name");
    }
}
```

## Core Interfaces

### ShardedWorld

Main interface for world operations:

- `setBlock(x, y, z, block)` - Set a block
- `getBlock(x, y, z)` - Get a block
- `spawnEntity(type, position)` - Spawn an entity
- `broadcastMessage(message)` - Send message to all players
- `registerEventHandler(handler)` - Register event listener

### ShardedPlayer

Player interface:

- `getPosition()` - Get player position
- `teleport(position)` - Teleport player
- `sendMessage(message)` - Send message to player
- `getInventory()` - Get player inventory

### Events

Available events:

- `PlayerJoinEvent` - Fired when player joins
- `PlayerQuitEvent` - Fired when player quits
- `BlockBreakEvent` - Fired when block is broken
- `EntityDamageEvent` - Fired when entity takes damage

## Building Plugins

1. Create a new Gradle project
2. Add dependency: `implementation("com.shardedmc:api:1.0.0")`
3. Implement `ShardedPlugin`
4. Build JAR and place in `plugins/` directory
