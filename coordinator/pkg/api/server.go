package api

import (
	"context"
	"log"
	"strconv"
	"strings"
	"sync"

	"github.com/shardedmc/v2/coordinator/internal/allocator"
	"github.com/shardedmc/v2/coordinator/internal/registry"
)

// CoordinatorServer implements the CoordinatorService gRPC interface
type CoordinatorServer struct {
	UnimplementedCoordinatorServiceServer
	registry *registry.ShardRegistry
	mu       sync.RWMutex
	players  map[string]string // playerID -> shardID
}

// NewCoordinatorServer creates a new CoordinatorServer instance
func NewCoordinatorServer(reg *registry.ShardRegistry) *CoordinatorServer {
	return &CoordinatorServer{
		registry: reg,
		players:  make(map[string]string),
	}
}

// parseRegion converts a string like "x:z" to [2]int
func parseRegion(s string) [2]int {
	parts := strings.Split(s, ":")
	if len(parts) != 2 {
		return [2]int{0, 0}
	}
	x, _ := strconv.Atoi(parts[0])
	z, _ := strconv.Atoi(parts[1])
	return [2]int{x, z}
}

// RegisterShard adds a shard to the registry and returns region allocations
func (s *CoordinatorServer) RegisterShard(ctx context.Context, req *RegisterShardRequest) (*RegisterShardResponse, error) {
	shard := &registry.ShardInfo{
		ID:       req.ShardId,
		Address:  req.Address,
		Port:     int(req.Port),
		Capacity: int(req.Capacity),
		Healthy:  true,
	}
	s.registry.RegisterShard(shard)

	allShards := s.registry.GetAllShards()
	allocation := make(map[string]string)

	for _, region := range req.Regions {
		coord := parseRegion(region)
		owner, err := allocator.GetRegionOwner(coord, allShards)
		if err != nil {
			return nil, err
		}
		allocation[region] = owner
	}

	log.Printf("Registered shard %s with %d regions", req.ShardId, len(req.Regions))

	return &RegisterShardResponse{
		AllocatedRegions: allocation,
		Success:          true,
	}, nil
}

// Heartbeat updates shard health and statistics
func (s *CoordinatorServer) Heartbeat(ctx context.Context, req *HeartbeatRequest) (*HeartbeatResponse, error) {
	if err := s.registry.UpdateHeartbeat(req.ShardId); err != nil {
		return &HeartbeatResponse{Success: false}, err
	}

	// Update player count and load if shard exists
	shard, ok := s.registry.GetShard(req.ShardId)
	if ok {
		shard.PlayerCount = int(req.PlayerCount)
		shard.Load = req.Load
	}

	log.Printf("Heartbeat received from shard %s (players: %d, load: %.2f)", req.ShardId, req.PlayerCount, req.Load)

	return &HeartbeatResponse{Success: true}, nil
}

// GetChunkOwner returns the shard responsible for a specific chunk
func (s *CoordinatorServer) GetChunkOwner(ctx context.Context, req *GetChunkOwnerRequest) (*GetChunkOwnerResponse, error) {
	shards := s.registry.GetHealthyShards()
	if len(shards) == 0 {
		return &GetChunkOwnerResponse{}, nil
	}

	coord := [2]int{int(req.ChunkX), int(req.ChunkZ)}
	owner, err := allocator.GetChunkOwner(coord, shards)
	if err != nil {
		return nil, err
	}

	return &GetChunkOwnerResponse{ShardId: owner}, nil
}

// GetRegionMap returns the complete region to shard mapping
func (s *CoordinatorServer) GetRegionMap(ctx context.Context, req *GetRegionMapRequest) (*GetRegionMapResponse, error) {
	// For demonstration, return a mapping of all known shard regions
	// In production, this would come from a persistent region store
	s.mu.RLock()
	defer s.mu.RUnlock()

	regionMap := make(map[string]string)
	shards := s.registry.GetHealthyShards()

	// Generate region map based on current shards
	// This is a simplified example - real implementation would track all regions
	for _, shard := range shards {
		regionMap[shard.ID] = shard.ID
	}

	return &GetRegionMapResponse{RegionMap: regionMap}, nil
}

// GetPlayerShard returns the shard assigned to a player
func (s *CoordinatorServer) GetPlayerShard(ctx context.Context, req *GetPlayerShardRequest) (*GetPlayerShardResponse, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	shardID, ok := s.players[req.PlayerId]
	if !ok {
		return &GetPlayerShardResponse{}, nil
	}

	return &GetPlayerShardResponse{ShardId: shardID}, nil
}

// RecordPlayerPosition updates player location and assigns them to the appropriate shard
func (s *CoordinatorServer) RecordPlayerPosition(ctx context.Context, req *RecordPlayerPositionRequest) (*RecordPlayerPositionResponse, error) {
	shards := s.registry.GetHealthyShards()
	if len(shards) == 0 {
		return &RecordPlayerPositionResponse{Success: false}, nil
	}

	coord := [2]int{int(req.ChunkX), int(req.ChunkZ)}
	shardID, err := allocator.GetChunkOwner(coord, shards)
	if err != nil {
		return &RecordPlayerPositionResponse{Success: false}, err
	}

	s.mu.Lock()
	s.players[req.PlayerId] = shardID
	s.mu.Unlock()

	log.Printf("Player %s moved to chunk (%d, %d), assigned to shard %s",
		req.PlayerId, req.ChunkX, req.ChunkZ, shardID)

	return &RecordPlayerPositionResponse{Success: true}, nil
}
