package storage

import (
	"container/list"
	"context"
	"fmt"
	"sync"
)

// TODO: Consolidate LRU implementation with internal/storage/engine.go

type MemoryCache struct {
	maxSize int
	chunks  map[string]*list.Element
	lru     *list.List
	mu      sync.RWMutex
}

type cacheEntry struct {
	key   string
	value *ChunkData
}

func NewMemoryCache(maxSize int) *MemoryCache {
	return &MemoryCache{
		maxSize: maxSize,
		chunks:  make(map[string]*list.Element),
		lru:     list.New(),
	}
}

func (c *MemoryCache) GetChunk(ctx context.Context, world, dimension string, x, z int32) (*ChunkData, error) {
	key := fmt.Sprintf("%s:%s:%d:%d", world, dimension, x, z)

	c.mu.RLock()
	defer c.mu.RUnlock()

	if elem, ok := c.chunks[key]; ok {
		c.lru.MoveToFront(elem)
		return elem.Value.(*cacheEntry).value, nil
	}

	return nil, fmt.Errorf("chunk not in cache")
}

func (c *MemoryCache) PutChunk(ctx context.Context, world, dimension string, x, z int32, data *ChunkData) error {
	key := fmt.Sprintf("%s:%s:%d:%d", world, dimension, x, z)

	c.mu.Lock()
	defer c.mu.Unlock()

	if elem, ok := c.chunks[key]; ok {
		c.lru.MoveToFront(elem)
		elem.Value.(*cacheEntry).value = data
		return nil
	}

	// Evict if necessary
	for len(c.chunks) >= c.maxSize {
		back := c.lru.Back()
		if back == nil {
			break
		}
		entry := back.Value.(*cacheEntry)
		delete(c.chunks, entry.key)
		c.lru.Remove(back)
	}

	elem := c.lru.PushFront(&cacheEntry{key: key, value: data})
	c.chunks[key] = elem

	return nil
}
