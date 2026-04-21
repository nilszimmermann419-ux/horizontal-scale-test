package shard

import (
	"context"
	"log"
	"sync"
	"sync/atomic"
	"time"
)

// Shard represents a Minecraft server shard
type Shard struct {
	ID       string
	Address  string
	Port     int
	Capacity int

	// Atomic counters for thread-safe access
	playerCount    int64
	load           float64
	lastHeartbeat  atomic.Int64 // Unix timestamp in nanoseconds
	healthy        atomic.Bool

	// Connection to shard - protected by mutex
	connMu sync.RWMutex
	conn   interface{}
}

// Manager manages all registered shards
type Manager struct {
	shards sync.Map // map[string]*Shard
	mu     sync.RWMutex
}

// NewManager creates a new shard manager
func NewManager() *Manager {
	return &Manager{}
}

// RegisterShard registers a new shard
func (m *Manager) RegisterShard(id, address string, port, capacity int) (*Shard, error) {
	shard := &Shard{
		ID:       id,
		Address:  address,
		Port:     port,
		Capacity: capacity,
	}
	shard.healthy.Store(true)
	shard.lastHeartbeat.Store(time.Now().UnixNano())

	m.shards.Store(id, shard)
	log.Printf("Registered shard %s at %s:%d (capacity: %d)", id, address, port, capacity)

	return shard, nil
}

// UnregisterShard removes a shard from management
func (m *Manager) UnregisterShard(id string) {
	m.shards.Delete(id)
	log.Printf("Unregistered shard %s", id)
}

// GetShard returns a shard by ID
func (m *Manager) GetShard(id string) (*Shard, bool) {
	val, ok := m.shards.Load(id)
	if !ok {
		return nil, false
	}
	return val.(*Shard), true
}

// GetLeastLoadedShard returns the shard with the lowest load
func (m *Manager) GetLeastLoadedShard() *Shard {
	var best *Shard
	var bestLoad float64 = 2.0 // Max load is 1.0

	m.shards.Range(func(key, value interface{}) bool {
		shard := value.(*Shard)

		if !shard.healthy.Load() {
			return true
		}

		load := float64(atomic.LoadInt64(&shard.playerCount)) / float64(shard.Capacity)
		if load < bestLoad {
			bestLoad = load
			best = shard
		}

		return true
	})

	return best
}

// GetAllShards returns all registered shards
func (m *Manager) GetAllShards() []*Shard {
	var shards []*Shard
	m.shards.Range(func(key, value interface{}) bool {
		shards = append(shards, value.(*Shard))
		return true
	})
	return shards
}

// ShardCount returns the total number of registered shards
func (m *Manager) ShardCount() int {
	count := 0
	m.shards.Range(func(_, _ interface{}) bool {
		count++
		return true
	})
	return count
}

// HealthyShardCount returns the number of healthy shards
func (m *Manager) HealthyShardCount() int {
	count := 0
	m.shards.Range(func(_, value interface{}) bool {
		shard := value.(*Shard)
		if shard.healthy.Load() {
			count++
		}
		return true
	})
	return count
}

// StartHealthChecks begins periodic health checking
func (m *Manager) StartHealthChecks(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			m.checkHealth()
		}
	}
}

func (m *Manager) checkHealth() {
	m.shards.Range(func(key, value interface{}) bool {
		shard := value.(*Shard)

		// Check if heartbeat is recent (within 10 seconds) using atomic read
		lastBeat := time.Unix(0, shard.lastHeartbeat.Load())
		if time.Since(lastBeat) > 10*time.Second {
			wasHealthy := shard.healthy.Load()
			shard.healthy.Store(false)
			if wasHealthy {
				log.Printf("Shard %s marked unhealthy (no heartbeat)", shard.ID)
			}
		}

		return true
	})
}

// AddPlayer increments the player count with bounds checking
func (s *Shard) AddPlayer() {
	for {
		current := atomic.LoadInt64(&s.playerCount)
		if current >= int64(s.Capacity) {
			log.Printf("Shard %s at capacity (%d/%d)", s.ID, current, s.Capacity)
			return
		}
		if atomic.CompareAndSwapInt64(&s.playerCount, current, current+1) {
			return
		}
	}
}

// RemovePlayer decrements the player count with bounds checking
func (s *Shard) RemovePlayer() {
	for {
		current := atomic.LoadInt64(&s.playerCount)
		if current <= 0 {
			return
		}
		if atomic.CompareAndSwapInt64(&s.playerCount, current, current-1) {
			return
		}
	}
}

// PlayerCount returns the current player count
func (s *Shard) PlayerCount() int32 {
	return int32(atomic.LoadInt64(&s.playerCount))
}

// UpdateHeartbeat updates the last heartbeat time and marks shard as healthy
func (s *Shard) UpdateHeartbeat() {
	s.lastHeartbeat.Store(time.Now().UnixNano())
	s.healthy.Store(true)
}

// IsHealthy returns whether the shard is healthy
func (s *Shard) IsHealthy() bool {
	return s.healthy.Load()
}

// SetConnection sets the shard's network connection (thread-safe)
func (s *Shard) SetConnection(conn interface{}) {
	s.connMu.Lock()
	defer s.connMu.Unlock()
	s.conn = conn
}

// GetConnection returns the shard's network connection (thread-safe)
func (s *Shard) GetConnection() interface{} {
	s.connMu.RLock()
	defer s.connMu.RUnlock()
	return s.conn
}

// Load returns the current load ratio (0.0 - 1.0)
func (s *Shard) Load() float64 {
	return float64(atomic.LoadInt64(&s.playerCount)) / float64(s.Capacity)
}
