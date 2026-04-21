package registry

import (
	"errors"
	"fmt"
	"sync"
	"time"
)

// ShardInfo represents a single game shard/server
type ShardInfo struct {
	ID            string
	Address       string
	Port          int
	Capacity      int
	PlayerCount   int
	Load          float64
	Healthy       bool
	LastHeartbeat time.Time
}

// ShardRegistry maintains a thread-safe map of all known shards
type ShardRegistry struct {
	mu               sync.RWMutex
	shards           map[string]*ShardInfo
	heartbeatTimeout time.Duration
	checkInterval    time.Duration
	stopCh           chan struct{}
}

// NewShardRegistry creates a new registry and starts the background heartbeat checker
func NewShardRegistry(heartbeatTimeout, checkInterval time.Duration) *ShardRegistry {
	r := &ShardRegistry{
		shards:           make(map[string]*ShardInfo),
		heartbeatTimeout: heartbeatTimeout,
		checkInterval:    checkInterval,
		stopCh:           make(chan struct{}),
	}
	go r.heartbeatChecker()
	return r
}

// RegisterShard adds or updates a shard in the registry
func (r *ShardRegistry) RegisterShard(shard *ShardInfo) error {
	if err := validateShardInfo(shard); err != nil {
		return err
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	// Check for duplicate shard registration
	if existing, ok := r.shards[shard.ID]; ok {
		// Allow re-registration only if address/port matches (same instance restarting)
		if existing.Address != shard.Address || existing.Port != shard.Port {
			return fmt.Errorf("shard ID collision: %s already registered with different address/port", shard.ID)
		}
	}

	shard.LastHeartbeat = time.Now()
	shard.Healthy = true
	r.shards[shard.ID] = shard
	return nil
}

func validateShardInfo(shard *ShardInfo) error {
	if shard.ID == "" {
		return errors.New("shard ID cannot be empty")
	}
	if shard.Port < 1 || shard.Port > 65535 {
		return fmt.Errorf("shard port must be between 1 and 65535, got %d", shard.Port)
	}
	if shard.Capacity <= 0 {
		return fmt.Errorf("shard capacity must be greater than 0, got %d", shard.Capacity)
	}
	return nil
}

// UnregisterShard removes a shard from the registry
func (r *ShardRegistry) UnregisterShard(shardID string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.shards, shardID)
}

// UpdateHeartbeat updates the last heartbeat time for a shard
func (r *ShardRegistry) UpdateHeartbeat(shardID string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	shard, ok := r.shards[shardID]
	if !ok {
		return errors.New("shard not found: " + shardID)
	}
	shard.LastHeartbeat = time.Now()
	shard.Healthy = true
	return nil
}

// GetShard retrieves a specific shard by ID
func (r *ShardRegistry) GetShard(shardID string) (*ShardInfo, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	shard, ok := r.shards[shardID]
	return shard, ok
}

// GetHealthyShards returns all shards marked as healthy and not overloaded
func (r *ShardRegistry) GetHealthyShards() []*ShardInfo {
	r.mu.RLock()
	defer r.mu.RUnlock()
	var healthy []*ShardInfo
	for _, shard := range r.shards {
		if shard.Healthy && shard.Load < 1.0 && shard.PlayerCount < shard.Capacity {
			healthy = append(healthy, shard)
		}
	}
	return healthy
}

// GetAllShards returns all registered shards
func (r *ShardRegistry) GetAllShards() []*ShardInfo {
	r.mu.RLock()
	defer r.mu.RUnlock()
	var all []*ShardInfo
	for _, shard := range r.shards {
		all = append(all, shard)
	}
	return all
}

// heartbeatChecker runs in a background goroutine to remove dead shards
func (r *ShardRegistry) heartbeatChecker() {
	ticker := time.NewTicker(r.checkInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			r.checkHeartbeats()
		case <-r.stopCh:
			return
		}
	}
}

// checkHeartbeats removes shards that haven't sent a heartbeat within the timeout
func (r *ShardRegistry) checkHeartbeats() {
	r.mu.Lock()
	defer r.mu.Unlock()
	now := time.Now()
	for id, shard := range r.shards {
		if now.Sub(shard.LastHeartbeat) > r.heartbeatTimeout {
			shard.Healthy = false
			delete(r.shards, id)
		}
	}
}

// Stop halts the background heartbeat checker
func (r *ShardRegistry) Stop() {
	close(r.stopCh)
}
