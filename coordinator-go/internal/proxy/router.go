package proxy

import (
	"github.com/shardedmc/coordinator/internal/shard"
)

// ShardRouter routes players to the appropriate shard
type ShardRouter struct {
	shardMgr *shard.Manager
}

// NewShardRouter creates a new shard router
func NewShardRouter(shardMgr *shard.Manager) *ShardRouter {
	return &ShardRouter{
		shardMgr: shardMgr,
	}
}

// RoutePlayer returns the best shard for a new player
func (r *ShardRouter) RoutePlayer() *shard.Shard {
	return r.shardMgr.GetLeastLoadedShard()
}

// RoutePlayerByUUID returns the shard for a specific player UUID
// This is used for player reconnection to maintain session affinity
func (r *ShardRouter) RoutePlayerByUUID(uuid string) *shard.Shard {
	// For now, just return least loaded shard
	// In production, this would look up the player's home shard
	return r.shardMgr.GetLeastLoadedShard()
}

// RoutePlayerByPosition returns the shard responsible for a player's position
func (r *ShardRouter) RoutePlayerByPosition(world string, x, z float64) *shard.Shard {
	// For now, return least loaded shard
	// In production, this would use chunk ownership to determine shard
	return r.shardMgr.GetLeastLoadedShard()
}

// GetShardByID returns a shard by its ID
func (r *ShardRouter) GetShardByID(id string) (*shard.Shard, bool) {
	return r.shardMgr.GetShard(id)
}

// ShardCount returns the number of registered shards
func (r *ShardRouter) ShardCount() int {
	return r.shardMgr.ShardCount()
}

// HealthyShardCount returns the number of healthy shards
func (r *ShardRouter) HealthyShardCount() int {
	return r.shardMgr.HealthyShardCount()
}
