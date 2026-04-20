package shard

import (
	"context"
	"log"
	"time"
)

// HealthChecker performs health checks on shards
type HealthChecker struct {
	manager   *Manager
	interval  time.Duration
	timeout   time.Duration
	unhealthy chan *Shard
}

// NewHealthChecker creates a new health checker
func NewHealthChecker(manager *Manager, interval, timeout time.Duration) *HealthChecker {
	return &HealthChecker{
		manager:   manager,
		interval:  interval,
		timeout:   timeout,
		unhealthy: make(chan *Shard, 100),
	}
}

// Start begins health checking in a background goroutine
func (hc *HealthChecker) Start(ctx context.Context) {
	go hc.run(ctx)
}

func (hc *HealthChecker) run(ctx context.Context) {
	ticker := time.NewTicker(hc.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			hc.checkAllShards()
		case shard := <-hc.unhealthy:
			log.Printf("Shard %s reported unhealthy", shard.ID)
		}
	}
}

func (hc *HealthChecker) checkAllShards() {
	hc.manager.shards.Range(func(key, value interface{}) bool {
		shard := value.(*Shard)
		hc.checkShard(shard)
		return true
	})
}

func (hc *HealthChecker) checkShard(shard *Shard) {
	// Check heartbeat timeout
	lastBeat := time.Unix(0, shard.lastHeartbeat.Load())
	if time.Since(lastBeat) > hc.timeout {
		if shard.healthy.Load() {
			shard.healthy.Store(false)
			log.Printf("Shard %s marked unhealthy (heartbeat timeout)", shard.ID)
			select {
			case hc.unhealthy <- shard:
			default:
			}
		}
		return
	}

	// Check if overloaded
	if shard.Load() > 0.95 {
		log.Printf("Shard %s is overloaded (load: %.2f)", shard.ID, shard.Load())
	}
}

// ReportUnhealthy allows external systems to report a shard as unhealthy
func (hc *HealthChecker) ReportUnhealthy(shard *Shard) {
	select {
	case hc.unhealthy <- shard:
	default:
	}
}

// GetUnhealthyChannel returns the channel for unhealthy shard notifications
func (hc *HealthChecker) GetUnhealthyChannel() <-chan *Shard {
	return hc.unhealthy
}

// HeartbeatHandler processes heartbeat packets from shards
type HeartbeatHandler struct {
	manager *Manager
}

// NewHeartbeatHandler creates a new heartbeat handler
func NewHeartbeatHandler(manager *Manager) *HeartbeatHandler {
	return &HeartbeatHandler{
		manager: manager,
	}
}

// ProcessHeartbeat updates a shard's heartbeat and statistics
func (hh *HeartbeatHandler) ProcessHeartbeat(shardID string, playerCount uint32, load float32) error {
	shard, ok := hh.manager.GetShard(shardID)
	if !ok {
		return ErrShardNotFound
	}

	shard.UpdateHeartbeat()

	// Update player count if provided
	if playerCount > 0 {
		// Atomic update would be better, but for now just log discrepancy
		current := shard.PlayerCount()
		if int32(playerCount) != current {
			log.Printf("Shard %s player count mismatch: reported=%d, tracked=%d",
				shardID, playerCount, current)
		}
	}

	return nil
}

// ErrShardNotFound is returned when a shard is not found
var ErrShardNotFound = errShardNotFound{}

type errShardNotFound struct{}

func (errShardNotFound) Error() string {
	return "shard not found"
}
