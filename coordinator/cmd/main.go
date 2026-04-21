package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/nats-io/nats.go"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"

	"github.com/shardedmc/v2/coordinator/internal/allocator"
	"github.com/shardedmc/v2/coordinator/internal/health"
	"github.com/shardedmc/v2/coordinator/internal/registry"
	"github.com/shardedmc/v2/coordinator/pkg/api"
)

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getIntEnv(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if i, err := strconv.Atoi(value); err == nil {
			return i
		}
	}
	return defaultValue
}

func getDurationEnv(key string, defaultValue time.Duration) time.Duration {
	if value := os.Getenv(key); value != "" {
		if seconds, err := strconv.Atoi(value); err == nil {
			return time.Duration(seconds) * time.Second
		}
	}
	return defaultValue
}

func getBoolEnv(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if b, err := strconv.ParseBool(value); err == nil {
			return b
		}
	}
	return defaultValue
}

func main() {
	// Parse all environment variables with defaults
	httpPort := getEnv("HTTP_PORT", "8080")
	grpcPort := getEnv("GRPC_PORT", "50051")
	natsURL := getEnv("NATS_URL", "nats://localhost:4222")
	tlsCert := getEnv("TLS_CERT", "")
	tlsKey := getEnv("TLS_KEY", "")
	heartbeatTimeout := getDurationEnv("HEARTBEAT_TIMEOUT", 30*time.Second)
	checkInterval := getDurationEnv("CHECK_INTERVAL", 10*time.Second)
	maxShards := getIntEnv("MAX_SHARDS", 100)
	readTimeout := getDurationEnv("READ_TIMEOUT", 15*time.Second)
	writeTimeout := getDurationEnv("WRITE_TIMEOUT", 15*time.Second)
	shutdownTimeout := getDurationEnv("SHUTDOWN_TIMEOUT", 30*time.Second)
	enableTLS := getBoolEnv("ENABLE_TLS", false)

	log.Printf("=== ShardedMC Coordinator v2.0 Starting ===")
	log.Printf("Config: HTTP_PORT=%s GRPC_PORT=%s NATS_URL=%s TLS=%v MAX_SHARDS=%d",
		httpPort, grpcPort, natsURL, enableTLS, maxShards)

	// Validate required configuration
	if heartbeatTimeout <= 0 {
		log.Fatalf("Invalid HEARTBEAT_TIMEOUT: must be positive")
	}
	if checkInterval <= 0 {
		log.Fatalf("Invalid CHECK_INTERVAL: must be positive")
	}
	if maxShards <= 0 {
		log.Fatalf("Invalid MAX_SHARDS: must be positive")
	}

	// Initialize NATS connection
	log.Printf("Connecting to NATS at %s...", natsURL)
	natsConn, err := nats.Connect(natsURL,
		nats.Name("coordinator"),
		nats.MaxReconnects(-1),
		nats.ReconnectWait(time.Second),
		nats.DisconnectErrHandler(func(nc *nats.Conn, err error) {
			log.Printf("NATS disconnected: %v", err)
		}),
		nats.ReconnectHandler(func(nc *nats.Conn) {
			log.Printf("NATS reconnected to %s", nc.ConnectedUrl())
		}),
	)
	if err != nil {
		log.Fatalf("Failed to connect to NATS: %v", err)
	}
	defer natsConn.Close()
	log.Printf("Connected to NATS")

	// Create JetStream context
	js, err := natsConn.JetStream()
	if err != nil {
		log.Fatalf("Failed to create JetStream context: %v", err)
	}

	// Create JetStream streams
	streams := []string{"world.events", "shard.heartbeats", "player.transfers"}
	for _, stream := range streams {
		_, err := js.AddStream(&nats.StreamConfig{
			Name:     stream,
			Subjects: []string{stream + ".>"},
			Retention: nats.LimitsPolicy,
			MaxMsgs:  100000,
			MaxAge:   24 * time.Hour,
		})
		if err != nil {
			if err == nats.ErrStreamNameAlreadyInUse {
				log.Printf("JetStream stream %s already exists", stream)
			} else {
				log.Printf("Warning: Failed to create JetStream stream %s: %v", stream, err)
			}
		} else {
			log.Printf("Created JetStream stream: %s", stream)
		}
	}

	// Initialize registry and allocator
	reg := registry.NewShardRegistry(heartbeatTimeout, checkInterval)
	defer reg.Stop()
	_ = allocator.GetRegionOwner // Ensure allocator is referenced

	// HTTP health server
	healthHandler := health.NewHandler(reg)
	mux := http.NewServeMux()
	healthHandler.RegisterRoutes(mux)

	httpServer := &http.Server{
		Addr:         ":" + httpPort,
		Handler:      mux,
		ReadTimeout:  readTimeout,
		WriteTimeout: writeTimeout,
	}

	go func() {
		log.Printf("HTTP health server starting on port %s", httpPort)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("HTTP server error: %v", err)
		}
	}()

	// gRPC server
	lis, err := net.Listen("tcp", ":"+grpcPort)
	if err != nil {
		log.Fatalf("Failed to listen on gRPC port %s: %v", grpcPort, err)
	}

	var grpcServer *grpc.Server
	if enableTLS && tlsCert != "" && tlsKey != "" {
		creds, err := credentials.NewServerTLSFromFile(tlsCert, tlsKey)
		if err != nil {
			log.Fatalf("Failed to load TLS credentials: %v", err)
		}
		grpcServer = grpc.NewServer(grpc.Creds(creds))
		log.Printf("gRPC server starting with TLS on port %s", grpcPort)
	} else {
		grpcServer = grpc.NewServer()
		log.Printf("gRPC server starting on port %s (plaintext)", grpcPort)
	}

	coordinatorServer := api.NewCoordinatorServer(reg)
	api.RegisterCoordinatorServiceServer(grpcServer, coordinatorServer)

	go func() {
		if err := grpcServer.Serve(lis); err != nil {
			log.Printf("gRPC server error: %v", err)
		}
	}()

	log.Printf("Coordinator service started. HTTP=%s gRPC=%s", httpPort, grpcPort)

	// Graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGTERM, syscall.SIGINT)
	<-sigCh

	log.Println("Received shutdown signal, shutting down gracefully...")

	// Create shutdown context with timeout
	shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()

	// Shutdown gRPC server
	grpcServer.GracefulStop()
	log.Println("gRPC server stopped")

	// Shutdown HTTP server
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Printf("HTTP shutdown error: %v", err)
	} else {
		log.Println("HTTP server stopped")
	}

	// Close NATS connection
	natsConn.Close()
	log.Println("NATS connection closed")

	log.Println("Shutdown complete")
}
