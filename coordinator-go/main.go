package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"
)

type Coordinator struct {
	config   *Config
	proxy    *Proxy
	shardMgr *ShardManager
	storage  *StorageEngine
	redis    *RedisClient
}

func main() {
	coord := &Coordinator{
		config: loadConfig(),
	}

	if err := coord.Start(); err != nil {
		log.Fatal(err)
	}

	// Wait for shutdown signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	coord.Stop()
}
