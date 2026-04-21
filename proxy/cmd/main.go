package main

import (
	"context"
	"log"
	"net"
	"os"
	"os/signal"
	"sync"
	"syscall"

	"github.com/shardedmc/v2/proxy/internal/connection"
	"github.com/shardedmc/v2/proxy/internal/router"
	"github.com/shardedmc/v2/proxy/pkg/protocol"
)

func main() {
	listenAddr := getEnv("LISTEN_ADDR", ":25565")
	coordinatorAddr := getEnv("COORDINATOR_ADDR", "localhost:50051")

	log.Printf("Starting ShardedMC Proxy v2.0 on %s", listenAddr)
	log.Printf("Coordinator address: %s", coordinatorAddr)

	r, err := router.NewRouter(coordinatorAddr)
	if err != nil {
		log.Fatalf("Failed to create router: %v", err)
	}
	defer r.Close()

	listener, err := net.Listen("tcp", listenAddr)
	if err != nil {
		log.Fatalf("Failed to listen on %s: %v", listenAddr, err)
	}
	defer listener.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var wg sync.WaitGroup

	go func() {
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
		<-sigChan
		log.Println("Shutting down gracefully...")
		cancel()
		listener.Close()
	}()

	for {
		conn, err := listener.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				wg.Wait()
				log.Println("Shutdown complete")
				return
			default:
				log.Printf("Accept error: %v", err)
				continue
			}
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
			handleClient(ctx, conn, r)
		}()
	}
}

func handleClient(ctx context.Context, netConn net.Conn, r *router.Router) {
	defer netConn.Close()

	client := connection.NewClientConn(netConn)
	defer client.Close()

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

	for client.State == connection.StateLogin {
		packet, err := client.ReadPacket()
		if err != nil {
			log.Printf("Client %s: read login error: %v", netConn.RemoteAddr(), err)
			return
		}

		if packet.ID == protocol.LoginStartPacketID {
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

	log.Printf("Player %s (%s) logged in", client.Username, client.UUID)

	shard, err := r.RoutePlayer(client.UUID, nil)
	if err != nil {
		log.Printf("Player %s: routing error: %v", client.Username, err)
		return
	}

	defer r.RemovePlayer(client.UUID)

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

	<-ctx.Done()
	close(done)
	forwardWg.Wait()

	log.Printf("Player %s disconnected", client.Username)
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
