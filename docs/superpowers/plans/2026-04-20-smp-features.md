# Production SMP Server Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all critical SMP server features based on research from 2b2t, DonutSMP, Hypixel - entity management, redstone optimization, security, and performance monitoring.

**Architecture:** Minestom-based Java shards with Go coordinator. Each feature implemented as modular subsystem with event-driven architecture.

**Tech Stack:** Java 21 + Minestom, Go, Redis, Protocol Buffers

---

## Phase 1: Entity Management (Critical)

### Task 1: Entity Spawn Limiter

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/EntityLimiter.java`
- Modify: `shard/src/main/java/com/shardedmc/shard/ShardServer.java`

Implement configurable entity spawn limits per type:
- Monsters: 20 per player
- Animals: 5 per player
- Water mobs: 2 per player
- Ambient (bats): 1 per player
- Track counts per chunk
- Cancel spawns when limits exceeded
- Configurable via YAML/properties

### Task 2: Entity Activation Ranges

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/EntityActivationManager.java`

Implement entity activation range system:
- Animals: 16 blocks (tick only within range)
- Monsters: 24 blocks
- Villagers: 16 blocks
- Misc (items/XP): 8 blocks
- Water mobs: 8 blocks
- Entities outside range skip AI ticks
- Save 50%+ entity CPU usage

### Task 3: Villager Optimization

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/VillagerOptimizer.java`

Implement villager-specific optimizations:
- Skip tick for villagers not trading (90%+ of time)
- Reduce POI acquisition checks (every 120 ticks vs every tick)
- Cache trade offers instead of recalculating
- Lobotomize villagers in trading halls (no AI, just trades)
- Count villagers per chunk, hard cap at 50

### Task 4: Item Despawn Optimization

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/ItemDespawnManager.java`

Implement fast item despawn for trash items:
- Cobblestone, dirt, netherrack despawn: 15 seconds (vs 5 minutes)
- Leaves, saplings: 30 seconds
- Common blocks: 1 minute
- Valuable items: normal 5 minutes
- Merge radius: 3.5 blocks
- Configurable item type → despawn time mapping

---

## Phase 2: Redstone & Farm Optimization (High)

### Task 5: Hopper Optimization

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/HopperOptimizer.java`

Implement hopper tick reduction:
- Transfer: every 8 ticks (vs vanilla 1) = 87.5% reduction
- Check: every 8 ticks (vs vanilla 1)
- Move 3 items at once (vs 1)
- Skip InventoryMoveItemEvent if no plugins need it
- Ignore occluding blocks (hoppers inside full blocks)
- Water streams as alternative to hopper chains

### Task 6: Redstone Lag Detection

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/RedstoneLagDetector.java`

Implement lag machine detection:
- Track redstone updates per chunk per second
- Alert when chunk exceeds 1000 updates/sec
- Auto-disable redstone in lagging chunks after warning
- Track observer/piston counts per chunk (limit: 256)
- Log repeat offender chunks for admin review

### Task 7: Alternate Current Redstone

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/OptimizedRedstone.java`

Implement optimized redstone algorithm:
- BFS propagation instead of vanilla recursive updates
- Eliminate redundant block updates
- Batch updates per tick
- 5-10x performance improvement for large circuits
- Toggle between vanilla and optimized modes

---

## Phase 3: Security (High)

### Task 8: Duping Prevention

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/DupePrevention.java`

Implement anti-dupe measures:
- Validate NBT tags (no illegal enchantments)
- Overstacked item detection (>64 stacks)
- Illegal data value checking
- Donkey chest disable (prevent donkey dupes)
- Book page size limit (255 bytes max)
- Portal cooldown (15 seconds)
- Auto-remove hacked items on inventory open

### Task 9: Combat Logging NPCs

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/CombatLogger.java`

Implement combat logging prevention:
- Track combat state (attacked within last 30 seconds)
- On disconnect during combat: spawn NPC with player skin
- NPC has player health, inventory, armor
- If NPC dies: player dies on reconnect
- If NPC survives 30s: player reconnects normally
- NPC drops all items on death

### Task 10: Grief Protection

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/GriefProtection.java`

Implement grief rollback system:
- Log all block changes to Redis (player, time, block type)
- Store 7 days of history
- /rollback command (rollback last X minutes in radius)
- /inspect command (see who placed/broke a block)
- Async logging (don't block main thread)
- Rollback chest contents, signs, etc.

---

## Phase 4: Performance & Monitoring (Medium)

### Task 11: Chunk Pregeneration

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/ChunkPregenerator.java`

Implement world pregeneration:
- /pregen command (radius from spawn)
- Generate chunks in spiral pattern
- Show progress (chunks generated, % complete, ETA)
- Pause/resume capability
- Low priority to avoid lagging players
- Store pregen status in Redis

### Task 12: Performance Monitor

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/PerformanceMonitor.java`

Implement real-time monitoring:
- Track TPS (ticks per second)
- Track MSPT (milliseconds per tick)
- Entity counts per type
- Chunk counts (loaded, active)
- Memory usage (heap, off-heap)
- GC pause times
- Export to Prometheus metrics endpoint
- Alert when TPS < 18 for > 60 seconds

### Task 13: Auto-Restart

**Files:**
- Create: `shard/src/main/java/com/shardedmc/shard/AutoRestart.java`

Implement scheduled restart system:
- Configurable restart interval (default: 12 hours)
- Warning broadcasts: 10min, 5min, 1min, 30sec
- Kick all players with message before restart
- Restart script integration
- Health check: restart if TPS < 10 for > 5 minutes
- Graceful shutdown (save all chunks)

---

## Implementation Order

1. EntityLimiter (Task 1) - Most critical for performance
2. EntityActivationManager (Task 2) - #2 performance impact
3. HopperOptimizer (Task 5) - Major farm lag reduction
4. VillagerOptimizer (Task 3) - Villagers are expensive
5. ItemDespawnManager (Task 4) - Reduce entity counts
6. DupePrevention (Task 8) - Security critical
7. CombatLogger (Task 9) - SMP essential
8. RedstoneLagDetector (Task 6) - Prevent abuse
9. GriefProtection (Task 10) - Admin essential
10. PerformanceMonitor (Task 12) - Monitor improvements
11. ChunkPregenerator (Task 11) - Pre-launch optimization
12. AutoRestart (Task 13) - Stability
13. OptimizedRedstone (Task 7) - Advanced optimization

---

## Testing

After each task:
- Verify compilation: `./gradlew :shard:compileJava`
- Test with bot: `node tests/bot-stress-test.js --max-bots 5`
- Check TPS/memory impact
- Commit with descriptive message

## Configuration

All features configurable via:
```yaml
# shard-config.yml
entity-limits:
  monsters: 20
  animals: 5
  water: 2
  ambient: 1

hopper-optimization:
  transfer-ticks: 8
  check-ticks: 8
  items-per-transfer: 3

redstone:
  max-updates-per-chunk: 1000
  observer-limit-per-chunk: 256

villager:
  max-per-chunk: 50
  poi-check-interval: 120

combat-log:
  combat-duration: 30
  npc-survival-time: 30

auto-restart:
  interval-hours: 12
  tps-threshold: 10
  tps-duration: 5
```
