# ShardedMC v2.0 Troubleshooting Guide

## Quick Diagnostics

```bash
# Check all services status
make validate

# Check running processes
bin/status.sh

# View logs
docker-compose logs -f

# Check coordinator health
curl http://localhost:8080/health
```

## Common Issues

### Can't Connect to Server

**Symptoms:**
- "Connection refused" or "Connection timed out"
- Can't ping the server
- Minecraft client hangs on "Connecting to server"

**Diagnosis:**
```bash
# Check if proxy is listening
netstat -tlnp | grep 25565
lsof -i :25565

# Test TCP connection
telnet localhost 25565
nc -vz localhost 25565

# Check proxy logs
bin/status.sh
# or
docker-compose logs proxy
```

**Common Fixes:**

1. **Proxy not running**
   ```bash
   # Start development mode
   make dev
   
   # Or start just infrastructure + binary
   docker-compose up -d nats redis minio
   ./bin/proxy
   ```

2. **Port already in use**
   ```bash
   # Find process using port
   lsof -i :25565
   
   # Kill it
   kill -9 <PID>
   
   # Or use different port
   LISTEN_ADDR=:25566 ./bin/proxy
   ```

3. **Firewall blocking**
   ```bash
   # Allow Minecraft port
   sudo ufw allow 25565/tcp
   sudo iptables -A INPUT -p tcp --dport 25565 -j ACCEPT
   ```

4. **Coordinator not reachable**
   ```bash
   # Check coordinator
   curl http://localhost:8080/health
   
   # Check proxy configuration
   echo $COORDINATOR_ADDR  # Should be localhost:50051 or coordinator:50051
   ```

### Blocks Don't Break

**Symptoms:**
- Blocks reappear after breaking
- Breaking animation plays but block stays
- Items don't drop when breaking blocks

**Diagnosis:**
```bash
# Check shard logs
docker-compose logs shard-1

# Check NATS connectivity
docker-compose exec nats nats pub test "hello"

# Check Redis
docker-compose exec redis redis-cli ping
```

**Common Fixes:**

1. **Shard not processing events**
   ```bash
   # Restart shard
   docker-compose restart shard-1
   
   # Or if running locally:
   pkill -f "java.*shard"
   java -jar shard/build/libs/shard-2.0.0-SNAPSHOT-all.jar
   ```

2. **NATS connection issues**
   ```bash
   # Check NATS is healthy
   curl http://localhost:8222/healthz
   
   # Restart NATS
   docker-compose restart nats
   ```

3. **Event bus not publishing**
   - Check shard logs for "event bus" errors
   - Verify NATS JetStream streams exist:
     ```bash
     docker-compose exec nats nats stream list
     ```

4. **Region not loaded**
   - Player may be in unloaded region
   - Check coordinator dashboard for region assignments
   - Wait for region to load (can take 10-30s on first visit)

### World is Dark

**Symptoms:**
- No lighting updates
- Blocks placed don't emit light
- Shadows don't update

**Diagnosis:**
```bash
# Check shard lighting engine
docker-compose logs shard-1 | grep -i light

# Check CPU usage (lighting is CPU intensive)
top -p $(pgrep -f "java.*shard")
```

**Common Fixes:**

1. **Lighting engine not started**
   ```bash
   # Restart shard
   docker-compose restart shard-1
   ```

2. **Insufficient CPU resources**
   ```bash
   # Increase lighting threads
   export LIGHTING_THREADS=4
   
   # Or reduce region size
   export SHARD_REGION_SIZE=2
   ```

3. **Chunk not loaded**
   - Move around to trigger chunk loading
   - Check if chunk is in Redis:
     ```bash
     docker-compose exec redis redis-cli KEYS "chunk:*"
     ```

### Chunk Loading is Slow

**Symptoms:**
- Chunks take long time to appear
- Player falls through world
- "Loading terrain..." message persists

**Diagnosis:**
```bash
# Check chunk loading metrics
curl http://localhost:9090/metrics | grep chunk

# Check storage latency
docker-compose logs shard-1 | grep -i "chunk load"

# Check Redis memory
docker-compose exec redis redis-cli INFO memory
```

**Common Fixes:**

1. **Increase chunk loading threads**
   ```bash
   export CHUNK_LOAD_THREADS=8
   docker-compose up -d shard-1
   ```

2. **Storage bottleneck**
   - Check MinIO response times:
     ```bash
     docker-compose logs minio | tail -20
     ```
   - Ensure MinIO has fast disk (SSD recommended)

3. **Network latency**
   - If Redis/MinIO are remote, consider local caching
   - Use Redis with persistence for hot chunks

4. **First-time world generation**
   - Initial chunk generation is slow
   - Pre-generate world:
     ```bash
     # Use Minestom world pre-generator
     # (see examples/simple-shard/)
     ```

### Players Disconnect

**Symptoms:**
- "Disconnected" or "Connection lost"
- "Timed out"
- Frequent reconnections

**Diagnosis:**
```bash
# Check proxy logs
docker-compose logs proxy | tail -50

# Check shard logs
docker-compose logs shard-1 | grep -i disconnect

# Network stats
netstat -s | grep -i retrans
```

**Common Fixes:**

1. **Proxy timeout too short**
   ```bash
   export READ_TIMEOUT=60
   export WRITE_TIMEOUT=60
   ./bin/proxy
   ```

2. **Shard overloaded**
   ```bash
   # Check shard CPU/memory
   docker stats shard-1
   
   # Reduce max players
   export SHARD_MAX_PLAYERS=1000
   ```

3. **Network issues**
   ```bash
   # Check packet loss
   ping localhost
   
   # Check MTU issues
   ifconfig | grep mtu
   ```

4. **Player state corruption**
   - Clear player state in Redis:
     ```bash
     docker-compose exec redis redis-cli DEL "player:<uuid>"
     ```

### Shard Crashes

**Symptoms:**
- Shard process exits
- "Connection reset" for players on that shard
- Coordinator shows shard as offline

**Diagnosis:**
```bash
# Check shard exit code
docker-compose ps shard-1

# Check logs for errors
docker-compose logs --tail=100 shard-1

# Check memory usage
docker stats shard-1
```

**Common Fixes:**

1. **Out of memory**
   ```bash
   # Increase JVM heap
   export JAVA_OPTS="-Xmx4G -Xms2G"
   
   # Or in docker-compose.yml:
   # environment:
   #   - JAVA_OPTS=-Xmx4G
   ```

2. **Uncaught exception**
   ```bash
   # Check logs for stack traces
   docker-compose logs shard-1 | grep -A 20 "Exception"
   
   # Restart shard
   docker-compose restart shard-1
   ```

3. **Storage connection lost**
   ```bash
   # Check Redis/MinIO connectivity
   docker-compose exec shard-1 wget -qO- http://redis:6379
   docker-compose exec shard-1 wget -qO- http://minio:9000/minio/health/live
   ```

4. **Corrupted world data**
   ```bash
   # Reset world (WARNING: destroys data)
   docker-compose exec redis redis-cli FLUSHDB
   docker-compose exec minio mc rm --recursive --force local/shardedmc-world
   ```

### Coordinator Not Responding

**Symptoms:**
- Dashboard unreachable
- Proxy can't connect to coordinator
- Shard registration fails

**Diagnosis:**
```bash
# Check coordinator process
ps aux | grep coordinator

# Check health endpoint
curl -v http://localhost:8080/health

# Check gRPC port
netstat -tlnp | grep 50051

# Check NATS connectivity
curl http://localhost:8222/healthz
```

**Common Fixes:**

1. **Coordinator crashed**
   ```bash
   # Restart coordinator
   ./bin/coordinator
   
   # Or in Docker:
   docker-compose restart coordinator
   ```

2. **Port conflict**
   ```bash
   # Check ports
   lsof -i :8080
   lsof -i :50051
   
   # Use different ports
   export HTTP_PORT=8081
   export GRPC_PORT=50052
   ./bin/coordinator
   ```

3. **NATS unavailable**
   ```bashn   # Start NATS
   docker-compose up -d nats
   
   # Wait for healthy
   for i in {1..30}; do
     curl -s http://localhost:8222/healthz >/dev/null && break
     sleep 1
   done
   ```

4. **Database corruption**
   ```bash
   # Reset coordinator state
   docker-compose exec redis redis-cli KEYS "shard:*" | xargs docker-compose exec redis redis-cli DEL
   ```

## Performance Tuning

### General Guidelines

1. **CPU**: Shard is CPU-intensive (lighting, redstone, chunk gen)
2. **Memory**: Allocate 2-4GB per shard
3. **Network**: Low latency between services is critical
4. **Storage**: SSD required for MinIO, Redis can use RAM

### Recommended Settings

**Small Server (1-100 players):**
```bash
SHARD_MAX_PLAYERS=100
CHUNK_LOAD_THREADS=2
LIGHTING_THREADS=1
SHARD_REGION_SIZE=2
```

**Medium Server (100-1000 players):**
```bash
SHARD_MAX_PLAYERS=500
CHUNK_LOAD_THREADS=4
LIGHTING_THREADS=2
SHARD_REGION_SIZE=4
```

**Large Server (1000+ players):**
```bash
SHARD_MAX_PLAYERS=2000
CHUNK_LOAD_THREADS=8
LIGHTING_THREADS=4
SHARD_REGION_SIZE=8
```

## Getting Help

If issues persist:

1. Check logs: `docker-compose logs`
2. Run validation: `make validate`
3. Check status: `bin/status.sh`
4. Open issue with logs and reproduction steps

## Useful Commands

```bash
# Restart everything
docker-compose down
make dev

# Check all service health
docker-compose ps

# View real-time logs
docker-compose logs -f

# Check resource usage
docker stats

# Redis inspection
docker-compose exec redis redis-cli MONITOR

# NATS monitoring
curl http://localhost:8222/varz

# Reset everything (WARNING: destroys data)
docker-compose down -v
rm -rf bin/
```
