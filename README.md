# ShardedMC v2.0
## Seamless Vanilla Experience on Horizontally Scaled Minecraft Servers

**Status:** Architecture Complete - Awaiting Implementation

---

## Overview

ShardedMC is a ground-up redesign of horizontally-scalable Minecraft server architecture. Unlike v1.0 which attempted to retrofit sharding onto a single-server model, v2.0 embraces a true distributed systems approach with event-sourced state synchronization, deterministic chunk allocation, and optimistic block breaking.

**Target:** 100% vanilla parity with 100,000+ concurrent players

## Why v2.0?

The v1.0 codebase had fundamental design flaws:

1. **Chunk ownership was too rigid** - denied actions on any uncertainty, making the game unplayable
2. **Lighting was computed synchronously** - 98,304 block lookups per chunk, causing dark worlds and timeouts
3. **State sync was best-effort** - no guarantees, no conflict resolution
4. **Data storage used wrong tools** - Redis for chunk data, strings for serialization
5. **Error handling was primitive** - silent failures, no graceful degradation

See [docs/ROOT_CAUSE_ANALYSIS.md](docs/ROOT_CAUSE_ANALYSIS.md) for the full analysis.

## Architecture

See [docs/ARCHITECTURE_v2.md](docs/ARCHITECTURE_v2.md) for the comprehensive v2.0 architecture document.

### Key Innovations

- **Event-Sourced Synchronization**: All state changes are events, not polling
- **Deterministic Chunk Allocation**: Consistent hashing instead of central coordinator
- **Optimistic Block Breaking**: Break first, validate asynchronously, rollback if needed
- **Three-Tier Async Lighting**: Client prediction + quick pass + deep validation
- **Seamless Sub-50ms Handoffs**: Predictive pre-loading with double-buffered state
- **Proper Storage Hierarchy**: MinIO for chunks, Redis for cache, NATS for events

## Technology Stack

| Component | Technology |
|---|---|
| Proxy | Go (custom Minecraft-aware TCP proxy) |
| Coordinator | Go + Raft consensus |
| Shard | Minestom (modified) |
| Message Bus | NATS JetStream |
| Cache | Redis Cluster |
| Chunk Storage | MinIO (S3-compatible) |

## Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
- Coordinator service with Raft consensus
- Deterministic chunk allocation
- Basic proxy with Mojang auth
- NATS JetStream setup

### Phase 2: Core Shard (Week 3-4)
- Minestom integration with region manager
- Async chunk loading from MinIO
- Event bus integration
- Basic player movement

### Phase 3: State Sync (Week 5-6)
- Player state storage (Redis + MinIO)
- Entity system with ownership
- Block change event sourcing
- Cross-region event propagation

### Phase 4: Lighting & Visuals (Week 7-8)
- Three-tier lighting engine
- Async lighting calculation
- Light update packets
- Chunk border seamlessness

### Phase 5: Gameplay (Week 9-10)
- Block breaking/placing (optimistic)
- Inventory management
- Crafting system
- Redstone (cross-region)

### Phase 6: Polish (Week 11-12)
- Entity AI with cross-region pathfinding
- Grief protection (event log rollback)
- Performance optimization
- Monitoring and metrics

### Phase 7: Production (Week 13+)
- Kubernetes deployment
- Load testing (10k, 50k, 100k players)
- Chaos engineering
- Documentation

## License

MIT
