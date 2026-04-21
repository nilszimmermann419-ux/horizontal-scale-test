#!/bin/bash
# ShardedMC v2.0 Demo
# This demonstrates the fixed block breaking and lighting

echo "=== ShardedMC v2.0 Demo ==="
echo ""
echo "Starting infrastructure (NATS, Redis, MinIO)..."
docker-compose up -d nats redis minio
sleep 5

echo ""
echo "Starting coordinator..."
./bin/coordinator &
sleep 2

echo "Starting proxy..."
./bin/proxy &
sleep 2

echo ""
echo "=== Services Ready ==="
echo "Coordinator: http://localhost:8080/health"
echo "Proxy: localhost:25565 (Minecraft)"
echo ""
echo "Connect with Minecraft Java Edition to localhost:25565"
echo ""
echo "What to test:"
echo "1. Break blocks - they break instantly (no lag)"
echo "2. Place blocks - they place instantly"
echo "3. World lighting - should be properly lit"
echo "4. Walk around - chunks load fast (<50ms)"
echo ""
echo "Press Ctrl+C to stop"
wait
