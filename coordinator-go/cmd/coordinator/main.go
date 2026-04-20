package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/shardedmc/coordinator/internal/chunk"
	"github.com/shardedmc/coordinator/internal/proxy"
	"github.com/shardedmc/coordinator/internal/shard"
	"github.com/shardedmc/coordinator/internal/storage"
	grpcserver "github.com/shardedmc/coordinator/internal/grpcserver"
)

type Config struct {
	ProxyAddr     string
	GRPCAddr      string
	RedisAddr     string
	StoragePath   string
	MaxPlayers    int
	ShardCapacity int
}

func loadConfig() *Config {
	cfg := &Config{
		ProxyAddr:     ":25565",
		GRPCAddr:      ":50051",
		RedisAddr:     "localhost:6379",
		StoragePath:   "./data",
		MaxPlayers:    100000,
		ShardCapacity: 2000,
	}

	flag.StringVar(&cfg.ProxyAddr, "proxy", cfg.ProxyAddr, "Proxy listen address")
	flag.StringVar(&cfg.GRPCAddr, "grpc", cfg.GRPCAddr, "GRPC listen address")
	flag.StringVar(&cfg.RedisAddr, "redis", cfg.RedisAddr, "Redis address")
	flag.StringVar(&cfg.StoragePath, "storage", cfg.StoragePath, "Storage path")
	flag.IntVar(&cfg.MaxPlayers, "max-players", cfg.MaxPlayers, "Maximum players")
	flag.IntVar(&cfg.ShardCapacity, "shard-capacity", cfg.ShardCapacity, "Players per shard")
	flag.Parse()

	return cfg
}

func main() {
	cfg := loadConfig()

	log.Printf("Starting ShardedMC Coordinator")
	log.Printf("Proxy: %s, gRPC: %s, Redis: %s", cfg.ProxyAddr, cfg.GRPCAddr, cfg.RedisAddr)

	// Initialize storage
	storageEngine, err := storage.NewEngine(cfg.StoragePath)
	if err != nil {
		log.Fatal("Failed to initialize storage:", err)
	}
	defer storageEngine.Close()

	// Initialize shard manager
	shardMgr := shard.NewManager()

	// Initialize chunk manager
	chunkMgr := chunk.NewManager()
	_ = chunkMgr

	// Start gRPC server for shard registration
	grpcServer, err := grpcserver.StartGRPCServer(cfg.GRPCAddr, shardMgr)
	if err != nil {
		log.Fatal("Failed to start gRPC server:", err)
	}
	defer grpcServer.Stop()

	// Initialize proxy
	proxyServer, err := proxy.NewProxy(cfg.ProxyAddr, shardMgr)
	if err != nil {
		log.Fatal("Failed to create proxy:", err)
	}

	// Start health checks
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go shardMgr.StartHealthChecks(ctx, 5*time.Second)

	// Start proxy in background
	go func() {
		if err := proxyServer.Start(); err != nil {
			log.Fatal("Proxy error:", err)
		}
	}()

	log.Printf("Coordinator ready! Accepting players on %s", cfg.ProxyAddr)
	log.Printf("gRPC server listening on %s", cfg.GRPCAddr)

	// Wait for shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	log.Println("Shutting down...")
	cancel()
	proxyServer.Stop()
	grpcServer.Stop()
}
