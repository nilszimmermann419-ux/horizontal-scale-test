#!/bin/bash
set -e

echo "=== Validating ShardedMC v2.0 ==="

# Check Go services compile
echo "Checking Go services..."
cd coordinator && go build ./cmd/main.go && cd ..
cd proxy && go build ./cmd/main.go && cd ..
echo "Go services: OK"

# Run tests
echo "Running tests..."
cd coordinator && go test ./... && cd ..
cd proxy && go test ./... && cd ..
echo "Tests: OK"

# Check docker-compose
echo "Checking docker-compose..."
docker-compose config > /dev/null
echo "Docker Compose: OK"

# Check proto files
echo "Checking proto files..."
for f in shared/proto/*.proto; do
  if [ -f "$f" ]; then
    echo "  $f: OK"
  fi
done

echo ""
echo "=== Validation Complete ==="
