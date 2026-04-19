# Architecture Documentation

## Overview

The system uses a shard-based architecture to distribute a single Minecraft world across multiple Minestom server instances.

## Components

### Central Coordinator

- Manages shard registration and health monitoring
- Allocates chunks to shards dynamically
- Routes players to appropriate shards
- Orchestrates player handoffs between shards

### Minestom Shards

- Run actual game simulation
- Own specific chunk regions
- Handle player interactions
- Communicate with Coordinator for cross-shard operations

### Redis

- Shared state store for chunk assignments, player locations, entity states
- Pub/sub message bus for cross-shard events
- Cache for hot data

## Data Flow

### Player Join

1. Player connects to Proxy
2. Proxy queries Coordinator for shard assignment
3. Coordinator checks Redis for player's last location
4. Returns nearest healthy shard address
5. Player connects directly to shard

### Chunk Boundary Transition

1. Player approaches boundary
2. Current shard notifies Coordinator
3. Coordinator triggers chunk pre-load on target shard
4. Handoff protocol executes:
   - Freeze player on source
   - Serialize entity state
   - Transfer to target shard
   - Spawn player with state
   - Resume simulation
5. Update player location in Redis

## Scaling

### Horizontal Scaling

Add more shards to increase capacity:

```bash
docker-compose up -d --scale shard=5
```

### Dynamic Rebalancing

Coordinator monitors shard load and migrates chunks automatically when:
- Shard utilization > 80%
- Shard utilization < 30%
- Shard failure detected

## Fault Tolerance

### Shard Failure

1. Coordinator detects missed heartbeats
2. Marks shard as unhealthy
3. Reassigns chunks to healthy shards
4. Players reconnect to new shards
5. State restored from Redis

### Redis Failure

1. Shards switch to degraded mode
2. Use cached state for 30 seconds
3. Queue updates in memory
4. Reconnect with exponential backoff
