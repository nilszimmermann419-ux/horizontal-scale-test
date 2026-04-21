package benchmark

import (
	"fmt"
	"testing"
	"time"

	"github.com/shardedmc/v2/coordinator/internal/allocator"
	"github.com/shardedmc/v2/coordinator/internal/registry"
)

// BenchmarkRegionAllocation tests consistent hashing performance
func BenchmarkRegionAllocation(b *testing.B) {
	shards := []*registry.ShardInfo{
		{ID: "shard-1", Capacity: 100, Healthy: true},
		{ID: "shard-2", Capacity: 100, Healthy: true},
		{ID: "shard-3", Capacity: 100, Healthy: true},
		{ID: "shard-4", Capacity: 100, Healthy: true},
	}

	regions := [][2]int{
		{0, 0}, {1, 0}, {0, 1}, {1, 1},
		{-1, 0}, {0, -1}, {-1, -1}, {2, 2},
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		for _, region := range regions {
			_, err := allocator.GetRegionOwner(region, shards)
			if err != nil {
				b.Fatalf("GetRegionOwner failed: %v", err)
			}
		}
	}
}

// BenchmarkShardRegistration tests registry performance
func BenchmarkShardRegistration(b *testing.B) {
	reg := registry.NewShardRegistry(30*time.Second, 10*time.Second)
	defer reg.Stop()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		shard := &registry.ShardInfo{
			ID:       fmt.Sprintf("shard-%d", i),
			Address:  "127.0.0.1",
			Port:     25565 + i%1000,
			Capacity: 100,
		}
		if err := reg.RegisterShard(shard); err != nil {
			b.Fatalf("RegisterShard failed: %v", err)
		}
		reg.UnregisterShard(shard.ID)
	}
}

// BenchmarkHeartbeat tests heartbeat processing
func BenchmarkHeartbeat(b *testing.B) {
	reg := registry.NewShardRegistry(30*time.Second, 10*time.Second)
	defer reg.Stop()

	// Register a shard first
	shard := &registry.ShardInfo{
		ID:       "shard-1",
		Address:  "127.0.0.1",
		Port:     25565,
		Capacity: 100,
	}
	if err := reg.RegisterShard(shard); err != nil {
		b.Fatalf("RegisterShard failed: %v", err)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if err := reg.UpdateHeartbeat("shard-1"); err != nil {
			b.Fatalf("UpdateHeartbeat failed: %v", err)
		}
	}
}

// BenchmarkPlayerRouting tests player routing performance
func BenchmarkPlayerRouting(b *testing.B) {
	shards := []*registry.ShardInfo{
		{ID: "shard-1", Capacity: 100, PlayerCount: 50, Healthy: true},
		{ID: "shard-2", Capacity: 100, PlayerCount: 30, Healthy: true},
		{ID: "shard-3", Capacity: 100, PlayerCount: 20, Healthy: true},
		{ID: "shard-4", Capacity: 100, PlayerCount: 40, Healthy: true},
	}

	positions := [][2]int{
		{100, 200}, {-50, 300}, {1000, -500}, {0, 0},
		{500, 500}, {-1000, -1000}, {250, 250}, {-250, 250},
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		pos := positions[i%len(positions)]
		_, err := allocator.GetChunkOwner(pos, shards)
		if err != nil {
			b.Fatalf("GetChunkOwner failed: %v", err)
		}
	}
}

// BenchmarkGetHealthyShards tests retrieving healthy shards
func BenchmarkGetHealthyShards(b *testing.B) {
	reg := registry.NewShardRegistry(30*time.Second, 10*time.Second)
	defer reg.Stop()

	// Register multiple shards
	for i := 0; i < 20; i++ {
		shard := &registry.ShardInfo{
			ID:          fmt.Sprintf("shard-%d", i),
			Address:     "127.0.0.1",
			Port:        25565 + i,
			Capacity:    100,
			PlayerCount: i * 3,
			Load:        float64(i) * 0.03,
			Healthy:     i%3 != 0, // Some shards unhealthy
		}
		if err := reg.RegisterShard(shard); err != nil {
			b.Fatalf("RegisterShard failed: %v", err)
		}
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_ = reg.GetHealthyShards()
	}
}
