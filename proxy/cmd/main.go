package main

import (
	"context"
	"log"
	"net"
	"os"
	"os/signal"
	"strconv"
	"sync"
	"syscall"
	"time"

	"github.com/shardedmc/v2/proxy/internal/connection"
	"github.com/shardedmc/v2/proxy/internal/router"
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

func main() {
	// Parse all environment variables
	listenAddr := getEnv("LISTEN_ADDR", ":25565")
	coordinatorAddr := getEnv("COORDINATOR_ADDR", "localhost:50051")
	readTimeout := getDurationEnv("READ_TIMEOUT", 30*time.Second)
	writeTimeout := getDurationEnv("WRITE_TIMEOUT", 30*time.Second)
	maxConnections := getIntEnv("MAX_CONNECTIONS", 10000)
	shutdownTimeout := getDurationEnv("SHUTDOWN_TIMEOUT", 30*time.Second)
	reconnectInterval := getDurationEnv("RECONNECT_INTERVAL", 5*time.Second)
	reconnectMaxRetries := getIntEnv("RECONNECT_MAX_RETRIES", -1)

	log.Printf("=== ShardedMC Proxy v2.0 Starting ===")
	log.Printf("Config: LISTEN_ADDR=%s COORDINATOR_ADDR=%s MAX_CONNECTIONS=%d",
		listenAddr, coordinatorAddr, maxConnections)

	// Validate configuration
	if listenAddr == "" {
		log.Fatalf("LISTEN_ADDR cannot be empty")
	}
	if coordinatorAddr == "" {
		log.Fatalf("COORDINATOR_ADDR cannot be empty")
	}
	if maxConnections <= 0 {
		log.Fatalf("MAX_CONNECTIONS must be positive")
	}

	// Connect to coordinator with retry
	var r *router.Router
	var err error
	retries := 0
	for {
		r, err = router.NewRouter(coordinatorAddr)
		if err == nil {
			break
		}
		retries++
		if reconnectMaxRetries > 0 && retries >= reconnectMaxRetries {
			log.Fatalf("Failed to connect to coordinator after %d retries: %v", retries, err)
		}
		log.Printf("Failed to connect to coordinator (attempt %d): %v. Retrying in %v...", retries, err, reconnectInterval)
		time.Sleep(reconnectInterval)
	}
	defer r.Close()
	log.Printf("Connected to coordinator at %s", coordinatorAddr)

	// Start TCP listener
	listener, err := net.Listen("tcp", listenAddr)
	if err != nil {
		log.Fatalf("Failed to listen on %s: %v", listenAddr, err)
	}
	defer listener.Close()
	log.Printf("Listening for Minecraft clients on %s", listenAddr)

	// Context for graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var wg sync.WaitGroup
	connectionCount := 0
	var countMu sync.Mutex

	// Signal handler
	go func() {
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
		<-sigChan
		log.Println("Received shutdown signal, shutting down gracefully...")
		cancel()
		listener.Close()
	}()

	// Connection accept loop
	for {
		conn, err := listener.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				// Wait for active connections to finish
				done := make(chan struct{})
				go func() {
					wg.Wait()
					close(done)
				}()
				select {
				case <-done:
					log.Println("All connections closed")
				case <-time.After(shutdownTimeout):
					log.Println("Shutdown timeout reached, forcing exit")
				}
				log.Println("Shutdown complete")
				return
			default:
				log.Printf("Accept error: %v", err)
				continue
			}
		}

		// Rate limiting / max connections check
		countMu.Lock()
		if connectionCount >= maxConnections {
			countMu.Unlock()
			log.Printf("Max connections reached (%d), rejecting connection from %s", maxConnections, conn.RemoteAddr())
			conn.Close()
			continue
		}
		connectionCount++
		countMu.Unlock()

		wg.Add(1)
		go func() {
			defer wg.Done()
			defer func() {
				countMu.Lock()
				connectionCount--
				countMu.Unlock()
			}()
			handleClient(ctx, conn, r, readTimeout, writeTimeout)
		}()
	}
}

func handleClient(ctx context.Context, netConn net.Conn, r *router.Router, readTimeout, writeTimeout time.Duration) {
	defer netConn.Close()

	// Set connection timeouts
	if tcpConn, ok := netConn.(*net.TCPConn); ok {
		tcpConn.SetKeepAlive(true)
		tcpConn.SetKeepAlivePeriod(3 * time.Minute)
	}

	client := connection.NewClientConn(netConn)
	defer client.Close()

	// Handshake phase
	for client.State == connection.StateHandshake {
		packet, err := client.ReadPacket()
		if err != nil {
			log.Printf("Client %s: read handshake error: %v", netConn.RemoteAddr(), err)
			return
		}

		if err := client.HandleHandshake(packet); err != nil {
			log.Printf("Client %s: handshake error: %v", netConn.RemoteAddr(), err)
			return
		}
	}

	// Login phase
	for client.State == connection.StateLogin {
		packet, err := client.ReadPacket()
		if err != nil {
			log.Printf("Client %s: read login error: %v", netConn.RemoteAddr(), err)
			return
		}

		if packet.ID == 0x00 { // LoginStart
			if err := client.HandleLoginStart(packet); err != nil {
				log.Printf("Client %s: login start error: %v", netConn.RemoteAddr(), err)
				return
			}
			break
		}
	}

	if client.State != connection.StatePlay {
		log.Printf("Client %s: unexpected state after login: %d", netConn.RemoteAddr(), client.State)
		return
	}

	log.Printf("Player %s (%s) logged in from %s", client.Username, client.UUID, netConn.RemoteAddr())

	// Route player to shard
	shard, err := r.RoutePlayer(client.UUID, nil)
	if err != nil {
		log.Printf("Player %s: routing error: %v", client.Username, err)
		return
	}

	defer r.RemovePlayer(client.UUID)

	log.Printf("Player %s routed to shard %s", client.Username, shard.ShardID)

	// Bidirectional packet forwarding
	done := make(chan struct{})
	var forwardWg sync.WaitGroup

	forwardWg.Add(2)
	go func() {
		defer forwardWg.Done()
		if err := connection.ForwardPackets(client.Conn, shard.Conn, done); err != nil {
			log.Printf("Player %s: client->shard forward error: %v", client.Username, err)
		}
	}()
	go func() {
		defer forwardWg.Done()
		if err := connection.ForwardPackets(shard.Conn, client.Conn, done); err != nil {
			log.Printf("Player %s: shard->client forward error: %v", client.Username, err)
		}
	}()

	// Wait for context cancellation or connection close
	select {
	case <-ctx.Done():
		log.Printf("Proxy shutting down, disconnecting player %s", client.Username)
	case <-done:
		// One of the forwarders closed
	}

	close(done)
	forwardWg.Wait()

	log.Printf("Player %s disconnected", client.Username)
}
