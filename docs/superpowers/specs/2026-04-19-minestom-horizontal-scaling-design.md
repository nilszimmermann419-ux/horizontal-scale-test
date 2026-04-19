# Minestom Horizontal Scaling Server - Design Document

**Date:** 2026-04-19
**Approach:** Central Coordinator with Redis (Approach B)
**Target Scale:** Tens of thousands of concurrent players
**Performance Goal:** <100ms seamless player transitions between shards

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [High-Level Components](#high-level-components)
3. [Data Flow & Communication](#data-flow--communication)
4. [Key Components Detail](#key-components-detail)
5. [Performance Optimizations](#performance-optimizations)
6. [Plugin API (Sharded Abstraction)](#plugin-api-sharded-abstraction)
7. [Error Handling & Edge Cases](#error-handling--edge-cases)
8. [Testing Strategy](#testing-strategy)
9. [Technology Stack](#technology-stack)
10. [Deployment Considerations](#deployment-considerations)

---

## Architecture Overview

The system distributes a single Minecraft world across multiple Minestom server instances (shards), with each shard responsible for a subset of chunks. A central coordinator manages shard assignments, player transitions, and global state. Redis serves as the shared state store and pub/sub message bus.

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Plugin JARs   │────▶│ ShardedPluginAPI │────▶│  Coordinator    │
│  (Developer     │     │ (Abstraction     │     │  (gRPC + HTTP)  │
│   Code)         │     │   Layer)         │     │                 │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
                              ┌───────────────────────────┼───────────────────────────┐
                              │                           │                           │
                              ▼                           ▼                           ▼
                        ┌──────────┐                ┌──────────┐                 ┌──────────┐
                        │  Redis   │◀──────────────▶│  Shard 1 │◀───────────────▶│  Shard 2 │
                        │  (State) │                │ (Minestom│                 │ (Minestom│
                        │  (Pub/Sub│                │  + gRPC) │                 │  + gRPC) │
                        └──────────┘                └──────────┘                 └──────────┘
```

---

## High-Level Components

### 1. Central Coordinator
- **REST API:** HTTP endpoints for shard registration, health checks, player routing
- **gRPC Service:** Low-latency commands for player handoffs, chunk allocation
- **Chunk Allocation Algorithm:** Dynamic assignment based on shard load, capacity, and player distribution
- **Health Monitoring:** Tracks shard health via heartbeats, removes dead shards, triggers rebalancing
- **Configuration:** World settings, shard capacity limits, chunk region size (configurable, default 16x16 chunks)

### 2. Minestom Shards
- **Custom Chunk Loader:** Requests chunk ownership from Coordinator before loading
- **Player Listener:** Detects boundary crossings, notifies Coordinator
- **gRPC Client:** Responds to handoff requests, sends player state
- **Entity Manager:** Manages entity serialization/deserialization for transitions
- **Block Cache:** Caches frequently accessed block data for boundary regions

### 3. Redis
- **State Store:** Shard metadata, chunk assignments, player locations, entity states
- **Pub/Sub:** Cross-shard events (block updates, entity transfers, global broadcasts)
- **Caching:** Hot data caching with TTL eviction policies
- **Persistence:** Optional RDB snapshots for recovery

### 4. Proxy/Load Balancer (Optional)
- Routes initial player connections based on Coordinator instructions
- Can be simplified if Coordinator provides direct shard addresses

### 5. Plugin API (Sharded Abstraction Layer)
- Provides unified interface hiding shard complexity from plugin developers
- Transparently routes API calls to correct shards via Coordinator
- Event system supporting both local and global event propagation

---

## Data Flow & Communication

### Shard Registration Flow
```
1. Shard starts
2. Connects to Coordinator via gRPC
3. Reports: shard ID, capacity, current load, network address
4. Coordinator updates Redis shard registry
5. Coordinator assigns initial chunk regions
6. Shard begins accepting player connections
```

### Player Join Flow
```
1. Player connects via Proxy
2. Proxy queries Coordinator for shard assignment
3. Coordinator checks Redis for player's last known chunk
4. Coordinator identifies nearest healthy shard with capacity
5. Coordinator returns shard address to Proxy
6. Proxy routes player to assigned shard
7. Shard loads player's chunk region from Redis/World Storage
8. Player spawns in world
```

### Chunk Boundary Transition (Seamless Handoff)
```
1. Player approaches chunk boundary (configurable buffer, default 3 chunks)
2. Current shard notifies Coordinator via gRPC
3. Coordinator:
   - Identifies destination shard for target chunks
   - Verifies destination shard has capacity
   - Triggers chunk pre-loading on destination shard
4. Destination shard loads chunks + entities from Redis/World Storage
5. Coordinator initiates handoff protocol:
   a. Source shard: freeze player input, serialize entity state
   b. Serialize: inventory, health, position, velocity, effects, metadata
   c. Send serialized state to destination shard via gRPC
   d. Destination shard: spawn player with state, resume simulation
6. Update player location in Redis
7. Source shard unloads player chunks after cooldown (configurable)
```

**Target Transition Time:** <100ms (measured from handoff initiation to player resume)

### Cross-Shard Events (Redis Pub/Sub)
- **Block Updates Near Boundaries:** When a block is modified within N chunks of a shard boundary, publish event to neighboring shards
- **Entity Movement Across Borders:** Entity ownership transfer protocol via pub/sub
- **Global Events:** Weather changes, time updates, broadcast messages
- **Player Chat:** Global chat messages propagated to all shards

### Chunk Allocation Algorithm
- **Initial Assignment:** Round-robin distribution across available shards
- **Dynamic Rebalancing:**
  - Monitor player density per chunk region
  - Identify overloaded shards (player count > threshold)
  - Identify underloaded shards (player count < threshold)
  - Migrate low-activity chunk regions from overloaded to underloaded shards
  - Preserve chunk adjacency where possible to minimize cross-shard traffic
- **Failure Recovery:**
  - Detect shard failure via missed heartbeats (configurable timeout, default 5s)
  - Mark failed shard as unhealthy in Redis
  - Reassign all chunks from failed shard to healthy shards
  - Notify affected players to reconnect to new shards
  - Load chunk data from persistent world storage

---

## Key Components Detail

### Coordinator API

#### REST Endpoints
```
POST /api/v1/shards/register
  Body: { shardId, address, port, capacity, regions: [] }
  Response: { status, assignedRegions: [] }

POST /api/v1/shards/heartbeat
  Body: { shardId, load, playerCount, regions: [] }
  Response: { status, reassignments: [] }

GET /api/v1/players/{uuid}/shard
  Response: { shardId, address, port }

POST /api/v1/players/{uuid}/route
  Body: { targetChunkX, targetChunkZ }
  Response: { shardId, address, port }

GET /api/v1/world/chunks/{x}/{z}/owner
  Response: { shardId }

POST /api/v1/world/rebalance
  Body: { strategy: "load_based" }
  Response: { migrations: [] }
```

#### gRPC Service
```protobuf
service CoordinatorService {
  rpc RegisterShard(ShardInfo) returns (RegistrationResponse);
  rpc SendHeartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  
  // Player handoff
  rpc RequestPlayerTransfer(TransferRequest) returns (TransferResponse);
  rpc ConfirmPlayerTransfer(TransferConfirmation) returns (ConfirmationResponse);
  
  // Chunk management
  rpc RequestChunkLoad(ChunkLoadRequest) returns (ChunkLoadResponse);
  rpc RequestChunkUnload(ChunkUnloadRequest) returns (ChunkUnloadResponse);
  
  // Entity sync
  rpc SyncEntityState(EntityStateSync) returns (SyncResponse);
}
```

### Redis Schema

#### Keys
```
shard:{shardId} -> Hash {
  "address": "host:port",
  "capacity": 1000,
  "playerCount": 523,
  "load": 0.75,
  "status": "healthy",
  "lastHeartbeat": 1713541200,
  "regions": "[0,0;1,0;0,1]"
}

chunk:{x}:{z} -> Hash {
  "ownerShard": "shard-1",
  "entityCount": 15,
  "lastUpdate": 1713541200,
  "lock": "none"
}

player:{uuid} -> Hash {
  "currentShard": "shard-1",
  "chunkX": 10,
  "chunkZ": -5,
  "position": "{160.5,64.0,-80.3}",
  "inventory": "base64_encoded",
  "health": 20.0,
  "status": "active"
}

entity:{uuid} -> Hash {
  "type": "zombie",
  "position": "{160.5,64.0,-80.3}",
  "ownerShard": "shard-1",
  "state": "base64_encoded"
}
```

#### Pub/Sub Channels
```
shard-events -> { type: "shard_joined", shardId: "..." }
shard-events -> { type: "shard_failed", shardId: "..." }
player-transfers -> { type: "transfer_initiated", playerId: "...", from: "...", to: "..." }
chunk-updates -> { type: "block_break", chunkX: 10, chunkZ: -5, position: "...", block: "..." }
global-events -> { type: "weather_change", weather: "rain" }
```

### Minestom Shard Architecture

#### Custom Components
1. **ShardedChunkLoader**
   - Extends Minestom's ChunkLoader
   - Before loading a chunk, verifies ownership with Coordinator
   - If not owned, requests ownership transfer or redirects to correct shard
   - Caches chunk data from Redis

2. **PlayerBoundaryMonitor**
   - Listens to player position updates
   - Calculates distance to shard boundaries
   - Triggers pre-load when player enters buffer zone (default 3 chunks from boundary)
   - Initiates handoff when player crosses boundary

3. **EntityStateSerializer**
   - Serializes/deserializes complete entity state
   - Uses Protocol Buffers for compact binary format
   - Includes: position, velocity, health, inventory, effects, AI state, metadata
   - Supports delta updates for frequent small changes

4. **CrossShardEventHandler**
   - Subscribes to Redis pub/sub channels
   - Applies block updates near shard boundaries
   - Handles entity transfers from neighboring shards
   - Broadcasts global events to local players

5. **ShardHeartbeatService**
   - Sends periodic heartbeats to Coordinator (default 1s interval)
   - Reports current load, player count, memory usage
   - Listens for rebalancing commands from Coordinator

---

## Performance Optimizations

### Async-First Architecture
- All I/O operations (Redis, gRPC, chunk loading) use `CompletableFuture` and async callbacks
- Non-blocking event loop for network operations
- Separate thread pools for: chunk loading, entity processing, network I/O

### Efficient Data Transfer
- **Protocol Buffers:** Compact binary serialization for player state and entity data
- **Batched Redis Operations:** Use Redis pipelining and MGET/MSET for bulk operations
- **Zero-Copy Networking:** Leverage Netty's ByteBuf pooling (already used by Minestom)
- **Compression:** LZ4 for hot data, gzip for cold storage

### Chunk Loading Optimizations
- **Predictive Pre-loading:** Based on player velocity vector, load chunks 2-3 regions ahead
- **Lazy Entity Serialization:** Only sync modified entities, use delta compression
- **Chunk Data Deduplication:** Share immutable chunk data (terrain) between shards via Redis
- **Memory-mapped Files:** For world storage access patterns

### Memory Management
- **Object Pooling:** Reuse frequent allocations (packets, chunk data buffers, player state objects)
- **Entity State Cache:** Per-shard LRU cache with TTL eviction
- **Chunk Compression:** Configurable compression levels based on activity
- **Garbage Collection Tuning:** Recommend G1GC with optimized settings for low latency

### Network Optimizations
- **gRPC Streaming:** Use bidirectional streams for shard-to-coordinator communication
- **Connection Pooling:** Maintain persistent gRPC connections between shards and coordinator
- **Batch Updates:** Group small updates into larger packets to reduce overhead

### Redis Optimizations
- **Pipelining:** Batch multiple Redis commands into single round-trip
- **Lua Scripts:** Use server-side Lua for atomic multi-key operations
- **Redis Cluster:** Scale Redis horizontally for large deployments
- **Read Replicas:** Use replicas for read-heavy operations (chunk lookups)

---

## Plugin API (Sharded Abstraction)

### Design Principle
Plugin developers write code as if it's a single server. The API transparently routes calls to the correct shard via the Coordinator.

### Core Interfaces

```java
public interface ShardedWorld {
    // Block operations
    CompletableFuture<Void> setBlock(Vec3i position, Block block);
    CompletableFuture<Block> getBlock(Vec3i position);
    CompletableFuture<Boolean> breakBlock(Vec3i position);
    
    // Entity operations
    CompletableFuture<ShardedEntity> spawnEntity(EntityType type, Vec3d position);
    CompletableFuture<Optional<ShardedEntity>> getEntity(UUID uuid);
    CompletableFuture<Void> removeEntity(UUID uuid);
    
    // World properties
    CompletableFuture<WorldTime> getTime();
    CompletableFuture<Void> setTime(WorldTime time);
    CompletableFuture<Weather> getWeather();
    CompletableFuture<Void> setWeather(Weather weather);
    
    // Chunk operations
    CompletableFuture<Boolean> isChunkLoaded(int chunkX, int chunkZ);
    CompletableFuture<Void> loadChunk(int chunkX, int chunkZ);
    CompletableFuture<Void> unloadChunk(int chunkX, int chunkZ);
    
    // Broadcasting
    void broadcastMessage(Component message);
    void broadcastEvent(ShardedEvent event);
    void playSound(Sound sound, Vec3d position);
    
    // Event registration
    void registerEventHandler(ShardedEventHandler handler);
    void unregisterEventHandler(ShardedEventHandler handler);
}

public interface ShardedPlayer {
    UUID getUuid();
    String getUsername();
    
    // Position
    CompletableFuture<Vec3d> getPosition();
    CompletableFuture<Void> teleport(Vec3d position);
    CompletableFuture<Void> teleportAsync(Vec3d position, Consumer<Result> callback);
    
    // Player state
    CompletableFuture<Double> getHealth();
    CompletableFuture<Void> setHealth(double health);
    CompletableFuture<ShardedInventory> getInventory();
    CompletableFuture<GameMode> getGameMode();
    CompletableFuture<Void> setGameMode(GameMode mode);
    
    // Communication
    CompletableFuture<Void> sendMessage(Component message);
    CompletableFuture<Void> sendActionBar(Component message);
    CompletableFuture<Void> sendTitle(Title title);
    CompletableFuture<Void> playSound(Sound sound);
    
    // Chunk view
    CompletableFuture<Set<ChunkPos>> getViewableChunks();
    
    // Permissions (if applicable)
    CompletableFuture<Boolean> hasPermission(String permission);
}

public interface ShardedEntity {
    UUID getUuid();
    EntityType getType();
    
    CompletableFuture<Vec3d> getPosition();
    CompletableFuture<Void> setPosition(Vec3d position);
    CompletableFuture<Vec3d> getVelocity();
    CompletableFuture<Void> setVelocity(Vec3d velocity);
    
    CompletableFuture<Double> getHealth();
    CompletableFuture<Void> setHealth(double health);
    CompletableFuture<Void> remove();
    
    CompletableFuture<Void> setMetadata(String key, Object value);
    CompletableFuture<Optional<Object>> getMetadata(String key);
}

public interface ShardedInventory {
    CompletableFuture<ItemStack> getItem(int slot);
    CompletableFuture<Void> setItem(int slot, ItemStack item);
    CompletableFuture<Integer> getSize();
    CompletableFuture<Void> clear();
}
```

### Event System

```java
public interface ShardedEventHandler<T extends ShardedEvent> {
    void handle(T event);
}

public abstract class ShardedEvent {
    private final UUID eventId;
    private final long timestamp;
    private final boolean global;
    
    // Local events: fire immediately on current shard
    // Global events: propagate via Redis pub/sub to all shards
    public boolean isGlobal() { return global; }
}

public class PlayerJoinEvent extends ShardedEvent {
    private final UUID playerId;
    private final Vec3d spawnPosition;
}

public class PlayerQuitEvent extends ShardedEvent {
    private final UUID playerId;
    private final QuitReason reason;
}

public class BlockBreakEvent extends ShardedEvent {
    private final UUID playerId;
    private final Vec3i position;
    private final Block block;
    private boolean cancelled;
}

public class EntityDamageEvent extends ShardedEvent {
    private final UUID entityId;
    private final double damage;
    private final DamageSource source;
}
```

### Plugin Lifecycle

```java
public interface ShardedPlugin {
    void onEnable(ShardedPluginContext context);
    void onDisable();
    
    default PluginInfo getInfo() {
        return new PluginInfo("plugin-name", "1.0.0", "Author");
    }
}

public interface ShardedPluginContext {
    ShardedWorld getWorld();
    ShardedScheduler getScheduler();
    Logger getLogger();
    Path getDataDirectory();
    
    // Plugin configuration
    <T> T getConfig(Class<T> configClass);
    void saveConfig(Object config);
    
    // Cross-plugin communication
    Optional<ShardedPlugin> getPlugin(String name);
    void registerService(Class<?> serviceClass, Object service);
    <T> Optional<T> getService(Class<T> serviceClass);
}

public interface ShardedScheduler {
    ScheduledTask runTask(Runnable task);
    ScheduledTask runTaskLater(Runnable task, Duration delay);
    ScheduledTask runTaskTimer(Runnable task, Duration delay, Duration period);
    ScheduledTask runAsync(Runnable task);
}
```

### Plugin Loading

```java
public class ShardedPluginManager {
    // Load plugin from JAR file
    public CompletableFuture<ShardedPlugin> loadPlugin(Path jarPath);
    
    // Enable/disable plugins
    public CompletableFuture<Void> enablePlugin(String pluginName);
    public CompletableFuture<Void> disablePlugin(String pluginName);
    
    // Get loaded plugins
    public List<ShardedPlugin> getLoadedPlugins();
    public Optional<ShardedPlugin> getPlugin(String name);
}
```

### Key Implementation Details

1. **Transparent Shard Routing:**
   - All API methods accept world coordinates
   - API layer determines target shard via Coordinator
   - Routes call to correct shard via gRPC
   - Returns `CompletableFuture` for async result

2. **Local vs Global Events:**
   - Local events processed immediately on current shard
   - Global events published to Redis, received by all shards
   - Event handlers executed asynchronously to prevent blocking

3. **Entity Tracking:**
   - API maintains UUID-to-shard mapping cache
   - Cache updated on entity transfers and player transitions
   - Stale entries detected and refreshed on miss

4. **Error Handling:**
   - Network failures: automatic retry with exponential backoff
   - Shard unavailable: queue operations, fail after timeout
   - Consistency: eventual consistency model for cross-shard operations

---

## Error Handling & Edge Cases

### Shard Crash During Handoff
```
1. Coordinator initiates handoff
2. Source shard crashes before completion
3. Coordinator detects timeout (configurable, default 5s)
4. Coordinator marks handoff as failed
5. Player remains on source shard (if still connected)
6. Coordinator attempts to find alternative target shard
7. If source shard also crashed, player gets disconnected with save state
8. Player can reconnect, state restored from Redis
```

### Redis Unavailable
```
1. Shards detect Redis connection failure
2. Shards switch to degraded mode with cached state
3. Cached state valid for 30 seconds (configurable)
4. Coordinator queues updates in memory
5. After 30s without Redis: shards reject new connections, finish active operations
6. Alert admin, attempt reconnection with exponential backoff
```

### Player Disconnect During Transition
```
1. Player disconnects while handoff in progress
2. Source shard detects disconnect
3. Saves partial state to Redis
4. Coordinator cancels handoff
5. Destination shard cleans up pre-loaded chunks
6. Player can reconnect, state restored from last save
```

### Overloaded Shard
```
1. Coordinator monitors shard load metrics
2. When load > 80% (configurable), mark as saturated
3. Stop assigning new chunks to saturated shard
4. Identify low-activity chunk regions on saturated shard
5. Migrate identified chunks to underloaded shards
6. Notify affected players of pending transition
7. Gradual migration to avoid disruption
```

### Duplicate Player Connections
```
1. New connection with same UUID as existing player
2. Coordinator detects duplicate
3. Disconnect old connection (kick with "logged in from another location")
4. Accept new connection
5. Ensure old session state is saved to Redis before disconnect
```

### World Border Enforcement
```
1. Coordinator maintains world border configuration
2. Shards check player position against world border
3. Players outside border teleported back inside
4. Chunk generation requests outside border rejected
```

---

## Testing Strategy

### Unit Tests
- Chunk allocation algorithm correctness
- Player state serialization/deserialization round-trip
- Event routing (local vs global)
- Plugin API contract compliance
- Redis schema operations

### Integration Tests
- Full handoff flow with 2 shards + Redis (using Testcontainers)
- Shard failure and recovery simulation
- Player reconnection after disconnect
- Cross-shard block updates
- Plugin load/unload lifecycle

### Load Tests
- Simulate 1000 concurrent players crossing boundaries
- Measure handoff latency (target: <100ms p99)
- Test shard rebalancing under load
- Redis throughput under high event volume
- Memory usage profiling

### Chaos Tests
- Random shard kills during gameplay
- Redis restart mid-operation
- Network partition between shards and coordinator
- Sudden player mass join/quit
- Verify no data loss, graceful degradation

### Performance Benchmarks
- Chunk loading time from cold start
- Player state serialization size and speed
- gRPC round-trip latency
- Redis operation throughput
- End-to-end player transition time

---

## Technology Stack

### Core
- **Java 21+** (Virtual Threads for async operations)
- **Minestom** (Latest stable release)
- **Gradle** (Build system with Kotlin DSL)

### Communication
- **gRPC** (Shard-to-Coordinator, efficient binary protocol)
- **Protocol Buffers** (Serialization)
- **Netty** (Underlying networking, provided by Minestom)

### Data Store
- **Redis** (State store, pub/sub, caching)
- **Lettuce** (Reactive Redis client for Java)

### Plugin System
- **Custom ClassLoader** (Isolated plugin loading)
- **ServiceLoader** (Plugin discovery)

### Monitoring & Observability
- **Micrometer** (Metrics collection)
- **Prometheus** (Metrics storage)
- **Structured Logging** (JSON format, correlation IDs)

### Testing
- **JUnit 5** (Unit testing)
- **Testcontainers** (Integration testing with Redis)
- **JMH** (Microbenchmarks)

---

## Deployment Considerations

### Infrastructure
- **Coordinator:** 2-3 instances with load balancer (HAProxy/NGINX)
- **Shards:** Horizontally scalable, deploy based on player count
- **Redis:** Redis Cluster for production (3 master + 3 replica minimum)
- **World Storage:** Network-attached storage or distributed filesystem

### Configuration
- Environment-specific config files (dev, staging, prod)
- Key settings: chunk region size, shard capacity, Redis endpoints, heartbeat intervals
- Hot-reload for non-critical settings

### Monitoring
- Shard health dashboards (Grafana)
- Player transition latency metrics
- Redis memory and throughput monitoring
- Alerting on shard failures, high latency, Redis issues

### Security
- gRPC TLS encryption between shards and coordinator
- Redis TLS and AUTH
- Plugin sandboxing (restricted ClassLoader permissions)
- Rate limiting on public APIs

---

## Future Enhancements (Out of Scope for v1)

1. **Multi-World Support:** Extend to multiple worlds with cross-world portals
2. **World Editing Tools:** Admin commands that work across shards
3. **Custom Entity AI:** Distributed pathfinding across shard boundaries
4. **Save/Restore:** Point-in-time world snapshots
5. **Geographic Distribution:** Shards in different regions for global player base
6. **Player Data Migration:** Tools for migrating from single-server to sharded

---

## Success Criteria

- [ ] Seamless player transitions between shards (<100ms)
- [ ] Support for 10,000+ concurrent players
- [ ] Zero data loss during shard failures
- [ ] Plugin API compatible with simple single-server plugins
- [ ] Sub-100ms API response times for local operations
- [ ] Graceful degradation when Redis is unavailable
- [ ] Dynamic rebalancing without player disruption
- [ ] Comprehensive test coverage (>80%)
- [ ] Production-ready monitoring and alerting

---

*Document Version: 1.0*
*Status: Approved*
