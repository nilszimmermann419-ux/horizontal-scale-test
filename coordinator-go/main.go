package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/shardedmc/coordinator/internal/proxy"
	"github.com/shardedmc/coordinator/internal/shard"
	"github.com/shardedmc/coordinator/internal/storage"
)

type Coordinator struct {
	config   Config
	proxy    *proxy.Proxy
	shardMgr *shard.Manager
	storage  storage.Engine
}

type Config struct {
	ProxyAddr     string
	ShardCapacity int
}

func loadConfig() Config {
	return Config{
		ProxyAddr:     ":25565",
		ShardCapacity: 2000,
	}
}

func (c *Coordinator) Start() error {
	log.Println("Coordinator starting...")
	return nil
}

func (c *Coordinator) Stop() {
	log.Println("Coordinator stopping...")
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
