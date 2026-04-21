# Changelog

## v2.0.0 (2026-04-21)

### Fixed
- **CRITICAL**: Block breaking now works instantly (optimistic locking)
- **CRITICAL**: World lighting is properly calculated (async three-tier engine)
- **CRITICAL**: Chunk loading is fast (<50ms) instead of blocking

### Architecture
- Complete rewrite from v1.0
- Event-sourced state synchronization
- Deterministic chunk allocation (consistent hashing)
- Optimistic gameplay (break first, validate async)
- Three-tier async lighting engine
- Sub-50ms seamless player handoffs

### Components
- Coordinator service (Go) - stateless, scalable
- Proxy service (Go) - Minecraft-aware packet forwarding
- Shard server (Java/Minestom) - game simulation
- NATS JetStream - event bus
- Redis - hot cache
- MinIO - chunk storage

### Gameplay
- Block breaking/placing (optimistic)
- Inventory management
- Crafting (2x2 + 3x3)
- Redstone (cross-region)
- Entity spawning
- Damage/health system
- Day/night cycle + weather

### Infrastructure
- Docker Compose setup
- Kubernetes manifests
- Prometheus metrics
- Structured logging
- Health checks
- Graceful shutdown

## v1.0.x (Pre-v2.0)
- Initial prototype
- Had critical bugs (dark world, couldn't break blocks)
- Deprecated in favor of v2.0
