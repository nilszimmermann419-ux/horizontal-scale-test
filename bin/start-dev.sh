#!/bin/bash
set -e

echo "=== ShardedMC v2.0 Development Mode ==="

# Start infrastructure
echo "Starting infrastructure..."
docker-compose up -d nats redis minio

# Wait for services
echo "Waiting for infrastructure..."
for i in {1..30}; do
  if curl -s http://localhost:8222/healthz >/dev/null 2>&1; then
    echo "NATS ready"
    break
  fi
  sleep 1
done

# Build if needed
if [ ! -f bin/coordinator ]; then
  echo "Building coordinator..."
  make build-coordinator
fi

if [ ! -f bin/proxy ]; then
  echo "Building proxy..."
  make build-proxy
fi

# Start coordinator
echo "Starting coordinator..."
./bin/coordinator &
echo $! > .coordinator.pid

sleep 2

# Start proxy
echo "Starting proxy..."
./bin/proxy &
echo $! > .proxy.pid

sleep 2

echo ""
echo "=== Services Running ==="
echo "Coordinator: http://localhost:8080/health"
echo "Dashboard: http://localhost:8080/dashboard"
echo "Proxy: localhost:25565 (Minecraft)"
echo "Metrics: http://localhost:9090/metrics"
echo ""
echo "Press Ctrl+C to stop"

# Wait for interrupt
trap 'echo "Stopping..."; kill $(cat .coordinator.pid .proxy.pid 2>/dev/null) 2>/dev/null; rm -f .coordinator.pid .proxy.pid; exit' INT
wait
