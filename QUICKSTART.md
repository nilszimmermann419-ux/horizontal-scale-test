# Quick Start

## Build
```bash
make build
```

## Run
```bash
# Start everything
make run

# Or manually:
docker-compose up -d nats redis minio
./bin/coordinator &
./bin/proxy &
# Java shard would go here
```

## Connect
Open Minecraft Java Edition → Multiplayer → Add Server → localhost:25565

## Test
- Break blocks (left click) - should be instant
- Place blocks (right click) - should be instant  
- Check lighting - no dark chunks
- Walk around - fast chunk loading
