#!/bin/bash
# Start infrastructure
docker-compose up -d nats redis minio

# Wait for infrastructure
echo "Waiting for infrastructure..."
sleep 5

# Start coordinator
./bin/coordinator &
CO_PID=$!

# Start proxy
./bin/proxy &
PROXY_PID=$!

# Start shards
java -jar shard/build/libs/shard-2.0.0-SNAPSHOT-all.jar &
SHARD1_PID=$!

echo "ShardedMC v2.0 started!"
echo "Coordinator PID: $CO_PID"
echo "Proxy PID: $PROXY_PID"
echo "Shard PID: $SHARD1_PID"
echo "Connect to localhost:25565"
