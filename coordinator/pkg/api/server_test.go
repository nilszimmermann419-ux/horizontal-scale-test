package api

import (
	"context"
	"testing"
	"time"

	"github.com/shardedmc/v2/coordinator/internal/allocator"
	"github.com/shardedmc/v2/coordinator/internal/registry"
)

func setupTestServer() (*CoordinatorServer, *registry.ShardRegistry) {
	reg := registry.NewShardRegistry(30*time.Second, 5*time.Second)
	server := NewCoordinatorServer(reg)
	return server, reg
}

func TestRegisterShard(t *testing.T) {
	server, reg := setupTestServer()
	defer reg.Stop()

	req := &RegisterShardRequest{
		ShardId:  "shard-test-1",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
		Regions:  []string{"0:0", "1:0", "0:1"},
	}

	resp, err := server.RegisterShard(context.Background(), req)
	if err != nil {
		t.Fatalf("RegisterShard failed: %v", err)
	}

	if !resp.Success {
		t.Error("Expected registration to succeed")
	}

	if len(resp.AllocatedRegions) != 3 {
		t.Errorf("Expected 3 allocated regions, got %d", len(resp.AllocatedRegions))
	}

	// Verify shard is in registry
	shard, ok := reg.GetShard("shard-test-1")
	if !ok {
		t.Fatal("Shard not found in registry after registration")
	}

	if shard.Address != "127.0.0.1" {
		t.Errorf("Expected address 127.0.0.1, got %s", shard.Address)
	}

	if shard.Capacity != 100 {
		t.Errorf("Expected capacity 100, got %d", shard.Capacity)
	}
}

func TestHeartbeat(t *testing.T) {
	server, reg := setupTestServer()
	defer reg.Stop()

	// First register a shard
	registerReq := &RegisterShardRequest{
		ShardId:  "shard-heartbeat",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
	}
	_, err := server.RegisterShard(context.Background(), registerReq)
	if err != nil {
		t.Fatalf("RegisterShard failed: %v", err)
	}

	// Send heartbeat
	heartbeatReq := &HeartbeatRequest{
		ShardId:     "shard-heartbeat",
		PlayerCount: 42,
		Load:        0.75,
	}

	resp, err := server.Heartbeat(context.Background(), heartbeatReq)
	if err != nil {
		t.Fatalf("Heartbeat failed: %v", err)
	}

	if !resp.Success {
		t.Error("Expected heartbeat to succeed")
	}

	// Verify shard stats were updated
	shard, ok := reg.GetShard("shard-heartbeat")
	if !ok {
		t.Fatal("Shard not found after heartbeat")
	}

	if shard.PlayerCount != 42 {
		t.Errorf("Expected player count 42, got %d", shard.PlayerCount)
	}

	if shard.Load != 0.75 {
		t.Errorf("Expected load 0.75, got %f", shard.Load)
	}
}

func TestRegionAllocation(t *testing.T) {
	server, reg := setupTestServer()
	defer reg.Stop()

	// Register multiple shards
	shards := []struct {
		id string
	}{
		{"shard-a"},
		{"shard-b"},
		{"shard-c"},
	}

	for _, s := range shards {
		req := &RegisterShardRequest{
			ShardId:  s.id,
			Address:  "127.0.0.1",
			Port:     25566,
			Capacity: 100,
			Regions:  []string{"0:0", "1:1", "2:2"},
		}
		_, err := server.RegisterShard(context.Background(), req)
		if err != nil {
			t.Fatalf("RegisterShard failed for %s: %v", s.id, err)
		}
	}

	// Verify consistent hashing assigns regions deterministically
	allShards := reg.GetAllShards()

	testRegions := [][2]int{
		{0, 0},
		{1, 1},
		{2, 2},
		{10, 10},
		{-5, -5},
	}

	for _, region := range testRegions {
		owner1, err1 := allocator.GetRegionOwner(region, allShards)
		owner2, err2 := allocator.GetRegionOwner(region, allShards)

		if err1 != nil {
			t.Errorf("Unexpected error: %v", err1)
		}

		if owner1 == "" {
			t.Error("Expected region to have an owner")
		}

		if err2 != nil {
			t.Errorf("Unexpected error: %v", err2)
		}

		if owner1 != owner2 {
			t.Errorf("Region allocation not deterministic: %s vs %s", owner1, owner2)
		}
	}
}

func TestChunkOwnershipLookup(t *testing.T) {
	server, reg := setupTestServer()
	defer reg.Stop()

	// Register a shard
	req := &RegisterShardRequest{
		ShardId:  "shard-chunk",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
	}
	_, err := server.RegisterShard(context.Background(), req)
	if err != nil {
		t.Fatalf("RegisterShard failed: %v", err)
	}

	// Look up chunk owner
	chunkReq := &GetChunkOwnerRequest{
		ChunkX: 10,
		ChunkZ: -5,
	}

	resp, err := server.GetChunkOwner(context.Background(), chunkReq)
	if err != nil {
		t.Fatalf("GetChunkOwner failed: %v", err)
	}

	if resp.ShardId == "" {
		t.Error("Expected chunk to have an owner")
	}

	// Same chunk should always return same owner
	resp2, err := server.GetChunkOwner(context.Background(), chunkReq)
	if err != nil {
		t.Fatalf("GetChunkOwner failed on second call: %v", err)
	}

	if resp.ShardId != resp2.ShardId {
		t.Errorf("Chunk owner not consistent: %s vs %s", resp.ShardId, resp2.ShardId)
	}
}

func TestPlayerRouting(t *testing.T) {
	server, reg := setupTestServer()
	defer reg.Stop()

	// Register shards
	for _, id := range []string{"shard-1", "shard-2"} {
		req := &RegisterShardRequest{
			ShardId:  id,
			Address:  "127.0.0.1",
			Port:     25566,
			Capacity: 100,
		}
		_, err := server.RegisterShard(context.Background(), req)
		if err != nil {
			t.Fatalf("RegisterShard failed for %s: %v", id, err)
		}
	}

	// Record player position
	playerID := "player-test-123"
	posReq := &RecordPlayerPositionRequest{
		PlayerId: playerID,
		ChunkX:   5,
		ChunkZ:   10,
	}

	posResp, err := server.RecordPlayerPosition(context.Background(), posReq)
	if err != nil {
		t.Fatalf("RecordPlayerPosition failed: %v", err)
	}

	if !posResp.Success {
		t.Error("Expected position recording to succeed")
	}

	// Get player shard assignment
	shardReq := &GetPlayerShardRequest{
		PlayerId: playerID,
	}

	shardResp, err := server.GetPlayerShard(context.Background(), shardReq)
	if err != nil {
		t.Fatalf("GetPlayerShard failed: %v", err)
	}

	if shardResp.ShardId == "" {
		t.Error("Expected player to be assigned to a shard")
	}

	// Move player and verify reassignment
	posReq2 := &RecordPlayerPositionRequest{
		PlayerId: playerID,
		ChunkX:   100,
		ChunkZ:   200,
	}

	_, err = server.RecordPlayerPosition(context.Background(), posReq2)
	if err != nil {
		t.Fatalf("RecordPlayerPosition failed on move: %v", err)
	}

	shardResp2, err := server.GetPlayerShard(context.Background(), shardReq)
	if err != nil {
		t.Fatalf("GetPlayerShard failed after move: %v", err)
	}

	// Note: Depending on consistent hashing, player may or may not change shards
	// The important thing is that they ARE assigned to a shard
	if shardResp2.ShardId == "" {
		t.Error("Expected player to still be assigned to a shard after move")
	}
}
