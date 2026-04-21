package allocator

import (
	"hash/fnv"
	"strconv"

	"github.com/shardedmc/v2/coordinator/internal/registry"
)

// consistentHash computes a FNV-1a hash of the given string
func consistentHash(s string) uint32 {
	h := fnv.New32a()
	h.Write([]byte(s))
	return h.Sum32()
}

// GetRegionOwner determines which shard owns a given region using consistent hashing
func GetRegionOwner(regionCoord [2]int, shards []*registry.ShardInfo) string {
	if len(shards) == 0 {
		return ""
	}
	key := strconv.Itoa(regionCoord[0]) + ":" + strconv.Itoa(regionCoord[1])
	hash := consistentHash(key)
	idx := hash % uint32(len(shards))
	return shards[idx].ID
}

// GetChunkOwner determines which shard owns a given chunk using consistent hashing
func GetChunkOwner(chunkCoord [2]int, shards []*registry.ShardInfo) string {
	if len(shards) == 0 {
		return ""
	}
	key := strconv.Itoa(chunkCoord[0]) + ":" + strconv.Itoa(chunkCoord[1])
	hash := consistentHash(key)
	idx := hash % uint32(len(shards))
	return shards[idx].ID
}
