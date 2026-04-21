# ShardedMC v2.0 Architecture
## Seamless Vanilla Experience on Horizontally Scaled Minecraft Servers

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Design Philosophy](#2-design-philosophy)
3. [System Overview](#3-system-overview)
4. [Coordinator Service](#4-coordinator-service)
5. [Shard Server](#5-shard-server)
6. [World Synchronization Layer](#6-world-synchronization-layer)
7. [Player State Management](#7-player-state-management)
8. [Chunk Ownership & Boundaries](#8-chunk-ownership--boundaries)
9. [Lighting Engine](#9-lighting-engine)
10. [Entity System](#10-entity-system)
11. [Block Interaction Model](#11-block-interaction-model)
12. [Fault Tolerance & Recovery](#12-fault-tolerance--recovery)
13. [Performance Architecture](#13-performance-architecture)
14. [Data Storage Strategy](#14-data-storage-strategy)
15. [Deployment Model](#15-deployment-model)
16. [Implementation Roadmap](#16-implementation-roadmap)

---

## 1. Executive Summary

ShardedMC v2.0 is a ground-up redesign of horizontally-scalable Minecraft server architecture. Unlike v1.0 which attempted to retrofit sharding onto Minestom's single-server model, v2.0 embraces a true distributed systems approach.

**Key Innovations:**
- **Virtual Player Proxies**: Players connect to lightweight proxies, not shards
- **Deterministic World State**: No chunk ownership conflicts via deterministic locking
- **Event-Sourced Synchronization**: All state changes are events, not polling
- **Predictive Lighting**: Client-side lighting with server validation
- **Seamless Boundaries**: Sub-50ms handoffs with predictive pre-loading

**Target:** 100% vanilla parity with 100,000+ concurrent players

---

## 2. Design Philosophy

### 2.1 Core Principles

1. **Vanilla First**: Every feature must be indistinguishable from single-server vanilla
2. **Eventual Consistency is Fine**: Players only care about their local view
3. **Fail Open, Not Closed**: Network issues shouldn't break gameplay
4. **Deterministic Over Coordinated**: Use math instead of consensus where possible
5. **Shard = Process, Not Machine**: One machine runs many shards

### 2.2 Anti-Patterns Eliminated

| v1.0 Anti-Pattern | v2.0 Solution |
|---|---|
| Chunk ownership denies on uncertainty | Optimistic locking with rollback |
| Synchronous lighting calculation | Async lighting with client prediction |
| Redis for chunk storage | File-based chunk storage with Redis metadata |
| String serialization for blocks | Binary protobuf serialization |
| Polling-based sync | Event-sourced pub/sub |
| Single-threaded per shard | Actor model per chunk region |
| Coordinator as bottleneck | Deterministic hash-based routing |

---

## 3. System Overview

### 3.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENTS                              │
│              (Minecraft Java Edition)                       │
└────────────────────┬────────────────────────────────────────┘
                     │ TCP
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                  PROXY LAYER (Stateful)                     │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │
│  │ Proxy 1 │  │ Proxy 2 │  │ Proxy 3 │  │ Proxy N │       │
│  │ (Go)    │  │ (Go)    │  │ (Go)    │  │ (Go)    │       │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘       │
│       └─────────────┴─────────────┴─────────────┘           │
│              HAProxy / Envoy (Load Balancer)                │
└────────────────────┬────────────────────────────────────────┘
                     │ gRPC
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              COORDINATOR CLUSTER (Stateless)                │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │
│  │ Coord 1 │  │ Coord 2 │  │ Coord 3 │  │ Coord N │       │
│  │ (Go)    │  │ (Go)    │  │ (Go)    │  │ (Go)    │       │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘       │
│       └─────────────┴─────────────┴─────────────┘           │
│              Raft Consensus (Etcd or Hashicorp)             │
└────────────────────┬────────────────────────────────────────┘
                     │ State Stream (NATS / Kafka)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│               SHARD CLUSTER (Stateful)                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │
│  │ Shard A │  │ Shard B │  │ Shard C │  │ Shard N │       │
│  │Regions  │  │Regions  │  │Regions  │  │Regions  │       │
│  │ 0,0-3,3 │  │ 4,0-7,3 │  │ 0,4-3,7 │  │ etc     │       │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘       │
│       └─────────────┴─────────────┴─────────────┘           │
│              Shared Nothing (except state stream)           │
└─────────────────────────────────────────────────────────────┘
                     │
         ┌───────────┼───────────┐
         ▼           ▼           ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │  NATS   │ │  Redis  │ │  MinIO  │
   │ (Events)│ │ (Cache) │ │(Chunks) │
   └─────────┘ └─────────┘ └─────────┘
```

### 3.2 Component Responsibilities

| Component | Responsibility | Technology |
|---|---|---|
| **Proxy** | Connection hold, packet translation, player routing | Go + custom TCP proxy |
| **Coordinator** | Shard registry, chunk allocation, player routing | Go + Raft |
| **Shard** | Game simulation, entity AI, physics, block logic | Minestom (modified) |
| **NATS** | Event bus for cross-shard communication | NATS JetStream |
| **Redis** | Hot cache, player sessions, temporary state | Redis Cluster |
| **MinIO** | Chunk storage, world snapshots, backups | MinIO (S3-compatible) |

---

## 4. Coordinator Service

### 4.1 Design

The coordinator is **stateless** and **horizontally scalable**. All state is stored in the consensus layer (Etcd).

**Why stateless?**
- Can scale to 100+ coordinators
- No single point of failure
- Rolling updates without downtime

### 4.2 Responsibilities

1. **Shard Registry**: Track which shards are online and their health
2. **Chunk Allocation**: Assign chunk regions to shards deterministically
3. **Player Routing**: Tell proxies which shard handles a player's chunks
4. **Rebalancing**: Move regions when shards fail or join

### 4.3 Deterministic Chunk Allocation

Instead of dynamic allocation (which caused v1.0's ownership issues), v2.0 uses **consistent hashing**:

```
chunk_owner(chunkX, chunkZ, numShards) = consistentHash(chunkX + ":" + chunkZ, shardList)
```

**Benefits:**
- No coordinator bottleneck for ownership lookups
- Shards can compute ownership locally
- Deterministic = testable, debuggable

**Shard Join:**
- New shard added to ring
- Existing shards compute new ownership locally
- Chunks migrate in background

**Shard Leave:**
- Failed shard removed from ring after timeout
- Neighboring shards take over regions
- No central coordinator decision needed

### 4.4 API

```protobuf
service CoordinatorService {
  // Shard Lifecycle
  rpc RegisterShard(ShardInfo) returns (ShardRegistration);
  rpc Heartbeat(ShardHealth) returns (HeartbeatAck);
  rpc UnregisterShard(ShardId) returns (Status);
  
  // Chunk Operations (metadata only)
  rpc GetChunkOwner(ChunkCoord) returns (ShardId);
  rpc GetRegionMap(Empty) returns (RegionMap);
  
  // Player Operations
  rpc GetPlayerShard(PlayerId) returns (ShardId);
  rpc RecordPlayerPosition(PlayerPosition) returns (Status);
}
```

### 4.5 Failure Handling

| Failure | Handling |
|---|---|
| Coordinator crash | Proxy retries another coordinator |
| Network partition | Shards continue with cached ownership |
| Split brain | Raft consensus prevents it |
| Coordinator overload | Scale horizontally, no state migration |

---

## 5. Shard Server

### 5.1 Architecture

Each shard is a **Minestom instance** with significant modifications:

```
Shard Process
├── Region Manager (manages N×N chunk regions)
│   ├── Chunk Loader (async file I/O)
│   ├── Entity Manager (per-region entity tracking)
│   ├── Lighting Engine (async calculation)
│   └── Physics Engine (per-region simulation)
├── Player Manager
│   ├── Local Players (active on this shard)
│   ├── Border Players (near boundary, pre-loaded)
│   └── Spectator Players (viewing from other shards)
├── Event Bus
│   ├── Inbound (from NATS: block changes, entity spawns)
│   └── Outbound (to NATS: local changes)
└── State Reconciler
    ├── Conflict Resolution (last-write-wins with timestamps)
    └── Rollback Engine (for grief protection)
```

### 5.2 Region-Based Sharding

Instead of individual chunks, shards own **regions** (e.g., 4×4 chunks = 64×64 blocks):

**Why regions?**
- Reduces coordination overhead (1 region check vs 16 chunk checks)
- Better locality for entity AI (mobs can pathfind within region)
- Easier load balancing (move 1 region, not 16 chunks)
- Simpler boundaries (region borders instead of chunk borders)

**Region Assignment:**
```java
// Deterministic - no coordinator needed
ShardId getRegionOwner(RegionCoord region) {
    return consistentHash(region.x + ":" + region.z, activeShards);
}
```

### 5.3 Chunk Storage

**Storage Format:** Binary Protobuf

```protobuf
message ChunkData {
  int32 x = 1;
  int32 z = 2;
  repeated SectionData sections = 3;
  uint64 lastModified = 4;
  uint32 version = 5;
}

message SectionData {
  int32 y = 1;
  bytes blockPalette = 2;  // VarInt encoded palette indices
  bytes blockData = 3;     // Compact bit-packed block IDs
  bytes skyLight = 4;      // 2048 bytes (nibble array)
  bytes blockLight = 5;    // 2048 bytes (nibble array)
}
```

**Storage Strategy:**
1. **Hot chunks** (players nearby): In-memory + Redis cache
2. **Warm chunks** (recently active): Redis cache
3. **Cold chunks** (inactive): MinIO/S3

**Loading Pipeline:**
```
Request Chunk
  → Check Memory (O(1))
  → Check Redis (O(1))
  → Load from MinIO (async, ~10ms)
  → Generate if new (async, ~50ms)
  → Return Future<Chunk>
```

### 5.4 Lighting Engine v2

**Problem with v1.0:** Synchronous calculation blocked chunk loading

**Solution:** Predictive + Asynchronous

```
┌────────────────────────────────────────────┐
│           LIGHTING PIPELINE                │
├────────────────────────────────────────────┤
│ 1. Client Prediction                       │
│    - Client calculates lighting locally    │
│    - Server validates asynchronously       │
│    - 99% of cases: client is correct       │
│                                            │
│ 2. Async Server Validation                 │
│    - Lighting calculation runs in thread   │
│    - Results sent as "correction" packets  │
│    - Rarely needed, seamless when it is    │
│                                            │
│ 3. Chunk Border Propagation                │
│    - Light spills into adjacent regions    │
│    - Pre-computed light maps for borders   │
│    - Updated when source changes           │
└────────────────────────────────────────────┘
```

**Algorithm:**
1. **Quick Pass** (synchronous, <1ms):
   - Full sky light above terrain
   - Simple heightmap-based occlusion
   - No block light calculation

2. **Deep Pass** (asynchronous, ~10ms):
   - BFS from light sources
   - Proper transparency checking
   - Neighbor chunk light propagation
   - Results applied when ready

**Key Insight:** Players don't notice if lighting "pops in" slightly after chunk loads. They DO notice if chunk loading stalls for lighting.

### 5.5 Block Breaking Model

**v1.0 Problem:** Ownership check denied breaks on any uncertainty

**v2.0 Solution:** Optimistic Locking with Rollback

```
Player Breaks Block
  → Optimistically break locally
  → Broadcast to event bus
  → Other shards apply if in their region
  → If conflict detected (two players, same block):
     - Compare timestamps (Lamport clocks)
     - Winner keeps change, loser gets rollback
     - Rollback sent as "block update" packet (looks like normal game event)
```

**Why this works:**
- Block conflicts are extremely rare (two players clicking same block within 50ms)
- When they happen, rollback looks like normal lag
- No ownership checks blocking gameplay
- No coordinator involvement in block breaking

**Grief Protection:**
- Log all block changes to append-only event log
- Rollback tool replays events in reverse
- No need to check ownership before breaking

---

## 6. World Synchronization Layer

### 6.1 Event Sourcing Architecture

All state changes are **events**. The world is a fold of the event log.

```protobuf
message WorldEvent {
  uint64 timestamp = 1;
  uint64 sequence = 2;
  string shardId = 3;
  oneof payload {
    BlockChangeEvent blockChange = 10;
    EntitySpawnEvent entitySpawn = 11;
    EntityMoveEvent entityMove = 12;
    PlayerJoinEvent playerJoin = 13;
    PlayerLeaveEvent playerLeave = 14;
  }
}

message BlockChangeEvent {
  int32 x = 1;
  int32 y = 2;
  int32 z = 3;
  string blockId = 4;
  string playerId = 5;  // empty if non-player cause
}
```

### 6.2 Event Bus (NATS JetStream)

**Topics:**
- `world.blocks.{regionX}.{regionZ}` - Block changes per region
- `world.entities.{regionX}.{regionZ}` - Entity updates per region
- `world.players.{playerId}` - Player-specific events
- `world.global` - Weather, time, global events

**Consumer Groups:**
- Each shard consumes events for its regions
- Durable subscriptions survive restarts
- ACK after processing (at-least-once delivery)

### 6.3 Conflict Resolution

**Last-Write-Wins with Vector Clocks:**

```java
BlockState resolveConflict(BlockState local, BlockState remote) {
    // Compare vector clocks
    if (local.clock.isAfter(remote.clock)) return local;
    if (remote.clock.isAfter(local.clock)) return remote;
    
    // Concurrent - tiebreak by timestamp
    return local.timestamp > remote.timestamp ? local : remote;
}
```

**Why this works for Minecraft:**
- Players rarely edit the same block simultaneously
- When they do, "later wins" is intuitive
- Rollback system can fix griefing after the fact

### 6.4 Event Log Compaction

The event log grows forever. Compact it periodically:

```
Compaction (every hour):
  - Read all events for a region
  - Build final state
  - Write snapshot to MinIO
  - Truncate events before snapshot
  - Keep last 5 minutes of events for rollback
```

---

## 7. Player State Management

### 7.1 Player Lifecycle

```
Connect
  → Proxy authenticates (Mojang/Yggdrasil)
  → Proxy asks coordinator: "Which shard?"
  → Coordinator: "Shard A, region 0,0"
  → Proxy connects to Shard A
  → Shard A loads player state from Redis
  → Player spawns

Move
  → Within region: Normal movement
  → Near boundary: Pre-load neighboring regions
  → Cross boundary: Seamless handoff

Disconnect
  → State saved to Redis
  → Region kept hot for 5 minutes
  → Event log preserved
```

### 7.2 State Storage

**Hot State** (Redis, TTL 1 hour):
```
player:{uuid}:position → {x, y, z, yaw, pitch}
player:{uuid}:inventory → serialized inventory
player:{uuid}:health → {health, food, saturation}
player:{uuid}:effects → active potion effects
player:{uuid}:metadata → {gamemode, xp, level}
```

**Cold State** (MinIO, permanent):
```
players/{uuid}/snapshot-{timestamp}.pb
players/{uuid}/history/{date}.log
```

### 7.3 Seamless Handoff

**The Sub-50ms Handoff:**

```
Player approaches boundary
  ↓
Shard A detects proximity (< 3 chunks to border)
  ↓
Shard A sends "PreloadPlayer" to Shard B
  ↓
Shard B loads player state + nearby chunks
  ↓
Shard B sends "Ready" to Shard A
  ↓
Player crosses boundary
  ↓
Shard A sends "TransferComplete" to Proxy
  ↓
Proxy routes packets to Shard B
  ↓
Shard B takes over (player doesn't notice)
```

**Key Optimizations:**
1. **Double-buffered state**: Both shards have player state
2. **Proxy routing**: Proxy switches destination, not player
3. **Chunk pre-loading**: Target region chunks loaded before handoff
4. **Entity freeze**: Entities frozen for 1 tick during handoff

---

## 8. Chunk Ownership & Boundaries

### 8.1 No Ownership Checks on Block Breaking

**v1.0:** Asked coordinator before every block break
**v2.0:** Break first, validate asynchronously

```
Player Breaks Block
  → Shard accepts immediately
  → Event published to bus
  → If block was in wrong region:
     - Event reaches correct shard
     - Correct shard: "That's my block!"
     - Conflict resolution: Winner = whoever's region it actually is
     - Loser gets rollback
```

**Why this is safe:**
- Players can't break blocks in unloaded regions (no blocks to break)
- If a region loads mid-break, event log records it
- Rollback system handles edge cases
- 99.999% of breaks are in loaded regions anyway

### 8.2 Region Borders

**Visual Seamlessness:**
- Regions share border chunks (each shard loads neighbor's border)
- Border chunks are read-only on non-owning shard
- Player sees seamless world, no loading screens

**Border Sync:**
```
Region A (owned by Shard 1)    Region B (owned by Shard 2)
         │                              │
    ┌────┴────┐                    ┌────┴────┐
    │ A │ A │ A │                    │ B │ B │ B │
    │ A │ A │ A │                    │ B │ B │ B │
    │ A │ A │B*│◄─────sync─────►│B*│ B │ B │ B │
    │ A │ A │B*│◄─────sync─────►│B*│ B │ B │ B │
    └─────────┘                    └─────────┘
         
    B* = Border chunks (loaded by both, writable by owner only)
```

---

## 9. Lighting Engine

### 9.1 Three-Tier Lighting

```
Tier 1: Client Prediction (instant)
  - Client calculates lighting when chunk loads
  - Uses local heightmap
  - Full sky light above surface
  
Tier 2: Server Quick Pass (< 1ms per chunk)
  - Simple heightmap-based sky light
  - No block light
  - Sent with chunk data
  
Tier 3: Server Deep Pass (async, ~10ms)
  - Full BFS from light sources
  - Proper transparency
  - Cross-chunk propagation
  - Sent as "light update" packet when ready
```

### 9.2 Why This Works

- Client prediction means no dark chunks
- Quick pass covers 90% of cases correctly
- Deep pass fixes the remaining 10%
- Light update packets are rare and small
- Players never see completely dark worlds

### 9.3 Cross-Region Light Propagation

Light sources near borders affect both regions:

```
Shard 1 (Region A)              Shard 2 (Region B)
     │                                 │
  torch                             dark
    │                                 │
    ▼                                 ▼
 [15][14][13]◄────event bus────►[13][12][11]
```

When torch is placed:
1. Shard 1 calculates light for its side
2. Publishes "LightSourceAdded" event
3. Shard 2 calculates light for its side
4. Both shards send updates to their players

---

## 10. Entity System

### 10.1 Entity Ownership

**Rule:** Entity is owned by the shard where it currently is.

**Movement:**
```
Entity moves within region
  → Normal simulation

Entity approaches border
  → Pre-notify neighbor shard
  → Neighbor loads entity state

Entity crosses border
  → Ownership transfers
  → Old shard stops simulating
  → New shard starts simulating
  → Event bus records transfer
```

### 10.2 AI and Pathfinding

**Problem:** Mobs need to pathfind across regions

**Solution:** Shared Navmesh

```
Each region computes navmesh for its area
Border navmeshes are shared between neighbors
Pathfinding:
  - Start: Local A* within region
  - Crosses border: Request path from neighbor
  - Neighbor returns path segment
  - Stitch segments together
```

### 10.3 Item Drops and Pickup

Items are entities. When dropped:
1. Spawned on current shard
2. If near border, replicated to neighbor (read-only)
3. Player can pick up from either shard
4. First pickup wins (atomic compare-and-swap)

---

## 11. Block Interaction Model

### 11.1 No Permission Checks on Action

**v1.0:** Check ownership → deny if uncertain
**v2.0:** Allow → log → resolve conflicts

**Block Break:**
```
1. Player clicks block
2. Shard immediately breaks it (no waiting)
3. Event published to bus
4. If conflict detected, rollback sent
```

**Block Place:**
```
1. Player places block
2. Shard immediately places it
3. Event published to bus
4. Neighbor shards validate (e.g., redstone circuits)
```

**Redstone:**
- Redstone updates stay within region when possible
- Cross-region redstone: event bus propagates updates
- Deterministic: same input always produces same output

### 11.2 Why No Permission Checks Work

1. **Chunks must be loaded** to interact with blocks
2. **Only owning shard loads** chunks (others have borders only)
3. **If chunk is loaded**, you must be on the owning shard
4. **If you're not on owning shard**, chunk isn't loaded, can't interact

The only edge case: two players on the exact border. Solved by:
- Border chunks are read-only for non-owners
- Players are routed to one shard or the other, never both

---

## 12. Fault Tolerance & Recovery

### 12.1 Shard Failure

```
Shard crashes
  ↓
Coordinator detects (heartbeat timeout)
  ↓
Remove shard from consistent hash ring
  ↓
Neighboring shards take over regions
  ↓
Players reconnect to new shards
  ↓
New shard loads player state from Redis
  ↓
Minimal interruption (< 5 seconds)
```

**Data Safety:**
- All block changes in event log
- Player state in Redis (1-hour TTL)
- Chunk data in MinIO (persistent)
- No data loss on shard crash

### 12.2 Network Partition

```
Network partition (Shard A isolated)
  ↓
Shard A continues operating (split brain)
  ↓
Players on Shard A can still play
  ↓
When partition heals:
  - Replay event log
  - Resolve conflicts (last-write-wins)
  - Rollback where necessary
```

**Mitigation:**
- Maximum partition time: 30 seconds
- After 30s, isolated shard stops accepting players
- Event log replay handles reconciliation

### 12.3 Proxy Failure

```
Proxy crashes
  ↓
Player connection drops
  ↓
Player reconnects to different proxy
  ↓
New proxy asks coordinator for shard
  ↓
Player rejoins same shard
  ↓
No state loss (state is in Redis)
```

---

## 13. Performance Architecture

### 13.1 Target Metrics

| Metric | Target | v1.0 |
|---|---|---|
| Chunk load time | < 50ms | > 500ms |
| Player handoff | < 50ms | > 500ms |
| Block break latency | < 10ms | > 100ms |
| Players per shard | 2,000 | 200 |
| Total players | 100,000 | 1,000 |
| Tick rate | 20 TPS | 15 TPS |

### 13.2 Optimization Strategies

**Memory:**
- Object pooling for packets, entities, chunks
- Off-heap memory for chunk data (ByteBuffer)
- Region-based garbage collection

**CPU:**
- Actor model: one thread per region
- Lock-free data structures
- SIMD for lighting calculation

**Network:**
- Packet batching (send every 50ms)
- Delta compression for chunk updates
- Priority queue: nearby chunks first

**Storage:**
- LSM-tree for event log (fast writes)
- B-tree for chunk index (fast lookups)
- Compression: LZ4 for chunks, Snappy for events

### 13.3 Caching Hierarchy

```
L1: In-Memory (shard local)
  - Active chunks
  - Player states
  - Entity positions
  
L2: Redis Cluster
  - Recently active chunks
  - Player sessions
  - Hot entity data
  
L3: MinIO (S3)
  - All chunks
  - Event snapshots
  - Player history
  
L4: Cold Storage
  - Backups
  - Archived worlds
```

---

## 14. Data Storage Strategy

### 14.1 Why Not Redis for Chunks?

**v1.0 Mistake:** Stored chunks in Redis as strings

**Problems:**
- Redis is memory-based, expensive for large worlds
- String serialization is 10x larger than binary
- No compression
- Single-threaded bottleneck

**v2.0 Solution:**
- **MinIO/S3** for chunk storage (cheap, scalable)
- **Redis** for hot cache and metadata
- **Binary protobuf** for serialization
- **LZ4 compression** for chunks

### 14.2 Storage Format

**Chunk File:**
```
chunk/{world}/{cx}/{cz}.bin.lz4
  → 16KB compressed (vs 256KB uncompressed)
  → Stored in MinIO
  → Loaded on demand
```

**Event Log:**
```
events/{world}/{region}/{timestamp}.bin
  → Append-only
  → Compacted hourly
  → Retained for 7 days
```

**Player Data:**
```
players/{uuid}/current.pb
  → Protobuf format
  → Updated every 5 seconds
  → TTL in Redis: 1 hour
```

### 14.3 Backup Strategy

```
Hourly: Snapshot event log to MinIO
Daily: Full world snapshot to MinIO
Weekly: Archive to cold storage

Recovery:
  - Load latest world snapshot
  - Replay event log from snapshot time
  - Full recovery in < 5 minutes
```

---

## 15. Deployment Model

### 15.1 Docker Compose (Development)

```yaml
version: '3.8'
services:
  nats:
    image: nats:2-alpine
    command: "--js --store_dir /data"
    
  redis:
    image: redis:7-alpine
    
  minio:
    image: minio/minio
    command: "server /data"
    
  coordinator:
    build: ./coordinator
    replicas: 3
    
  proxy:
    build: ./proxy
    replicas: 2
    ports:
      - "25565:25565"
      
  shard:
    build: ./shard
    replicas: 4
```

### 15.2 Kubernetes (Production)

```yaml
# Coordinator: StatefulSet with 3 replicas
# Proxy: Deployment with HPA (10-100 replicas)
# Shard: StatefulSet with anti-affinity
# NATS: StatefulSet with persistent volumes
# Redis: Redis Cluster with 6 nodes
# MinIO: Distributed mode with 4 nodes
```

### 15.3 Scaling

**Horizontal:**
```
Add shard:
  1. Start new shard process
  2. Register with coordinator
  3. Consistent hash ring updates
  4. Regions migrate in background
  5. Old shards unload migrated regions
```

**Vertical:**
```
Increase shard capacity:
  1. Adjust region allocation
  2. Restart shard with new config
  3. No data migration needed
```

---

## 16. Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
- [ ] Coordinator service (Go) with Raft consensus
- [ ] Deterministic chunk allocation
- [ ] Basic proxy (Go) with Mojang auth
- [ ] NATS JetStream setup

### Phase 2: Core Shard (Week 3-4)
- [ ] Minestom integration with region manager
- [ ] Async chunk loading from MinIO
- [ ] Event bus integration
- [ ] Basic player movement

### Phase 3: State Sync (Week 5-6)
- [ ] Player state storage (Redis + MinIO)
- [ ] Entity system with ownership
- [ ] Block change event sourcing
- [ ] Cross-region event propagation

### Phase 4: Lighting & Visuals (Week 7-8)
- [ ] Three-tier lighting engine
- [ ] Async lighting calculation
- [ ] Light update packets
- [ ] Chunk border seamlessness

### Phase 5: Gameplay (Week 9-10)
- [ ] Block breaking/placing (optimistic)
- [ ] Inventory management
- [ ] Crafting system
- [ ] Redstone (cross-region)

### Phase 6: Polish (Week 11-12)
- [ ] Entity AI with cross-region pathfinding
- [ ] Grief protection (event log rollback)
- [ ] Performance optimization
- [ ] Monitoring and metrics

### Phase 7: Production (Week 13+)
- [ ] Kubernetes deployment
- [ ] Load testing (10k, 50k, 100k players)
- [ ] Chaos engineering
- [ ] Documentation

---

## Appendix A: Comparison with v1.0

| Aspect | v1.0 | v2.0 |
|---|---|---|
| Chunk Ownership | Centralized coordinator | Deterministic hash |
| Block Breaking | Ask permission first | Optimistic + rollback |
| Lighting | Synchronous | Async 3-tier |
| State Sync | Polling | Event-sourced |
| Storage | Redis strings | MinIO binary |
| Scaling | Vertical | Horizontal |
| Fault Tolerance | Graceful degradation | Full recovery |
| Vanillaness | ~70% | ~99% |

## Appendix B: Technology Choices

| Component | Choice | Alternative | Why |
|---|---|---|---|
| Language | Go + Java | Rust + Java | Go for infra, Java for game |
| Message Bus | NATS | Kafka | Lighter, faster |
| Consensus | Raft (Etcd) | Paxos | Simpler |
| Storage | MinIO | Ceph | Simpler |
| Cache | Redis | Memcached | Persistence |
| Proxy | Custom Go | HAProxy | Minecraft-aware |
| Game Engine | Minestom | Custom | Proven |

---

*Document Version: 2.0.0*
*Last Updated: 2026-04-21*
*Status: Architecture Complete - Ready for Implementation*
