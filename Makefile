.PHONY: all build run test clean proto demo

all: build

proto:
	@echo "Proto generation not yet implemented"

build: build-coordinator build-proxy

build-coordinator:
	@mkdir -p bin
	cd coordinator && go build -o ../bin/coordinator ./cmd/main.go

build-proxy:
	@mkdir -p bin
	cd proxy && go build -o ../bin/proxy ./cmd/main.go

run: run-infra run-services

run-infra:
	docker-compose up -d nats redis minio
	@echo "Waiting for infrastructure..."
	@sleep 5

run-services: run-infra
	@echo "Starting coordinator..."
	@./bin/coordinator &
	@echo "Coordinator PID: $$!"
	@sleep 2
	@echo "Starting proxy..."
	@./bin/proxy &
	@echo "Proxy PID: $$!"
	@echo ""
	@echo "=== ShardedMC v2.0 Running ==="
	@echo "Connect to: localhost:25565"

demo: build run
	@echo "Demo mode - connect with Minecraft to localhost:25565"

test:
	cd coordinator && go test ./...
	cd proxy && go test ./...

clean:
	rm -rf bin/
	cd coordinator && go clean
	cd proxy && go clean
	pkill -f coordinator || true
	pkill -f proxy || true

docker-build:
	docker-compose build

docker-up:
	docker-compose up -d

docker-down:
	docker-compose down
