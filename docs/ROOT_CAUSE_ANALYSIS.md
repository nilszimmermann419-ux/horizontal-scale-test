# Root Cause Analysis Report

## Issues Found

### 1. Cannot Break Blocks (CRITICAL)
**Location:** `shard/src/main/java/com/shardedmc/shard/ChunkOwnershipEnforcer.java:103`
**Root Cause:** The "fix" changed fallback from `return true` to `return false`. When the coordinator is unreachable, slow, or returns errors, ALL block modifications are denied. In a real-world scenario, network hiccups, Redis latency, or coordinator load will constantly trigger this fallback, making the game unplayable.

**Why it happens:**
- `coordinatorClient.requestChunkLoad()` is a blocking gRPC call wrapped in `.get()`
- Any timeout, network issue, or exception causes the catch block to fire
- The fallback denies ALL actions instead of being context-aware (game mode, permissions)

### 2. Lighting Engine Completely Dark (CRITICAL)
**Location:** `shard/src/main/java/com/shardedmc/shard/LightingEngine.java`
**Root Cause:** Multiple cascading failures:

a) **Performance catastrophe:** `computeHighestBlocks()` calls `getHighestBlockY()` for every column (16×16 = 256 columns). Each call scans from y=319 down to y=-64, checking every block. That's **98,304 block lookups per chunk** just for the highest block calculation. On chunk generation, this causes timeouts or silent failures.

b) **ThreadLocal data race:** The `reusableSkyLight` and `reusableBlockLight` ThreadLocal arrays are modified and then `Arrays.copyOf()` is called. While ThreadLocal is per-thread, if the same thread processes multiple chunks (which Minestom's event loop does), the array gets overwritten before the copy is made.

c) **Missing light initialization trigger:** `initializeChunk` is called from `SharedChunkLoader`, but there's no guarantee it's called before the chunk is sent to the client. If the chunk sends before lighting is calculated, the client sees default (dark) lighting.

d) **No sunlight propagation:** The sky light calculation is binary - either full light (15) above highest block, or 0 below. It doesn't account for:
   - Caves that should be dark
   - Overhangs that create shadows
   - Light decay through transparent blocks
   - Chunk boundaries where sky light should propagate

### 3. PlayerBoundaryMonitor Corrupted (COMPILATION ERROR)
**Location:** `shard/src/main/java/com/shardedmc/shard/PlayerBoundaryMonitor.java:287-404`
**Root Cause:** The file has DUPLICATE methods - two `fromJson()` implementations and the class structure is malformed. This causes compilation errors or runtime crashes. The manual JSON parsing was replaced with Gson, but the old manual parsing code was left behind, creating duplicate methods.

### 4. World Sync Ineffective
**Location:** `shard/src/main/java/com/shardedmc/shard/WorldSyncManager.java`
**Root Cause:** 
- Player sync spawns no actual entities on remote shards (just logs)
- Entity sync is one-way broadcast with no reconciliation
- Block sync uses string parsing instead of proper serialization
- No conflict resolution for simultaneous edits
- Redis pub/sub doesn't guarantee delivery or ordering

### 5. Chunk Serialization Inefficient
**Location:** `shard/src/main/java/com/shardedmc/shard/SharedChunkLoader.java:222-239`
**Root Cause:** Chunks are serialized as strings like `"x,y,z,blockName;x,y,z,blockName"` - extremely inefficient for large worlds. A single chunk with terrain can be megabytes of string data. Redis is not designed for this volume.

## Summary
The current architecture has fundamental design flaws:
1. **Chunk ownership model is too rigid** - denies actions on any uncertainty
2. **Lighting is computed synchronously** - blocks chunk loading, causes dark world
3. **State synchronization is best-effort** - no guarantees, no conflict resolution
4. **Data storage is wrong tool** - Redis for chunk data, strings for serialization
5. **Error handling is primitive** - silent failures, no graceful degradation

A complete architectural redesign is needed to achieve a seamless vanilla experience.
