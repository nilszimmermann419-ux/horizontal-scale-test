.PHONY: all build run test clean proto

all: build

proto:
	cd shared/proto && protoc --go_out=../go --go-grpc_out=../go *.proto

build: build-coordinator build-proxy build-shard

build-coordinator:
	cd coordinator && go build -o ../bin/coordinator ./cmd

build-proxy:
	cd proxy && go build -o ../bin/proxy ./cmd

build-shard:
	cd shard && ./gradlew build

run: run-infra run-services

run-infra:
	docker-compose up -d nats redis minio

run-services: run-infra
	docker-compose up -d coordinator proxy shard-1

test:
	cd coordinator && go test ./...
	cd proxy && go test ./...
	cd shard && ./gradlew test

clean:
	rm -rf bin/
	cd shard && ./gradlew clean
	cd coordinator && go clean
	cd proxy && go clean
