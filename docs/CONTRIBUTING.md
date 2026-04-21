# Contributing to ShardedMC

Thank you for your interest in contributing to ShardedMC! This document outlines the process and guidelines for contributing.

## Table of Contents

1. [Development Setup](#development-setup)
2. [Code Style](#code-style)
3. [Testing Requirements](#testing-requirements)
4. [Pull Request Process](#pull-request-process)
5. [Architecture Decisions](#architecture-decisions)

## Development Setup

### Prerequisites

- **Go 1.21+** (for coordinator and proxy)
- **Java 21+** (for shard server)
- **Docker and Docker Compose** (for infrastructure)
- **Protocol Buffers compiler** (`protoc`)
- **Make**

### Quick Start

```bash
# Clone the repository
git clone https://github.com/shardedmc/v2.git
cd v2

# Copy environment variables
cp .env.example .env

# Build all components
make build

# Run tests
make test

# Start infrastructure for development
make run-infra
```

### IDE Configuration

#### Go (VS Code)

Install the following extensions:
- Go (golang.go)
- Go Test Explorer

Enable these settings:
```json
{
  "gopls": {
    "formatting.gofumpt": true,
    "ui.diagnostic.vulncheck": true
  }
}
```

#### Java (IntelliJ IDEA)

- Import the `shard/build.gradle` file
- Enable annotation processing
- Set code style to Google Java Format

## Code Style

### Go

We follow standard Go conventions:

- **gofmt**: All Go code must be formatted with `gofmt`
- **golint**: Code should pass `golint` without warnings
- **go vet**: Code should pass `go vet` without issues
- **Line length**: No hard limit, but keep under 120 characters when possible
- **Error handling**: Always check errors, wrap with context using `fmt.Errorf("...: %w", err)`

**Example:**
```go
package example

import "fmt"

// ProcessChunk handles chunk data processing.
// It returns an error if the chunk is invalid or processing fails.
func ProcessChunk(data []byte) (*Chunk, error) {
    if len(data) == 0 {
        return nil, fmt.Errorf("empty chunk data")
    }

    chunk, err := parseChunk(data)
    if err != nil {
        return nil, fmt.Errorf("parse chunk: %w", err)
    }

    return chunk, nil
}
```

### Java (Shard Server)

We follow a modified Google Java Style:

- **Indentation**: 4 spaces (not 2)
- **Line length**: 120 characters maximum
- **Imports**: No wildcard imports, organize imports
- **Null safety**: Use `Optional` instead of returning null
- **Logging**: Use SLF4J with appropriate log levels

**Example:**
```java
package com.shardedmc.shard.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ChunkProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkProcessor.class);

    /**
     * Processes chunk data and returns the parsed chunk.
     *
     * @param data the raw chunk data
     * @return Optional containing the parsed chunk, or empty if invalid
     */
    public Optional<Chunk> processChunk(byte[] data) {
        if (data == null || data.length == 0) {
            LOGGER.warn("Received empty chunk data");
            return Optional.empty();
        }

        try {
            return Optional.of(parseChunk(data));
        } catch (ChunkParseException e) {
            LOGGER.error("Failed to parse chunk", e);
            return Optional.empty();
        }
    }
}
```

### Protocol Buffers

- Use `proto3` syntax
- Package: `shardedmc.v2`
- Go package: `github.com/shardedmc/v2/shared/proto;shardedmc`
- Field numbers: sequential, no reuse
- Comments: Document every message and field

## Testing Requirements

### Coverage

- **Minimum coverage**: 70% for new code
- **Critical paths**: 90% (chunk allocation, player routing, state sync)
- **Integration tests**: Required for cross-component features

### Go Testing

```bash
# Run all coordinator tests
cd coordinator && go test ./...

# Run with coverage
cd coordinator && go test -cover ./...

# Run specific test
cd coordinator && go test ./pkg/api -run TestRegisterShard
```

**Test patterns:**
- Table-driven tests preferred
- Use `testify/assert` for assertions
- Mock external dependencies (NATS, Redis, MinIO)
- Name tests descriptively: `TestFunctionName_Scenario_ExpectedResult`

**Example:**
```go
func TestGetChunkOwner_ValidCoord_ReturnsShard(t *testing.T) {
    // Arrange
    registry := registry.NewShardRegistry()
    registry.RegisterShard(&registry.ShardInfo{ID: "shard-1"})
    server := api.NewCoordinatorServer(registry)

    // Act
    resp, err := server.GetChunkOwner(context.Background(), &pb.GetChunkOwnerRequest{
        ChunkX: 0, ChunkZ: 0,
    })

    // Assert
    assert.NoError(t, err)
    assert.NotNil(t, resp)
    assert.Equal(t, "shard-1", resp.ShardId)
}
```

### Java Testing

```bash
# Run all shard tests
cd shard && ./gradlew test

# Run specific test
cd shard && ./gradlew test --tests "com.shardedmc.shard.ShardIntegrationTest"
```

**Test patterns:**
- Use JUnit 5
- Mockito for mocking
- AssertJ for fluent assertions
- Name tests: `shouldExpectedResultWhenScenario`

**Example:**
```java
@Test
void shouldReturnChunkOwnerWhenChunkExists() {
    // Arrange
    ChunkCoord coord = new ChunkCoord(0, 0);
    ShardRegistry registry = new ShardRegistry();
    registry.registerShard(new ShardInfo("shard-1"));

    // Act
    Optional<ShardInfo> owner = allocator.getChunkOwner(coord, registry.getAllShards());

    // Assert
    assertThat(owner).isPresent();
    assertThat(owner.get().getId()).isEqualTo("shard-1");
}
```

### Integration Testing

Integration tests require running infrastructure:

```bash
# Start test infrastructure
docker-compose -f docker-compose.test.yml up -d

# Run integration tests
make test-integration

# Stop test infrastructure
docker-compose -f docker-compose.test.yml down
```

## Pull Request Process

1. **Fork and branch**: Create a feature branch from `main`
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make changes**: Follow code style and add tests

3. **Run tests locally**:
   ```bash
   make test
   make lint  # if available
   ```

4. **Commit**: Use conventional commits format
   ```
   feat: add optimistic block breaking
   fix: resolve race condition in chunk allocation
   docs: update API documentation
   test: add integration tests for player handoff
   refactor: simplify consistent hash implementation
   ```

5. **Push and create PR**:
   - Fill out the PR template
   - Link related issues
   - Include screenshots for UI changes
   - Ensure CI passes

6. **Review process**:
   - All PRs require at least one review
   - Address review feedback promptly
   - Squash commits before merging if requested

7. **Merge**: Maintainers will merge once approved and CI passes

### PR Checklist

- [ ] Tests added/updated
- [ ] Code follows style guidelines
- [ ] Documentation updated (if needed)
- [ ] CHANGELOG.md updated (if user-facing)
- [ ] No breaking changes (or clearly documented)
- [ ] CI passes

## Architecture Decisions

### Technology Choices

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Go** | Coordinator, Proxy | Excellent concurrency, small binaries, fast startup |
| **Java** | Shard Server | Minestom ecosystem, mature game dev tools |
| **NATS** | Message Bus | Lighter than Kafka, built-in persistence |
| **Redis** | Cache | Fast in-memory operations, TTL support |
| **MinIO** | Object Storage | S3-compatible, self-hosted, cheap |

### Design Principles

1. **Vanilla First**: Every feature must be indistinguishable from single-server vanilla Minecraft
2. **Eventual Consistency**: Players only care about their local view; global consistency can lag
3. **Fail Open**: Network issues shouldn't break gameplay; use cached data if live data unavailable
4. **Deterministic Over Coordinated**: Use math (consistent hashing) instead of consensus where possible
5. **Shard = Process**: One machine runs many shards; shards are lightweight

### Key Architectural Decisions

#### Why Consistent Hashing for Chunk Allocation?

Instead of a central coordinator making allocation decisions:
- Shards can compute ownership locally
- No single point of failure for chunk lookups
- Deterministic = testable, debuggable
- Natural load distribution

#### Why Optimistic Block Breaking?

Instead of asking permission before every block break:
- Eliminates coordinator bottleneck
- Conflicts are extremely rare (two players clicking same block within 50ms)
- Rollback looks like normal lag when it happens
- Enables truly seamless gameplay

#### Why Event Sourcing for State Sync?

Instead of polling or direct RPC:
- Decouples shards from each other
- Natural audit trail for rollback/grief protection
- Scales better than point-to-point communication
- Supports replay for recovery

#### Why Three-Tier Lighting?

- Client prediction: instant, covers 99% of cases
- Quick pass: synchronous, <1ms, covers 90% correctly
- Deep pass: asynchronous, fixes remaining edge cases
- Players never see dark worlds, chunk loading isn't blocked

### When to Challenge Architecture

If your contribution:
- Introduces a new dependency
- Changes the wire protocol
- Modifies chunk allocation logic
- Affects the event bus schema
- Changes authentication flow

**Please open an issue first** to discuss the architectural implications.

### Adding New Events

When adding new event types to the event bus:

1. Update `shared/proto/events.proto`
2. Regenerate protobuf files: `make proto`
3. Add handler in both Go and Java code
4. Document in `docs/API.md`
5. Add integration test

### Modifying gRPC API

When modifying the coordinator API:

1. Update `shared/proto/coordinator.proto`
2. Regenerate protobuf files: `make proto`
3. Update both server (Go) and client (Java/Go) implementations
4. Maintain backward compatibility (don't remove fields, only deprecate)
5. Update `docs/API.md`

## Questions?

- Open an issue for bugs or feature requests
- Join our Discord for real-time discussion
- Check existing documentation in `docs/`

Thank you for contributing to ShardedMC!
