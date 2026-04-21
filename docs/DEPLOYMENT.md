# ShardedMC Deployment Guide

## Table of Contents

1. [Docker Compose (Development)](#docker-compose-development)
2. [Kubernetes (Production)](#kubernetes-production)
3. [Scaling Guide](#scaling-guide)
4. [Monitoring Setup](#monitoring-setup)
5. [Backup and Recovery](#backup-and-recovery)
6. [Troubleshooting](#troubleshooting)

---

## Docker Compose (Development)

The included `docker-compose.yml` provides a complete development environment.

### Services

| Service | Port | Description |
|---------|------|-------------|
| `nats` | 4222, 8222 | Message bus and JetStream |
| `redis` | 6379 | Player state cache |
| `minio` | 9000, 9001 | Chunk storage (S3-compatible) |
| `coordinator` | 50051, 8080 | Shard coordination and routing |
| `proxy` | 25565 | Minecraft client proxy |
| `shard-1` | 25566 | Game server shard |

### Quick Start

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f coordinator
docker-compose logs -f proxy
docker-compose logs -f shard-1

# Scale shards
docker-compose up -d --scale shard-1=3

# Stop everything
docker-compose down

# Stop and remove volumes (clears all data)
docker-compose down -v
```

### Development Mode

Start only infrastructure services and run components locally:

```bash
# Start infrastructure
docker-compose up -d nats redis minio

# Run coordinator locally
cd coordinator && go run ./cmd/main.go

# Run proxy locally (in another terminal)
cd proxy && go run ./cmd/main.go

# Run shard locally (in another terminal)
cd shard && ./gradlew run
```

### Custom Configuration

Create a `.env` file from `.env.example`:

```bash
cp .env.example .env
```

Key variables:

```env
# Coordinator
HTTP_PORT=8080
GRPC_PORT=50051
HEARTBEAT_TIMEOUT=30
CHECK_INTERVAL=10

# Proxy
LISTEN_ADDR=:25565
MAX_CONNECTIONS=10000

# Shard
SHARD_MAX_PLAYERS=2000
SHARD_REGION_SIZE=4
```

---

## Kubernetes (Production)

### Architecture

```
┌─────────────────────────────────────────┐
│              Ingress                     │
│         (Minecraft TCP LB)               │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│           Proxy Deployment              │
│    (HPA: 10-100 replicas)               │
│         Minecraft :25565                 │
└─────────────────┬───────────────────────┘
                  │ gRPC
┌─────────────────▼───────────────────────┐
│        Coordinator StatefulSet          │
│         (3 replicas, Raft)              │
│      gRPC :50051, HTTP :8080            │
└─────────────────┬───────────────────────┘
                  │ NATS / State Stream
┌─────────────────▼───────────────────────┐
│          Shard StatefulSet              │
│    (Anti-affinity, persistent vols)     │
│         Minecraft :25566                 │
└─────────────────────────────────────────┘
```

### Namespace Setup

```bash
kubectl create namespace shardedmc
kubectl config set-context --current --namespace=shardedmc
```

### Infrastructure

#### NATS

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nats
spec:
  serviceName: nats
  replicas: 3
  selector:
    matchLabels:
      app: nats
  template:
    metadata:
      labels:
        app: nats
    spec:
      containers:
      - name: nats
        image: nats:2-alpine
        command: ["nats-server", "--js", "--store_dir", "/data"]
        ports:
        - containerPort: 4222
        - containerPort: 8222
        volumeMounts:
        - name: data
          mountPath: /data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
```

#### Redis Cluster

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
spec:
  serviceName: redis
  replicas: 6
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        command: ["redis-server", "--appendonly", "yes"]
        ports:
        - containerPort: 6379
        volumeMounts:
        - name: data
          mountPath: /data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 5Gi
```

#### MinIO

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: minio
spec:
  serviceName: minio
  replicas: 4
  selector:
    matchLabels:
      app: minio
  template:
    metadata:
      labels:
        app: minio
    spec:
      containers:
      - name: minio
        image: minio/minio:latest
        command: ["minio", "server", "/data", "--console-address", ":9001"]
        ports:
        - containerPort: 9000
        - containerPort: 9001
        env:
        - name: MINIO_ROOT_USER
          valueFrom:
            secretKeyRef:
              name: minio-credentials
              key: access-key
        volumeMounts:
        - name: data
          mountPath: /data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 100Gi
```

### Application Components

#### Coordinator

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: coordinator
spec:
  serviceName: coordinator
  replicas: 3
  selector:
    matchLabels:
      app: coordinator
  template:
    metadata:
      labels:
        app: coordinator
    spec:
      containers:
      - name: coordinator
        image: shardedmc/coordinator:latest
        ports:
        - containerPort: 50051
          name: grpc
        - containerPort: 8080
          name: http
        env:
        - name: NATS_URL
          value: "nats://nats:4222"
        - name: ENABLE_TLS
          value: "true"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

#### Proxy

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: proxy
spec:
  replicas: 10
  selector:
    matchLabels:
      app: proxy
  template:
    metadata:
      labels:
        app: proxy
    spec:
      containers:
      - name: proxy
        image: shardedmc/proxy:latest
        ports:
        - containerPort: 25565
        env:
        - name: COORDINATOR_ADDR
          value: "coordinator:50051"
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
```

#### Shard

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: shard
spec:
  serviceName: shard
  replicas: 4
  selector:
    matchLabels:
      app: shard
  template:
    metadata:
      labels:
        app: shard
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - shard
              topologyKey: kubernetes.io/hostname
      containers:
      - name: shard
        image: shardedmc/shard:latest
        ports:
        - containerPort: 25566
        env:
        - name: COORDINATOR_HOST
          value: "coordinator"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
```

### HPA Configuration

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: proxy-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: proxy
  minReplicas: 10
  maxReplicas: 100
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: connections
      target:
        type: AverageValue
        averageValue: "800"
```

---

## Scaling Guide

### Horizontal Scaling

#### Add a Shard

1. Update StatefulSet replicas:
   ```bash
   kubectl scale statefulset shard --replicas=5
   ```

2. New shard automatically:
   - Registers with coordinator
   - Consistent hash ring updates
   - Receives region allocations
   - Loads chunks from MinIO

3. Monitor migration:
   ```bash
   kubectl logs -f shard-4
   ```

#### Add a Proxy

1. Update Deployment replicas:
   ```bash
   kubectl scale deployment proxy --replicas=20
   ```

2. New proxies automatically:
   - Connect to coordinator
   - Accept player connections
   - Load balancer distributes traffic

#### Add a Coordinator

1. Update StatefulSet replicas:
   ```bash
   kubectl scale statefulset coordinator --replicas=5
   ```

2. New coordinator joins Raft consensus

### Vertical Scaling

#### Increase Shard Resources

```bash
# Edit resources
kubectl edit statefulset shard

# Rolling restart
kubectl rollout restart statefulset shard
```

No data migration needed - shards load chunks on demand.

### Capacity Planning

| Players | Shards | Proxies | Coordinators | Redis | MinIO |
|---------|--------|---------|--------------|-------|-------|
| 1,000 | 1 | 2 | 3 | 1 | 1 |
| 10,000 | 5 | 10 | 3 | 3 | 2 |
| 50,000 | 25 | 50 | 5 | 6 | 4 |
| 100,000 | 50 | 100 | 7 | 6 | 4 |

**Per-shard limits:**
- Max players: 2,000
- Max regions: Depends on memory (approx. 100 regions per 2GB)
- Max chunks: 16 chunks per region

---

## Monitoring Setup

### Prometheus

#### Installation

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/prometheus
```

#### ServiceMonitor

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: shardedmc-metrics
spec:
  selector:
    matchLabels:
      app: coordinator
  endpoints:
  - port: http
    path: /metrics
    interval: 15s
```

#### Key Alerts

```yaml
groups:
- name: shardedmc
  rules:
  - alert: ShardDown
    expr: shards_healthy < shards_total
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Shard is down"

  - alert: HighLoad
    expr: rate(grpc_request_duration_seconds_count[5m]) > 1000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High gRPC request rate"

  - alert: ProxyConnections
    expr: player_routes_total > 8000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High player connection count"
```

### Grafana

#### Installation

```bash
helm install grafana grafana/grafana
```

#### Dashboard

Import dashboard ID `shardedmc-main` or create custom:

**Panels:**
- Shards Online (gauge)
- Player Count (graph)
- gRPC Request Rate (graph)
- Chunk Load Time (heatmap)
- Memory Usage per Shard (graph)
- Error Rate (graph)

**Sample Query:**
```promql
# Players per shard
sum by (shard_id) (player_routes_total)

# gRPC request rate
rate(grpc_requests_total[5m])

# Average chunk load time
histogram_quantile(0.95, rate(chunk_load_duration_seconds_bucket[5m]))
```

### NATS Monitoring

```bash
# NATS metrics endpoint
curl http://nats:8222/varz

# JetStream info
curl http://nats:8222/jsz
```

### Redis Monitoring

```bash
# Redis INFO
redis-cli INFO

# Monitor commands
redis-cli MONITOR
```

---

## Backup and Recovery

### Automated Backups

#### World Data (MinIO)

```bash
# Daily snapshot
mc mirror minio/shardedmc-world s3/backup-bucket/world/$(date +%Y%m%d)

# Hourly incremental
mc cp --recursive minio/shardedmc-world/changed s3/backup-bucket/world/incremental
```

#### Player Data (Redis)

```bash
# Redis RDB snapshot
redis-cli BGSAVE

# Copy to backup
cp /data/dump.rdb /backup/redis/$(date +%Y%m%d).rdb
```

#### Event Log (NATS)

```bash
# Export JetStream streams
nats stream backup world.blocks /backup/nats/blocks
nats stream backup world.entities /backup/nats/entities
```

### Recovery Procedures

#### Full World Recovery

```bash
# 1. Stop all shards
kubectl scale statefulset shard --replicas=0

# 2. Restore MinIO data
mc mirror s3/backup-bucket/world/20260101 minio/shardedmc-world

# 3. Restore Redis data
cp /backup/redis/20260101.rdb /data/dump.rdb

# 4. Start shards
kubectl scale statefulset shard --replicas=4

# 5. Replay events from NATS
nats stream restore world.blocks /backup/nats/blocks
```

#### Single Shard Recovery

```bash
# 1. Delete failed shard pod
kubectl delete pod shard-2

# 2. StatefulSet recreates it
# 3. New shard loads chunks from MinIO
# 4. Player data loaded from Redis
```

#### Point-in-Time Recovery

1. Restore latest world snapshot
2. Replay event log from snapshot time
3. Full recovery time: < 5 minutes

### Backup Schedule

| Data | Frequency | Retention |
|------|-----------|-----------|
| World snapshots | Daily | 30 days |
| Event logs | Hourly | 7 days |
| Player data | Every 5 min (Redis) + Daily snapshot | 30 days |
| Configuration | On change | Git history |

---

## Troubleshooting

### Common Issues

#### Shard Fails to Register

**Symptoms:** Shard logs show "Failed to register with coordinator"

**Diagnosis:**
```bash
# Check coordinator is running
kubectl get pods -l app=coordinator

# Check coordinator logs
kubectl logs -l app=coordinator

# Test gRPC connection
grpcurl -plaintext coordinator:50051 list
```

**Solutions:**
1. Restart coordinator: `kubectl rollout restart statefulset coordinator`
2. Check NATS connectivity
3. Verify shard has correct coordinator address

#### Players Can't Connect

**Symptoms:** Connection refused or timeout

**Diagnosis:**
```bash
# Check proxy is running
kubectl get pods -l app=proxy

# Check proxy logs
kubectl logs -l app=proxy

# Test TCP connection
nc -vz proxy-ip 25565
```

**Solutions:**
1. Scale up proxies: `kubectl scale deployment proxy --replicas=20`
2. Check load balancer health
3. Verify proxy can reach coordinator

#### High Chunk Load Times

**Symptoms:** Players experience lag, chunks load slowly

**Diagnosis:**
```bash
# Check MinIO performance
mc admin info minio

# Check Redis memory
redis-cli INFO memory

# Check shard CPU/memory
kubectl top pod -l app=shard
```

**Solutions:**
1. Add more shards to distribute load
2. Increase Redis memory
3. Check MinIO disk I/O
4. Enable chunk caching

#### Event Bus Backlog

**Symptoms:** NATS JetStream shows high pending messages

**Diagnosis:**
```bash
# Check stream status
nats stream info world.blocks

# Check consumer lag
nats consumer info world.blocks shard-1
```

**Solutions:**
1. Scale up shards consuming the stream
2. Increase MaxAckPending
3. Check for slow consumers
4. Consider stream partitioning

#### Memory Leaks

**Symptoms:** Shard memory usage grows continuously

**Diagnosis:**
```bash
# Go memory profile
curl http://shard:8080/debug/pprof/heap > heap.prof
go tool pprof heap.prof

# Java heap dump
jmap -dump:format=b,file=heap.hprof <pid>
```

**Solutions:**
1. Check for unclosed connections
2. Verify object pooling is working
3. Review recent code changes
4. Restart affected shards

### Debug Commands

```bash
# Get all component status
kubectl get all

# Check events
kubectl get events --sort-by=.lastTimestamp

# Port forward for debugging
kubectl port-forward pod/coordinator-0 8080:8080

# Exec into shard
kubectl exec -it shard-0 -- /bin/sh

# Check network connectivity
kubectl run debug --rm -it --image=busybox -- /bin/sh
```

### Log Locations

| Component | Log Command |
|-----------|-------------|
| Coordinator | `kubectl logs -l app=coordinator` |
| Proxy | `kubectl logs -l app=proxy` |
| Shard | `kubectl logs -l app=shard` |
| NATS | `kubectl logs -l app=nats` |
| Redis | `kubectl logs -l app=redis` |

### Getting Help

1. Check logs first: `kubectl logs -f <pod-name>`
2. Check metrics: `curl http://coordinator:8080/metrics`
3. Check health: `curl http://coordinator:8080/health`
4. Open an issue with logs and metrics
