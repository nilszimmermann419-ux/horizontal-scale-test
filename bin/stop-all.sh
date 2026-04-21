#!/bin/bash
pkill -f coordinator
pkill -f proxy
pkill -f "java.*shard"
docker-compose down
