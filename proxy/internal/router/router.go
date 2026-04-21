package router

import (
	"context"
	"fmt"
	"net"
	"sync"

	"github.com/shardedmc/v2/proxy/internal/connection"
	"google.golang.org/grpc"
)

// CoordinatorClient interface for shard assignment
type CoordinatorClient interface {
	GetShardForPlayer(ctx context.Context, playerID string, position *Position) (*ShardAssignment, error)
}

type Position struct {
	X, Y, Z float64
}

type ShardAssignment struct {
	ShardID string
	Address string
}

type Router struct {
	coordinator CoordinatorClient
	players     map[string]*connection.ShardConn
	mu          sync.RWMutex
}

func NewRouter(coordinatorAddr string) (*Router, error) {
	conn, err := grpc.Dial(coordinatorAddr, grpc.WithInsecure())
	if err != nil {
		return nil, fmt.Errorf("connect to coordinator: %w", err)
	}

	_ = conn

	return &Router{
		coordinator: &coordinatorClient{addr: coordinatorAddr},
		players:     make(map[string]*connection.ShardConn),
	}, nil
}

func (r *Router) RoutePlayer(playerID string, position *Position) (*connection.ShardConn, error) {
	r.mu.RLock()
	existingShard := r.players[playerID]
	r.mu.RUnlock()

	assignment, err := r.coordinator.GetShardForPlayer(context.Background(), playerID, position)
	if err != nil {
		return nil, fmt.Errorf("get shard assignment: %w", err)
	}

	if existingShard != nil && existingShard.ShardID == assignment.ShardID && existingShard.IsConnected() {
		return existingShard, nil
	}

	if existingShard != nil {
		r.mu.Lock()
		delete(r.players, playerID)
		r.mu.Unlock()
		existingShard.Close()
	}

	conn, err := net.Dial("tcp", assignment.Address)
	if err != nil {
		return nil, fmt.Errorf("connect to shard %s at %s: %w", assignment.ShardID, assignment.Address, err)
	}

	shard := connection.NewShardConn(conn, assignment.ShardID)

	r.mu.Lock()
	r.players[playerID] = shard
	r.mu.Unlock()

	return shard, nil
}

func (r *Router) RemovePlayer(playerID string) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if shard, ok := r.players[playerID]; ok {
		shard.Close()
		delete(r.players, playerID)
	}
}

func (r *Router) GetPlayerShard(playerID string) *connection.ShardConn {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.players[playerID]
}

func (r *Router) Close() {
	r.mu.Lock()
	defer r.mu.Unlock()

	for _, shard := range r.players {
		shard.Close()
	}
}

type coordinatorClient struct {
	addr string
}

func (c *coordinatorClient) GetShardForPlayer(ctx context.Context, playerID string, position *Position) (*ShardAssignment, error) {
	return &ShardAssignment{
		ShardID: "shard-1",
		Address: "localhost:25566",
	}, nil
}
