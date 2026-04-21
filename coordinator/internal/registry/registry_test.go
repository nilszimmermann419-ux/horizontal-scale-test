package registry

import (
	"testing"
	"time"
)

func TestShardRegistration(t *testing.T) {
	reg := NewShardRegistry(30*time.Second, 5*time.Second)
	defer reg.Stop()

	shard := &ShardInfo{
		ID:       "shard-test-1",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
	}

	reg.RegisterShard(shard)

	// Verify shard is in registry
	retrieved, ok := reg.GetShard("shard-test-1")
	if !ok {
		t.Fatal("Shard not found in registry after registration")
	}

	if retrieved.Address != "127.0.0.1" {
		t.Errorf("Expected address 127.0.0.1, got %s", retrieved.Address)
	}

	if retrieved.Capacity != 100 {
		t.Errorf("Expected capacity 100, got %d", retrieved.Capacity)
	}

	if !retrieved.Healthy {
		t.Error("Expected shard to be healthy after registration")
	}
}

func TestHeartbeat(t *testing.T) {
	reg := NewShardRegistry(30*time.Second, 5*time.Second)
	defer reg.Stop()

	// Register shard
	shard := &ShardInfo{
		ID:       "shard-heartbeat",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
	}
	reg.RegisterShard(shard)

	// Update heartbeat
	err := reg.UpdateHeartbeat("shard-heartbeat")
	if err != nil {
		t.Fatalf("UpdateHeartbeat failed: %v", err)
	}

	// Verify shard is still healthy
	retrieved, ok := reg.GetShard("shard-heartbeat")
	if !ok {
		t.Fatal("Shard not found after heartbeat")
	}

	if !retrieved.Healthy {
		t.Error("Expected shard to be healthy after heartbeat")
	}
}

func TestHeartbeatTimeout(t *testing.T) {
	// Use very short timeout for testing
	reg := NewShardRegistry(100*time.Millisecond, 50*time.Millisecond)
	defer reg.Stop()

	// Register shard
	shard := &ShardInfo{
		ID:       "shard-timeout",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
	}
	reg.RegisterShard(shard)

	// Verify shard is healthy
	if len(reg.GetHealthyShards()) != 1 {
		t.Error("Expected 1 healthy shard")
	}

	// Wait for heartbeat timeout
	time.Sleep(200 * time.Millisecond)

	// Shard should be removed due to timeout
	if len(reg.GetAllShards()) != 0 {
		t.Error("Expected shard to be removed after heartbeat timeout")
	}
}

func TestGetHealthyShards(t *testing.T) {
	reg := NewShardRegistry(30*time.Second, 5*time.Second)
	defer reg.Stop()

	// Register multiple shards
	shards := []*ShardInfo{
		{ID: "shard-1", Address: "127.0.0.1", Port: 25566, Healthy: true, Capacity: 100},
		{ID: "shard-2", Address: "127.0.0.1", Port: 25567, Healthy: true, Capacity: 100},
		{ID: "shard-3", Address: "127.0.0.1", Port: 25568, Healthy: true, Capacity: 100},
	}

	for _, shard := range shards {
		reg.RegisterShard(shard)
	}

	healthy := reg.GetHealthyShards()
	if len(healthy) != 3 {
		t.Errorf("Expected 3 healthy shards, got %d", len(healthy))
	}
}

func TestUnregisterShard(t *testing.T) {
	reg := NewShardRegistry(30*time.Second, 5*time.Second)
	defer reg.Stop()

	// Register and then unregister
	shard := &ShardInfo{
		ID:       "shard-unregister",
		Address:  "127.0.0.1",
		Port:     25566,
		Capacity: 100,
	}
	reg.RegisterShard(shard)

	if len(reg.GetAllShards()) != 1 {
		t.Error("Expected 1 shard after registration")
	}

	reg.UnregisterShard("shard-unregister")

	if len(reg.GetAllShards()) != 0 {
		t.Error("Expected 0 shards after unregistration")
	}

	_, ok := reg.GetShard("shard-unregister")
	if ok {
		t.Error("Expected shard to not be found after unregistration")
	}
}
