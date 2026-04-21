# ShardedMC v2.0 API Documentation

## Table of Contents

1. [gRPC API](#grpc-api)
2. [Event Bus](#event-bus)
3. [REST API](#rest-api)
4. [Authentication](#authentication)
5. [Error Codes](#error-codes)
6. [Rate Limits](#rate-limits)

---

## gRPC API

The coordinator exposes a gRPC API on port `50051` (configurable via `GRPC_PORT`).

### CoordinatorService

```protobuf
service CoordinatorService {
  rpc RegisterShard(ShardRegistrationRequest) returns (ShardRegistrationResponse);
  rpc Heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  rpc GetChunkOwner(GetChunkOwnerRequest) returns (GetChunkOwnerResponse);
  rpc GetRegionMap(GetRegionMapRequest) returns (GetRegionMapResponse);
  rpc GetPlayerShard(GetPlayerShardRequest) returns (GetPlayerShardResponse);
  rpc RecordPlayerPosition(RecordPlayerPositionRequest) returns (RecordPlayerPositionResponse);
}
```

#### RegisterShard

Registers a new shard with the coordinator and receives region allocations.

**Request:**
```protobuf
message ShardRegistrationRequest {
  ShardInfo shard = 1;
}

message ShardInfo {
  string id = 1;
  string address = 2;
  int32 port = 3;
  int32 capacity = 4;
  int32 player_count = 5;
  double load = 6;
  bool healthy = 7;
  repeated RegionCoord regions = 8;
}
```

**Response:**
```protobuf
message ShardRegistrationResponse {
  bool success = 1;
  string coordinator_id = 2;
}
```

**Example (Go):**
```go
client := pb.NewCoordinatorServiceClient(conn)
resp, err := client.RegisterShard(ctx, &pb.ShardRegistrationRequest{
    Shard: &pb.ShardInfo{
        Id:       "shard-1",
        Address:  "10.0.0.5",
        Port:     25566,
        Capacity: 2000,
        Regions:  []*pb.RegionCoord{{X: 0, Z: 0}, {X: 1, Z: 0}},
    },
})
```

#### Heartbeat

Shards must send heartbeats every 10-30 seconds to maintain their healthy status.

**Request:**
```protobuf
message HeartbeatRequest {
  string shard_id = 1;
  double cpu_usage = 2;
  double memory_usage = 3;
  int32 player_count = 4;
  double load = 5;
  bool healthy = 6;
  repeated RegionCoord regions = 7;
}
```

**Response:**
```protobuf
message HeartbeatResponse {
  bool accepted = 1;
  repeated string commands = 2;
}
```

**Example (Go):**
```go
resp, err := client.Heartbeat(ctx, &pb.HeartbeatRequest{
    ShardId:     "shard-1",
    CpuUsage:    45.2,
    MemoryUsage: 1024.0,
    PlayerCount: 342,
    Load:        0.17,
    Healthy:     true,
})
```

#### GetChunkOwner

Determines which shard owns a specific chunk using consistent hashing.

**Request:**
```protobuf
message GetChunkOwnerRequest {
  ChunkCoord coord = 1;
}
```

**Response:**
```protobuf
message GetChunkOwnerResponse {
  ShardInfo shard = 1;
  bool found = 2;
}
```

#### GetRegionMap

Returns the complete region-to-shard mapping.

**Request:**
```protobuf
message GetRegionMapRequest {}
```

**Response:**
```protobuf
message GetRegionMapResponse {
  map<string, ShardInfo> region_map = 1;
}
```

#### GetPlayerShard

Returns the shard currently assigned to a player.

**Request:**
```protobuf
message GetPlayerShardRequest {
  string player_uuid = 1;
}
```

**Response:**
```protobuf
message GetPlayerShardResponse {
  ShardInfo shard = 1;
  bool found = 2;
}
```

#### RecordPlayerPosition

Updates player position and reassigns them to the appropriate shard if needed.

**Request:**
```protobuf
message RecordPlayerPositionRequest {
  string player_uuid = 1;
  Vec3d position = 2;
  string shard_id = 3;
}
```

**Response:**
```protobuf
message RecordPlayerPositionResponse {
  bool success = 1;
}
```

---

## Event Bus

ShardedMC uses NATS JetStream as its event bus for cross-shard communication.

### Connection

```
NATS_URL=nats://localhost:4222
```

### Topics

| Topic Pattern | Description | Payload |
|---------------|-------------|---------|
| `world.blocks.{regionX}.{regionZ}` | Block changes in a region | `BlockChangeEvent` |
| `world.entities.{regionX}.{regionZ}` | Entity spawns/moves in a region | `EntitySpawnEvent`, `EntityMoveEvent` |
| `world.players.{playerUUID}` | Player-specific events | `PlayerJoinEvent`, `PlayerLeaveEvent`, `PlayerMoveEvent` |
| `world.global` | Global world events (time, weather) | `WorldEvent` |
| `shard.heartbeat` | Shard health broadcasts | `HeartbeatEvent` |
| `shard.commands.{shardId}` | Coordinator commands to shards | `CommandEvent` |

### Message Formats

All events use the `WorldEvent` envelope:

```protobuf
message WorldEvent {
  int64 timestamp = 1;
  int64 sequence = 2;
  string shard_id = 3;

  oneof payload {
    BlockChangeEvent block_change = 10;
    EntitySpawnEvent entity_spawn = 11;
    EntityMoveEvent entity_move = 12;
    PlayerJoinEvent player_join = 13;
    PlayerLeaveEvent player_leave = 14;
    PlayerMoveEvent player_move = 15;
  }
}
```

#### BlockChangeEvent

```protobuf
message BlockChangeEvent {
  int32 x = 1;
  int32 y = 2;
  int32 z = 3;
  string block_id = 4;
  string player_id = 5;
}
```

#### EntitySpawnEvent

```protobuf
message EntitySpawnEvent {
  string uuid = 1;
  string type = 2;
  double x = 3;
  double y = 4;
  double z = 5;
}
```

#### EntityMoveEvent

```protobuf
message EntityMoveEvent {
  string uuid = 1;
  double x = 2;
  double y = 3;
  double z = 4;
  double vx = 5;
  double vy = 6;
  double vz = 7;
}
```

#### PlayerJoinEvent

```protobuf
message PlayerJoinEvent {
  string uuid = 1;
  string username = 2;
  string shard_id = 3;
}
```

#### PlayerLeaveEvent

```protobuf
message PlayerLeaveEvent {
  string uuid = 1;
  string shard_id = 2;
}
```

#### PlayerMoveEvent

```protobuf
message PlayerMoveEvent {
  string uuid = 1;
  double x = 2;
  double y = 3;
  double z = 4;
  float yaw = 5;
  float pitch = 6;
}
```

### Consumer Groups

Shards consume events using durable subscriptions:

```go
// Subscribe to block changes for regions this shard owns
sub, err := js.Subscribe("world.blocks.0.0", handler,
    nats.Durable("shard-1-blocks"),
    nats.AckExplicit(),
)
```

### Delivery Guarantees

- **At-least-once delivery** with explicit ACK
- Durable subscriptions survive restarts
- JetStream persists events for replay

---

## REST API

The coordinator exposes HTTP endpoints on port `8080`.

### Health Endpoints

#### GET /health

Returns overall coordinator health status.

**Response:**
```json
{
  "status": "healthy",
  "shards": 4
}
```

#### GET /health/shards

Returns detailed information about all registered shards.

**Response:**
```json
[
  {
    "id": "shard-1",
    "address": "10.0.0.5",
    "port": 25566,
    "capacity": 2000,
    "player_count": 342,
    "load": 0.17,
    "healthy": true
  }
]
```

#### GET /health/regions

Returns the region allocation map.

**Response:**
```json
{
  "0:0": "shard-1",
  "1:0": "shard-2"
}
```

### Metrics Endpoints

#### GET /metrics

Prometheus metrics endpoint.

**Key Metrics:**

| Metric | Type | Description |
|--------|------|-------------|
| `shards_total` | Gauge | Total registered shards |
| `shards_healthy` | Gauge | Healthy shards |
| `regions_total` | Gauge | Allocated regions |
| `region_allocations_total` | Counter | Region allocation operations |
| `grpc_requests_total` | Counter | gRPC requests by method/status |
| `grpc_request_duration_seconds` | Histogram | gRPC request duration |
| `player_routes_total` | Counter | Player routing operations |

**Example Response (Prometheus format):**
```
# HELP shards_total Total number of registered shards
# TYPE shards_total gauge
shards_total 4

# HELP shards_healthy Number of healthy shards
# TYPE shards_healthy gauge
shards_healthy 4
```

---

## Authentication

### Mojang Authentication (Online Mode)

In production, ShardedMC supports Mojang/Yggdrasil authentication:

1. Client connects to proxy
2. Proxy validates session with Mojang session servers
3. Proxy extracts UUID and username from Mojang response
4. Player data is stored using Mojang UUID

**Configuration:**
```
AUTH_MODE=mojang
```

### Offline Mode

For development and private servers:

1. Client connects without Mojang verification
2. Proxy generates deterministic UUID from username
3. UUID format: `UUID.nameUUIDFromBytes("OfflinePlayer:<username>".getBytes())`

**Configuration:**
```
AUTH_MODE=offline
```

**Offline UUID Generation (Go):**
```go
func GenerateOfflineUUID(username string) string {
    input := "OfflinePlayer:" + username
    hash := md5.Sum([]byte(input))
    hash[6] = (hash[6] & 0x0F) | 0x30
    hash[8] = (hash[8] & 0x3F) | 0x80
    return fmt.Sprintf("%x-%x-%x-%x-%x",
        hash[0:4], hash[4:6], hash[6:8], hash[8:10], hash[10:16])
}
```

### Username Validation

Offline mode usernames must:
- Be 1-16 characters
- Contain only alphanumeric characters and underscores
- Be normalized to lowercase

---

## Error Codes

### gRPC Status Codes

| Code | Description | Common Causes |
|------|-------------|---------------|
| `OK` (0) | Success | - |
| `CANCELLED` (1) | Request cancelled by client | Client timeout |
| `UNKNOWN` (2) | Unknown error | Internal server error |
| `INVALID_ARGUMENT` (3) | Bad request parameters | Invalid shard ID, malformed coordinates |
| `DEADLINE_EXCEEDED` (4) | Request timeout | Coordinator overload, network issues |
| `NOT_FOUND` (5) | Resource not found | Shard not registered, player not found |
| `ALREADY_EXISTS` (6) | Resource already exists | Duplicate shard registration |
| `UNAVAILABLE` (14) | Service unavailable | Coordinator starting up, all shards unhealthy |
| `UNAUTHENTICATED` (16) | Authentication failed | Invalid credentials |

### HTTP Status Codes

| Code | Endpoint | Description |
|------|----------|-------------|
| 200 | All | Success |
| 503 | /health | Service unhealthy (no healthy shards) |

### Error Handling Best Practices

1. **Retry with exponential backoff** for `UNAVAILABLE` and `DEADLINE_EXCEEDED`
2. **Cache shard assignments** locally to reduce coordinator load
3. **Handle `NOT_FOUND` gracefully** - shard may have crashed, request re-registration
4. **Validate inputs before sending** to avoid `INVALID_ARGUMENT`

---

## Rate Limits

### Coordinator gRPC API

| Method | Rate Limit | Burst |
|--------|-----------|-------|
| `RegisterShard` | 1/minute | 3 |
| `Heartbeat` | 1/10 seconds | 2 |
| `GetChunkOwner` | 1000/second | 2000 |
| `GetRegionMap` | 10/second | 20 |
| `GetPlayerShard` | 500/second | 1000 |
| `RecordPlayerPosition` | 1000/second | 2000 |

### Proxy Connection Limits

| Limit | Default | Description |
|-------|---------|-------------|
| `MAX_CONNECTIONS` | 10000 | Total concurrent player connections |
| `CONNECTION_RATE_LIMIT` | 100 | New connections per second |

### Shard Limits

| Limit | Default | Description |
|-------|---------|-------------|
| `SHARD_MAX_PLAYERS` | 2000 | Maximum players per shard |
| `SHARD_REGION_SIZE` | 4 | Region size in chunks (4x4 = 16 chunks) |

### NATS Rate Limits

NATS JetStream provides backpressure. Publishers should:
- Monitor pending messages
- Apply backpressure when pending > 10000
- Use async publish for high-throughput scenarios

### Exceeding Rate Limits

When rate limits are exceeded:
- **gRPC**: Returns `UNAVAILABLE` status with retry-after header
- **HTTP**: Returns 429 Too Many Requests
- **NATS**: Publishers block until backlog clears
