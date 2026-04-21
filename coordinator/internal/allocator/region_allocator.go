package allocator

import (
	"errors"
	"fmt"
	"hash/fnv"
	"strconv"

	"github.com/shardedmc/v2/coordinator/internal/registry"
)

var ErrNoShardsAvailable = errors.New("no shards available for allocation")

// consistentHash computes a FNV-1a hash of the given string
func consistentHash(s string) uint32 {
	h := fnv.New32a()
	h.Write([]byte(s))
	return h.Sum32()
}

// GetRegionOwner determines which shard owns a given region using consistent hashing
func GetRegionOwner(regionCoord [2]int, shards []*registry.ShardInfo) (string, error) {
	if len(shards) == 0 {
		return "", ErrNoShardsAvailable
	}
	if len(shards) == 1 {
		return shards[0].ID, nil
	}
	key := strconv.Itoa(regionCoord[0]) + ":" + strconv.Itoa(regionCoord[1])
	hash := consistentHash(key)
	idx := hash % uint32(len(shards))
	return shards[idx].ID, nil
}

// GetChunkOwner determines which shard owns a given chunk using consistent hashing
func GetChunkOwner(chunkCoord [2]int, shards []*registry.ShardInfo) (string, error) {
	if len(shards) == 0 {
		return "", ErrNoShardsAvailable
	}
	if len(shards) == 1 {
		return shards[0].ID, nil
	}
	key := strconv.Itoa(chunkCoord[0]) + ":" + strconv.Itoa(chunkCoord[1])
	hash := consistentHash(key)
	idx := hash % uint32(len(shards))
	return shards[idx].ID, nil
}

// GetShardLoad returns a map of shard ID to its current load ratio
func GetShardLoad(shards []*registry.ShardInfo) map[string]float64 {
	load := make(map[string]float64)
	for _, shard := range shards {
		if shard.Capacity > 0 {
			load[shard.ID] = float64(shard.PlayerCount) / float64(shard.Capacity)
		} else {
			load[shard.ID] = 1.0
		}
	}
	return load
}

// CheckDistributionEvenness returns true if no shard has more than 1.5x the average load
func CheckDistributionEvenness(shards []*registry.ShardInfo) (bool, string) {
	if len(shards) == 0 {
		return false, "no shards available"
	}
	if len(shards) == 1 {
		return true, ""
	}

	totalLoad := 0
	totalCapacity := 0
	for _, shard := range shards {
		totalLoad += shard.PlayerCount
		totalCapacity += shard.Capacity
	}

	if totalCapacity == 0 {
		return false, "total capacity is zero"
	}

	avgLoadRatio := float64(totalLoad) / float64(totalCapacity)
	for _, shard := range shards {
		var shardLoadRatio float64
		if shard.Capacity > 0 {
			shardLoadRatio = float64(shard.PlayerCount) / float64(shard.Capacity)
		} else {
			shardLoadRatio = 1.0
		}
		if shardLoadRatio > avgLoadRatio*1.5 {
			return false, fmt.Sprintf("shard %s is overloaded (%.2f vs avg %.2f)", shard.ID, shardLoadRatio, avgLoadRatio)
		}
	}

	return true, ""
}
