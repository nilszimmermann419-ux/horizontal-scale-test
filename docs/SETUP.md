# Setup Guide

## Development Environment

### 1. Clone Repository

```bash
git clone <repo-url>
cd minestom-horizontal-scaling
```

### 2. Install Dependencies

```bash
./gradlew build
```

### 3. Start Infrastructure

```bash
docker-compose up -d redis
```

### 4. Start Coordinator

```bash
./gradlew :coordinator:run
```

### 5. Start Shard

```bash
./gradlew :shard:run
```

### 6. Connect with Minecraft Client

Connect to `localhost:25565`

## Production Deployment

### Requirements

- Redis Cluster (3 master + 3 replica minimum)
- Coordinator instances (2-3 for HA)
- Shard instances (scale based on player count)
- Load balancer (NGINX/HAProxy)

### Deployment Steps

1. Deploy Redis Cluster
2. Deploy Coordinator instances behind load balancer
3. Deploy Shard instances (scale horizontally)
4. Configure DNS/load balancer for shard connections
5. Monitor with Prometheus/Grafana

## Configuration

All configuration is done via environment variables. See `.env.example` for available options.
