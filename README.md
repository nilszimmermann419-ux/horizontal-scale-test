# Minestom Horizontal Scaling Server

A production-grade horizontally-scalable Minecraft server built on Minestom.

## Features

- **Shard-Based Distribution:** Distribute chunks across multiple Minestom instances
- **Seamless Player Transitions:** <100ms handoff between shards
- **Central Coordinator:** Manages chunk allocation and player routing
- **Redis State Store:** Shared state and pub/sub messaging
- **Plugin API:** Abstracted API hiding shard complexity from developers
- **Production Ready:** Health monitoring, dynamic rebalancing, graceful degradation

## Quick Start

### Prerequisites

- Java 21+
- Docker and Docker Compose
- Gradle

### Running with Docker Compose

```bash
# Copy example environment file
cp .env.example .env

# Start infrastructure (Redis) and coordinator
docker-compose up -d

# Start shards
./gradlew :shard:run
```

### Configuration

See `.env.example` for available configuration options.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed architecture documentation.

## Plugin Development

See [docs/API.md](docs/API.md) for plugin API documentation.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `./gradlew test`
5. Submit a pull request

## License

MIT
