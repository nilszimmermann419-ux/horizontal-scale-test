package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/nats-io/nats.go"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"

	pb "github.com/shardedmc/v2/shared/go/pkg/proto"
)

// SimpleShard is a minimal implementation of a ShardedMC shard.
// It demonstrates how to connect to the coordinator and handle basic events.
type SimpleShard struct {
	id            string
	coordinatorAddr string
	natsURL       string
	
	grpcConn      *grpc.ClientConn
	coordinator   pb.CoordinatorServiceClient
	natsConn      *nats.Conn
	jetStream     nats.JetStreamContext
	
	regions       []RegionCoord
	players       map[string]*Player
	running       bool
}

// RegionCoord represents a region coordinate
type RegionCoord struct {
	X, Z int32
}

// Player represents a connected player
type Player struct {
	UUID     string
	Username string
	X, Y, Z  float64
}

// NewSimpleShard creates a new minimal shard instance.
func NewSimpleShard(id, coordinatorAddr, natsURL string) *SimpleShard {
	return &SimpleShard{
		id:              id,
		coordinatorAddr: coordinatorAddr,
		natsURL:         natsURL,
		regions:         []RegionCoord{{X: 0, Z: 0}},
		players:         make(map[string]*Player),
	}
}

// Connect establishes connections to coordinator and NATS.
func (s *SimpleShard) Connect() error {
	// Connect to coordinator via gRPC
	log.Printf("[%s] Connecting to coordinator at %s...", s.id, s.coordinatorAddr)
	conn, err := grpc.Dial(s.coordinatorAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return fmt.Errorf("connect to coordinator: %w", err)
	}
	s.grpcConn = conn
	s.coordinator = pb.NewCoordinatorServiceClient(conn)
	log.Printf("[%s] Connected to coordinator", s.id)

	// Connect to NATS
	log.Printf("[%s] Connecting to NATS at %s...", s.id, s.natsURL)
	natsConn, err := nats.Connect(s.natsURL)
	if err != nil {
		return fmt.Errorf("connect to NATS: %w", err)
	}
	s.natsConn = natsConn

	// Create JetStream context
	js, err := natsConn.JetStream()
	if err != nil {
		return fmt.Errorf("create jetstream: %w", err)
	}
	s.jetStream = js
	log.Printf("[%s] Connected to NATS JetStream", s.id)

	return nil
}

// Register registers this shard with the coordinator.
func (s *SimpleShard) Register() error {
	log.Printf("[%s] Registering with coordinator...", s.id)

	// Build region list
	var regions []*pb.RegionCoord
	for _, r := range s.regions {
		regions = append(regions, &pb.RegionCoord{X: r.X, Z: r.Z})
	}

	resp, err := s.coordinator.RegisterShard(context.Background(), &pb.ShardRegistrationRequest{
		Shard: &pb.ShardInfo{
			Id:       s.id,
			Address:  "localhost",
			Port:     25566,
			Capacity: 100,
			Regions:  regions,
		},
	})
	if err != nil {
		return fmt.Errorf("register shard: %w", err)
	}

	if !resp.Success {
		return fmt.Errorf("registration rejected by coordinator")
	}

	log.Printf("[%s] Registered successfully (coordinator: %s)", s.id, resp.CoordinatorId)
	return nil
}

// SubscribeToEvents subscribes to relevant event bus topics.
func (s *SimpleShard) SubscribeToEvents() error {
	// Subscribe to player join events
	_, err := s.jetStream.Subscribe("world.players.*", func(msg *nats.Msg) {
		s.handlePlayerEvent(msg)
	}, nats.Durable(s.id+"-players"))
	if err != nil {
		return fmt.Errorf("subscribe to player events: %w", err)
	}

	// Subscribe to block changes for our regions
	for _, region := range s.regions {
		topic := fmt.Sprintf("world.blocks.%d.%d", region.X, region.Z)
		_, err := s.jetStream.Subscribe(topic, func(msg *nats.Msg) {
			s.handleBlockChange(msg)
		}, nats.Durable(fmt.Sprintf("%s-blocks-%d-%d", s.id, region.X, region.Z)))
		if err != nil {
			return fmt.Errorf("subscribe to block changes: %w", err)
		}
		log.Printf("[%s] Subscribed to %s", s.id, topic)
	}

	log.Printf("[%s] Subscribed to all events", s.id)
	return nil
}

// handlePlayerEvent processes player join/leave events.
func (s *SimpleShard) handlePlayerEvent(msg *nats.Msg) {
	// Parse the event (simplified - in production use protobuf)
	log.Printf("[%s] Received player event: %s", s.id, string(msg.Data))
	
	// Acknowledge the message
	msg.Ack()
}

// handleBlockChange processes block change events.
func (s *SimpleShard) handleBlockChange(msg *nats.Msg) {
	// Parse the event (simplified - in production use protobuf)
	log.Printf("[%s] Received block change: %s", s.id, string(msg.Data))
	
	// In a real implementation:
	// 1. Parse the BlockChangeEvent protobuf
	// 2. Update local chunk data
	// 3. Notify nearby players
	// 4. Acknowledge the message
	
	msg.Ack()
}

// PublishBlockChange publishes a block change event to the event bus.
func (s *SimpleShard) PublishBlockChange(x, y, z int32, blockID, playerID string) error {
	// Determine which region this block belongs to
	regionX := x / (16 * 4) // 16 blocks per chunk, 4 chunks per region
	regionZ := z / (16 * 4)

	topic := fmt.Sprintf("world.blocks.%d.%d", regionX, regionZ)
	
	// In production, serialize a proper protobuf WorldEvent
	data := fmt.Sprintf(`{"x":%d,"y":%d,"z":%d,"block":"%s","player":"%s"}`,
		x, y, z, blockID, playerID)

	_, err := s.jetStream.Publish(topic, []byte(data))
	if err != nil {
		return fmt.Errorf("publish block change: %w", err)
	}

	log.Printf("[%s] Published block change to %s", s.id, topic)
	return nil
}

// SendHeartbeat sends periodic heartbeats to the coordinator.
func (s *SimpleShard) SendHeartbeat() {
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	for s.running {
		select {
		case <-ticker.C:
			_, err := s.coordinator.Heartbeat(context.Background(), &pb.HeartbeatRequest{
				ShardId:     s.id,
				PlayerCount: int32(len(s.players)),
				Load:        0.5, // Calculate based on CPU/memory
				Healthy:     true,
			})
			if err != nil {
				log.Printf("[%s] Heartbeat failed: %v", s.id, err)
			} else {
				log.Printf("[%s] Heartbeat sent (%d players)", s.id, len(s.players))
			}
		}
	}
}

// Run starts the shard and blocks until interrupted.
func (s *SimpleShard) Run() error {
	s.running = true

	// Start heartbeat goroutine
	go s.SendHeartbeat()

	log.Printf("[%s] Shard running. Press Ctrl+C to stop.", s.id)

	// Wait for interrupt signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	return s.Shutdown()
}

// Shutdown gracefully stops the shard.
func (s *SimpleShard) Shutdown() error {
	log.Printf("[%s] Shutting down...", s.id)
	s.running = false

	// Unregister from coordinator
	// Note: In production, implement an UnregisterShard RPC
	log.Printf("[%s] Unregistering from coordinator...", s.id)

	// Close connections
	if s.natsConn != nil {
		s.natsConn.Close()
	}
	if s.grpcConn != nil {
		s.grpcConn.Close()
	}

	log.Printf("[%s] Shutdown complete", s.id)
	return nil
}

func main() {
	// Configuration from environment or defaults
	shardID := os.Getenv("SHARD_ID")
	if shardID == "" {
		shardID = "simple-shard-1"
	}

	coordinatorAddr := os.Getenv("COORDINATOR_ADDR")
	if coordinatorAddr == "" {
		coordinatorAddr = "localhost:50051"
	}

	natsURL := os.Getenv("NATS_URL")
	if natsURL == "" {
		natsURL = nats.DefaultURL
	}

	// Create and run shard
	shard := NewSimpleShard(shardID, coordinatorAddr, natsURL)

	// Connect to infrastructure
	if err := shard.Connect(); err != nil {
		log.Fatalf("Failed to connect: %v", err)
	}
	defer shard.Shutdown()

	// Register with coordinator
	if err := shard.Register(); err != nil {
		log.Fatalf("Failed to register: %v", err)
	}

	// Subscribe to events
	if err := shard.SubscribeToEvents(); err != nil {
		log.Fatalf("Failed to subscribe to events: %v", err)
	}

	// Run until interrupted
	if err := shard.Run(); err != nil {
		log.Fatalf("Shard error: %v", err)
	}
}
