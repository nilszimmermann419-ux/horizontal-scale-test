# ShardedMC v2.0 Testing Guide

This directory contains integration tests for ShardedMC v2.0 distributed Minecraft server.

## Test Structure

- `coordinator/internal/registry/registry_test.go` - Go tests for shard registry
- `coordinator/pkg/api/server_test.go` - Go tests for coordinator API
- `proxy/internal/connection/client_test.go` - Go tests for client connections
- `proxy/internal/router/router_test.go` - Go tests for shard routing
- `proxy/pkg/protocol/packet_test.go` - Go tests for Minecraft protocol
- `shard/src/test/java/com/shardedmc/shard/ShardIntegrationTest.java` - Java tests for shard server

## Running Unit Tests

### Coordinator Tests

```bash
cd coordinator
go test -v ./internal/registry/... ./pkg/api/...
```

Individual test suites:
```bash
go test -v -run TestRegisterShard
go test -v -run TestHeartbeat
go test -v -run TestRegionAllocation
go test -v -run TestChunkOwnershipLookup
go test -v -run TestPlayerRouting
```

### Proxy Tests

```bash
cd proxy
go test -v ./internal/connection/... ./internal/router/... ./pkg/protocol/...
```

Individual test suites:
```bash
go test -v -run TestClientConnection
go test -v -run TestPacketForwarding
go test -v -run TestShardRouting
go test -v -run TestMojangAuthMock
```

### Shard Tests

```bash
cd shard
./gradlew test
```

Individual test classes:
```bash
./gradlew test --tests "com.shardedmc.shard.ShardIntegrationTest.testChunkLoading"
./gradlew test --tests "com.shardedmc.shard.ShardIntegrationTest.testBlockBreaking"
./gradlew test --tests "com.shardedmc.shard.ShardIntegrationTest.testPlayerStateSaveLoad"
./gradlew test --tests "com.shardedmc.shard.ShardIntegrationTest.testEventPublishing"
```

## Running Integration Tests

Integration tests require infrastructure services (Redis, NATS, MinIO).

### Start Infrastructure

```bash
make run-infra
# or
docker-compose up -d nats redis minio
```

### Run All Tests

```bash
make test
```

This runs tests for all components:
- Coordinator Go tests
- Proxy Go tests
- Shard Java tests

### Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Run tests against running services
cd coordinator && go test -v ./...
cd proxy && go test -v ./...
cd shard && ./gradlew test
```

## Starting the Full Stack

### Quick Start (Local Binaries)

Build all components first:
```bash
make build
```

Start all services:
```bash
./bin/start-all.sh
```

This script:
1. Starts infrastructure (NATS, Redis, MinIO)
2. Starts coordinator
3. Starts proxy
4. Starts shard server

### Stop All Services

```bash
./bin/stop-all.sh
```

This script:
1. Stops coordinator process
2. Stops proxy process
3. Stops Java shard process
4. Stops Docker infrastructure

### Docker Compose (Full Stack)

```bash
# Build and start all services
docker-compose up -d

# Scale shards
docker-compose up -d --scale shard-1=3

# View logs
docker-compose logs -f coordinator
docker-compose logs -f proxy
docker-compose logs -f shard-1
```

## Connecting with Minecraft Client

### Server Address

Connect your Minecraft client to:
```
localhost:25565
```

### Supported Versions

- Java Edition 1.19.1+ (protocol 760+)

### Offline Mode

The server runs in offline mode by default for development. Use any username to connect.

### Testing Connection

1. Start the full stack: `./bin/start-all.sh`
2. Open Minecraft Java Edition
3. Add server: `localhost:25565`
4. Connect

### Verify Shard Assignment

Check coordinator logs to see player routing:
```bash
docker-compose logs -f coordinator
```

Expected output:
```
Player <username> moved to chunk (x, z), assigned to shard <shard-id>
```

## Test Coverage

### Coordinator
- Shard registration and deregistration
- Heartbeat handling and timeout detection
- Region allocation via consistent hashing
- Chunk ownership lookup
- Player routing and position tracking

### Proxy
- Client connection handling
- Minecraft protocol packet forwarding
- Shard routing via coordinator
- Offline UUID generation (Mojang auth mock)
- Handshake and login state machine

### Shard
- Chunk loading and generation
- Block breaking and placing
- Player state save/load to Redis
- Event publishing via NATS
- Region ownership management

## Troubleshooting

### Port Already in Use

```bash
# Find process using port 25565
lsof -i :25565

# Kill process
kill -9 <PID>
```

### Infrastructure Not Starting

```bash
# Check Docker status
docker-compose ps

# Restart infrastructure
docker-compose down
docker-compose up -d nats redis minio
```

### Test Failures

```bash
# Run with verbose output
go test -v ./...

# Run specific failing test
go test -v -run TestHeartbeat

# Check logs
cat /tmp/shardedmc-test.log
```

## Environment Variables

### Coordinator
- `PORT` - gRPC port (default: 50051)
- `HTTP_PORT` - HTTP port (default: 8080)
- `HEARTBEAT_TIMEOUT` - Shard timeout (default: 30s)

### Proxy
- `LISTEN_ADDR` - Minecraft client listen address (default: :25565)
- `COORDINATOR_ADDR` - Coordinator gRPC address (default: localhost:50051)

### Shard
- `SHARD_ID` - Unique shard identifier
- `PORT` - Server port (default: 25565)
- `COORDINATOR_HOST` - Coordinator hostname
- `COORDINATOR_PORT` - Coordinator port (default: 50051)
- `NATS_URL` - NATS connection URL
- `REDIS_URL` - Redis connection URL
- `MINIO_URL` - MinIO connection URL
