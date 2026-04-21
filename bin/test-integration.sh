#!/bin/bash
set -e

echo "=== ShardedMC v2.0 Integration Tests ==="

# Start infrastructure
docker-compose up -d nats redis minio
echo "Waiting for infrastructure..."
sleep 5

# Run Go tests
echo "Running coordinator tests..."
cd coordinator && go test -v ./...

echo "Running proxy tests..."
cd ../proxy && go test -v ./...

# Build services
echo "Building coordinator..."
cd ../coordinator && go build -o ../bin/coordinator ./cmd/main.go

echo "Building proxy..."
cd ../proxy && go build -o ../bin/proxy ./cmd/main.go

# Start services
echo "Starting coordinator..."
./bin/coordinator &
CO_PID=$!
sleep 2

echo "Starting proxy..."
./bin/proxy &
PROXY_PID=$!
sleep 2

# Health checks
echo "Checking coordinator health..."
curl -s http://localhost:8080/health | grep -q "healthy" && echo "Coordinator OK" || echo "Coordinator FAIL"

echo "Checking proxy metrics..."
curl -s http://localhost:9090/metrics | grep -q "connections" && echo "Proxy OK" || echo "Proxy FAIL"

# Cleanup
kill $CO_PID $PROXY_PID 2>/dev/null || true
docker-compose down

echo "=== Integration tests complete ==="
