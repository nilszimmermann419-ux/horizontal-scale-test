package integration

import (
	"context"
	"testing"

	"github.com/shardedmc/coordinator/internal/shard"
	"github.com/shardedmc/coordinator/internal/storage"
)

// TestShardRegistration tests that shards can register and be discovered
func TestShardRegistration(t *testing.T) {
	mgr := shard.NewManager()

	// Register a shard
	s, err := mgr.RegisterShard("shard-1", "localhost", 25565, 2000)
	if err != nil {
		t.Fatalf("Failed to register shard: %v", err)
	}

	// Verify shard exists
	found, ok := mgr.GetShard("shard-1")
	if !ok {
		t.Fatal("Shard not found after registration")
	}

	if found.ID != "shard-1" {
		t.Errorf("Expected shard ID shard-1, got %s", found.ID)
	}

	if found.Capacity != 2000 {
		t.Errorf("Expected capacity 2000, got %d", found.Capacity)
	}

	// Test player count
	if s.PlayerCount() != 0 {
		t.Errorf("Expected 0 players, got %d", s.PlayerCount())
	}

	s.AddPlayer()
	if s.PlayerCount() != 1 {
		t.Errorf("Expected 1 player, got %d", s.PlayerCount())
	}

	s.RemovePlayer()
	if s.PlayerCount() != 0 {
		t.Errorf("Expected 0 players after removal, got %d", s.PlayerCount())
	}
}

// TestLeastLoadedShard tests the load balancing algorithm
func TestLeastLoadedShard(t *testing.T) {
	mgr := shard.NewManager()

	// Register multiple shards
	shard1, _ := mgr.RegisterShard("shard-1", "localhost", 25565, 100)
	shard2, _ := mgr.RegisterShard("shard-2", "localhost", 25566, 100)
	shard3, _ := mgr.RegisterShard("shard-3", "localhost", 25567, 100)

	// Add players to shard1 and shard2
	shard1.AddPlayer()
	shard1.AddPlayer()
	shard2.AddPlayer()

	// shard3 has 0 players, so it should be least loaded
	leastLoaded := mgr.GetLeastLoadedShard()
	if leastLoaded == nil {
		t.Fatal("Expected least loaded shard, got nil")
	}

	if leastLoaded.ID != "shard-3" {
		t.Errorf("Expected shard-3 to be least loaded, got %s", leastLoaded.ID)
	}

	// Now add players to shard3
	shard3.AddPlayer()
	shard3.AddPlayer()
	shard3.AddPlayer()

	// shard2 has 1 player, so it should be least loaded now
	leastLoaded = mgr.GetLeastLoadedShard()
	if leastLoaded.ID != "shard-2" {
		t.Errorf("Expected shard-2 to be least loaded, got %s", leastLoaded.ID)
	}
}

// TestStorageEngine tests the storage interface
func TestStorageEngine(t *testing.T) {
	engine, err := storage.NewEngine(t.TempDir())
	if err != nil {
		t.Fatalf("Failed to create storage engine: %v", err)
	}
	defer engine.Close()

	ctx := context.Background()

	// Test chunk storage
	chunkData := &storage.ChunkData{
		X: 0,
		Z: 0,
		Sections: []storage.SectionData{
			{
				Y:           0,
				BlockStates: []byte{1, 2, 3},
				SkyLight:    make([]byte, 2048),
				BlockLight:  make([]byte, 2048),
			},
		},
	}

	err = engine.PutChunk(ctx, "world", "overworld", 0, 0, chunkData)
	if err != nil {
		t.Fatalf("Failed to put chunk: %v", err)
	}

	retrieved, err := engine.GetChunk(ctx, "world", "overworld", 0, 0)
	if err != nil {
		t.Fatalf("Failed to get chunk: %v", err)
	}

	if retrieved.X != 0 || retrieved.Z != 0 {
		t.Errorf("Expected chunk at 0,0, got %d,%d", retrieved.X, retrieved.Z)
	}

	// Test player data
	playerData := &storage.PlayerData{
		UUID:     "test-uuid",
		Username: "TestPlayer",
		Position: storage.Vec3D{X: 100, Y: 64, Z: 200},
		Health:   20.0,
		Food:     20,
	}

	err = engine.PutPlayerData(ctx, "test-uuid", playerData)
	if err != nil {
		t.Fatalf("Failed to put player data: %v", err)
	}

	retrievedPlayer, err := engine.GetPlayerData(ctx, "test-uuid")
	if err != nil {
		t.Fatalf("Failed to get player data: %v", err)
	}

	if retrievedPlayer.Username != "TestPlayer" {
		t.Errorf("Expected username TestPlayer, got %s", retrievedPlayer.Username)
	}
}

// TestHealthChecks tests shard health monitoring
func TestHealthChecks(t *testing.T) {
	mgr := shard.NewManager()

	shard1, _ := mgr.RegisterShard("shard-1", "localhost", 25565, 100)

	// Shard should be healthy initially
	if !shard1.IsHealthy() {
		t.Fatal("New shard should be healthy")
	}

	// Update heartbeat
	shard1.UpdateHeartbeat()

	if !shard1.IsHealthy() {
		t.Fatal("Shard should be healthy after heartbeat")
	}
}

// BenchmarkShardRegistration benchmarks shard registration
func BenchmarkShardRegistration(b *testing.B) {
	mgr := shard.NewManager()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		mgr.RegisterShard(
			string(rune('a'+i%26)),
			"localhost",
			25565+i,
			2000,
		)
	}
}

// BenchmarkGetLeastLoadedShard benchmarks load balancing
func BenchmarkGetLeastLoadedShard(b *testing.B) {
	mgr := shard.NewManager()

	// Register 100 shards
	for i := 0; i < 100; i++ {
		s, _ := mgr.RegisterShard(
			string(rune('a'+i%26))+string(rune('0'+i/26)),
			"localhost",
			25565+i,
			2000,
		)
		// Add random player counts
		for j := 0; j < i%50; j++ {
			s.AddPlayer()
		}
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		mgr.GetLeastLoadedShard()
	}
}
