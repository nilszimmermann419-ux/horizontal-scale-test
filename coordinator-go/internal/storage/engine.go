package storage

import (
	"container/list"
	"context"
	"fmt"
	"sync"
	"time"
)

const (
	MaxChunks   = 100000
	MaxPlayers  = 10000
	MaxEntities = 50000
	MaxBlocks   = 1000000
)

// Engine is the interface for the custom storage backend
type Engine interface {
	// Chunk operations
	GetChunk(ctx context.Context, world, dimension string, x, z int32) (*ChunkData, error)
	PutChunk(ctx context.Context, world, dimension string, x, z int32, data *ChunkData) error
	DeleteChunk(ctx context.Context, world, dimension string, x, z int32) error

	// Player data
	GetPlayerData(ctx context.Context, uuid string) (*PlayerData, error)
	PutPlayerData(ctx context.Context, uuid string, data *PlayerData) error

	// Entity data
	GetEntities(ctx context.Context, world, dimension string, minX, minZ, maxX, maxZ int32) ([]*EntityData, error)
	PutEntity(ctx context.Context, world, dimension string, data *EntityData) error
	DeleteEntity(ctx context.Context, world, dimension string, uuid string) error

	// Block operations
	GetBlock(ctx context.Context, world, dimension string, x, y, z int32) (uint16, error)
	PutBlock(ctx context.Context, world, dimension string, x, y, z int32, blockID uint16) error

	// Close
	Close() error
}

// ChunkData represents serialized chunk data
type ChunkData struct {
	X, Z          int32
	Sections      []SectionData
	Biomes        []byte
	BlockEntities map[string]BlockEntityData
	LastModified  int64
}

type SectionData struct {
	Y           int8
	BlockStates []byte // Palette + packed data
	SkyLight    []byte
	BlockLight  []byte
}

type BlockEntityData struct {
	X, Y, Z int32
	Type    string
	NBT     []byte
}

type PlayerData struct {
	UUID       string
	Username   string
	Position   Vec3D
	Inventory  []byte
	Health     float32
	Food       int32
	Experience int32
	Dimension  string
	LastLogin  int64
	Data       map[string]interface{}
}

type EntityData struct {
	UUID     string
	Type     string
	Position Vec3D
	Velocity Vec3D
	Health   float32
	Metadata []byte
}

type Vec3D struct {
	X, Y, Z float64
}

// lruEntry wraps a value with its access time for LRU eviction
type lruEntry struct {
	key       string
	value     interface{}
	accessed  time.Time
}

// NewEngine creates a new storage engine
func NewEngine(path string) (Engine, error) {
	return newMemoryEngine(), nil
}

type memoryEngine struct {
	mu       sync.RWMutex
	chunks   map[string]*ChunkData
	players  map[string]*PlayerData
	entities map[string]*EntityData
	blocks   map[string]uint16

	// LRU tracking
	chunkLRU   *list.List
	playerLRU  *list.List
	entityLRU  *list.List
	blockLRU   *list.List

	chunkMap   map[string]*list.Element
	playerMap  map[string]*list.Element
	entityMap  map[string]*list.Element
	blockMap   map[string]*list.Element
}

func newMemoryEngine() *memoryEngine {
	return &memoryEngine{
		chunks:    make(map[string]*ChunkData),
		players:   make(map[string]*PlayerData),
		entities:  make(map[string]*EntityData),
		blocks:    make(map[string]uint16),
		chunkLRU:  list.New(),
		playerLRU: list.New(),
		entityLRU: list.New(),
		blockLRU:  list.New(),
		chunkMap:  make(map[string]*list.Element),
		playerMap: make(map[string]*list.Element),
		entityMap: make(map[string]*list.Element),
		blockMap:  make(map[string]*list.Element),
	}
}

func chunkKey(world, dimension string, x, z int32) string {
	return fmt.Sprintf("%s:%s:%d:%d", world, dimension, x, z)
}

func blockKey(world, dimension string, x, y, z int32) string {
	return fmt.Sprintf("%s:%s:%d:%d:%d", world, dimension, x, y, z)
}

// evictOldest removes the oldest entry from an LRU list when over capacity
func (e *memoryEngine) evictOldest(lru *list.List, deleteFunc func(string), maxSize int) {
	if lru.Len() <= maxSize {
		return
	}
	for lru.Len() > maxSize {
		elem := lru.Back()
		if elem == nil {
			break
		}
		lru.Remove(elem)
		entry := elem.Value.(*lruEntry)
		deleteFunc(entry.key)
	}
}

// touch moves an entry to the front of the LRU list
func (e *memoryEngine) touch(lru *list.List, elemMap map[string]*list.Element, key string, elem *list.Element) {
	if elem != nil {
		lru.MoveToFront(elem)
		entry := elem.Value.(*lruEntry)
		entry.accessed = time.Now()
	}
}

func (e *memoryEngine) GetChunk(ctx context.Context, world, dimension string, x, z int32) (*ChunkData, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	key := chunkKey(world, dimension, x, z)
	chunk, ok := e.chunks[key]
	if !ok {
		return nil, fmt.Errorf("chunk not found: %s", key)
	}

	if elem, ok := e.chunkMap[key]; ok {
		e.touch(e.chunkLRU, e.chunkMap, key, elem)
	}

	return chunk, nil
}

func (e *memoryEngine) PutChunk(ctx context.Context, world, dimension string, x, z int32, data *ChunkData) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	key := chunkKey(world, dimension, x, z)
	
	// Update access time
	data.LastModified = time.Now().Unix()

	// Remove old entry if exists
	if elem, ok := e.chunkMap[key]; ok {
		e.chunkLRU.Remove(elem)
		delete(e.chunkMap, key)
	}

	// Add new entry
	e.chunks[key] = data
	entry := &lruEntry{key: key, value: data, accessed: time.Now()}
	e.chunkMap[key] = e.chunkLRU.PushFront(entry)

	// Evict if over capacity
	e.evictOldest(e.chunkLRU, func(key string) {
		delete(e.chunks, key)
	}, MaxChunks)

	return nil
}

func (e *memoryEngine) DeleteChunk(ctx context.Context, world, dimension string, x, z int32) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	key := chunkKey(world, dimension, x, z)
	if elem, ok := e.chunkMap[key]; ok {
		e.chunkLRU.Remove(elem)
		delete(e.chunkMap, key)
	}
	delete(e.chunks, key)
	return nil
}

func (e *memoryEngine) GetPlayerData(ctx context.Context, uuid string) (*PlayerData, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	player, ok := e.players[uuid]
	if !ok {
		return nil, fmt.Errorf("player not found: %s", uuid)
	}

	if elem, ok := e.playerMap[uuid]; ok {
		e.touch(e.playerLRU, e.playerMap, uuid, elem)
	}

	return player, nil
}

func (e *memoryEngine) PutPlayerData(ctx context.Context, uuid string, data *PlayerData) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	// Remove old entry if exists
	if elem, ok := e.playerMap[uuid]; ok {
		e.playerLRU.Remove(elem)
		delete(e.playerMap, uuid)
	}

	e.players[uuid] = data
	entry := &lruEntry{key: uuid, value: data, accessed: time.Now()}
	e.playerMap[uuid] = e.playerLRU.PushFront(entry)

	// Evict if over capacity
	if e.playerLRU.Len() > MaxPlayers {
		e.evictOldest(e.playerLRU, func(key string) {
			delete(e.players, key)
		}, MaxPlayers)
	}

	return nil
}

func (e *memoryEngine) GetEntities(ctx context.Context, world, dimension string, minX, minZ, maxX, maxZ int32) ([]*EntityData, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	var result []*EntityData
	for _, entity := range e.entities {
		if entity.Position.X >= float64(minX) && entity.Position.X <= float64(maxX) &&
			entity.Position.Z >= float64(minZ) && entity.Position.Z <= float64(maxZ) {
			result = append(result, entity)
		}
	}
	return result, nil
}

func (e *memoryEngine) PutEntity(ctx context.Context, world, dimension string, data *EntityData) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	// Remove old entry if exists
	if elem, ok := e.entityMap[data.UUID]; ok {
		e.entityLRU.Remove(elem)
		delete(e.entityMap, data.UUID)
	}

	e.entities[data.UUID] = data
	entry := &lruEntry{key: data.UUID, value: data, accessed: time.Now()}
	e.entityMap[data.UUID] = e.entityLRU.PushFront(entry)

	// Evict if over capacity
	if e.entityLRU.Len() > MaxEntities {
		e.evictOldest(e.entityLRU, func(key string) {
			delete(e.entities, key)
		}, MaxEntities)
	}

	return nil
}

func (e *memoryEngine) DeleteEntity(ctx context.Context, world, dimension string, uuid string) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if elem, ok := e.entityMap[uuid]; ok {
		e.entityLRU.Remove(elem)
		delete(e.entityMap, uuid)
	}
	delete(e.entities, uuid)
	return nil
}

func (e *memoryEngine) GetBlock(ctx context.Context, world, dimension string, x, y, z int32) (uint16, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	key := blockKey(world, dimension, x, y, z)
	block, ok := e.blocks[key]
	if !ok {
		return 0, nil // Air
	}

	if elem, ok := e.blockMap[key]; ok {
		e.touch(e.blockLRU, e.blockMap, key, elem)
	}

	return block, nil
}

func (e *memoryEngine) PutBlock(ctx context.Context, world, dimension string, x, y, z int32, blockID uint16) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	key := blockKey(world, dimension, x, y, z)

	// Remove old entry if exists
	if elem, ok := e.blockMap[key]; ok {
		e.blockLRU.Remove(elem)
		delete(e.blockMap, key)
	}

	e.blocks[key] = blockID
	entry := &lruEntry{key: key, value: blockID, accessed: time.Now()}
	e.blockMap[key] = e.blockLRU.PushFront(entry)

	// Evict if over capacity
	if e.blockLRU.Len() > MaxBlocks {
		e.evictOldest(e.blockLRU, func(key string) {
			delete(e.blocks, key)
		}, MaxBlocks)
	}

	return nil
}

func (e *memoryEngine) Close() error {
	return nil
}
