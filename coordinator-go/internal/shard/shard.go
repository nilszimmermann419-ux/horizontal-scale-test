package shard

import (
	"fmt"
	"sync/atomic"
)

// ShardStats contains statistics for a shard
type ShardStats struct {
	ID            string
	Address       string
	Port          int
	Capacity      int
	PlayerCount   int32
	Load          float64
	Healthy       bool
	LastHeartbeat int64
}

// Stats returns the current statistics for this shard
func (s *Shard) Stats() ShardStats {
	return ShardStats{
		ID:            s.ID,
		Address:       s.Address,
		Port:          s.Port,
		Capacity:      s.Capacity,
		PlayerCount:   atomic.LoadInt32(&s.playerCount),
		Load:          s.Load(),
		Healthy:       s.healthy.Load(),
		LastHeartbeat: s.lastHeartbeat.Unix(),
	}
}

// String returns a string representation of the shard
func (s *Shard) String() string {
	return fmt.Sprintf("Shard{id=%s, addr=%s:%d, players=%d/%d, load=%.2f, healthy=%v}",
		s.ID, s.Address, s.Port, atomic.LoadInt32(&s.playerCount), s.Capacity, s.Load(), s.healthy.Load())
}

// ShardMetrics contains aggregated metrics across all shards
type ShardMetrics struct {
	TotalShards   int
	HealthyShards int
	TotalPlayers  int32
	TotalCapacity int
	AverageLoad   float64
}

// GetMetrics returns aggregated metrics for all shards
func (m *Manager) GetMetrics() ShardMetrics {
	metrics := ShardMetrics{}
	var totalLoad float64

	m.shards.Range(func(key, value interface{}) bool {
		shard := value.(*Shard)
		metrics.TotalShards++
		metrics.TotalCapacity += shard.Capacity
		metrics.TotalPlayers += atomic.LoadInt32(&shard.playerCount)
		totalLoad += shard.Load()

		if shard.healthy.Load() {
			metrics.HealthyShards++
		}

		return true
	})

	if metrics.TotalShards > 0 {
		metrics.AverageLoad = totalLoad / float64(metrics.TotalShards)
	}

	return metrics
}

// ShardFilter is used to filter shards
type ShardFilter func(*Shard) bool

// FilterShards returns shards matching the filter
func (m *Manager) FilterShards(filter ShardFilter) []*Shard {
	var shards []*Shard
	m.shards.Range(func(key, value interface{}) bool {
		shard := value.(*Shard)
		if filter(shard) {
			shards = append(shards, shard)
		}
		return true
	})
	return shards
}

// GetHealthyShards returns all healthy shards
func (m *Manager) GetHealthyShards() []*Shard {
	return m.FilterShards(func(s *Shard) bool {
		return s.healthy.Load()
	})
}

// GetShardsByLoad returns shards sorted by load (ascending)
func (m *Manager) GetShardsByLoad() []*Shard {
	shards := m.GetAllShards()
	// Simple bubble sort for small N
	for i := 0; i < len(shards); i++ {
		for j := i + 1; j < len(shards); j++ {
			if shards[i].Load() > shards[j].Load() {
				shards[i], shards[j] = shards[j], shards[i]
			}
		}
	}
	return shards
}
