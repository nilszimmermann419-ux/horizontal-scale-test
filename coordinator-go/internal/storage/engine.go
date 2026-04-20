package storage

import (
	"context"
	"fmt"
	"sync"
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
}

func newMemoryEngine() *memoryEngine {
	return &memoryEngine{
		chunks:   make(map[string]*ChunkData),
		players:  make(map[string]*PlayerData),
		entities: make(map[string]*EntityData),
		blocks:   make(map[string]uint16),
	}
}

func chunkKey(world, dimension string, x, z int32) string {
	return fmt.Sprintf("%s:%s:%d:%d", world, dimension, x, z)
}

func blockKey(world, dimension string, x, y, z int32) string {
	return fmt.Sprintf("%s:%s:%d:%d:%d", world, dimension, x, y, z)
}

func (e *memoryEngine) GetChunk(ctx context.Context, world, dimension string, x, z int32) (*ChunkData, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	key := chunkKey(world, dimension, x, z)
	chunk, ok := e.chunks[key]
	if !ok {
		return nil, fmt.Errorf("chunk not found: %s", key)
	}
	return chunk, nil
}

func (e *memoryEngine) PutChunk(ctx context.Context, world, dimension string, x, z int32, data *ChunkData) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	key := chunkKey(world, dimension, x, z)
	e.chunks[key] = data
	return nil
}

func (e *memoryEngine) DeleteChunk(ctx context.Context, world, dimension string, x, z int32) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	key := chunkKey(world, dimension, x, z)
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
	return player, nil
}

func (e *memoryEngine) PutPlayerData(ctx context.Context, uuid string, data *PlayerData) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	e.players[uuid] = data
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

	e.entities[data.UUID] = data
	return nil
}

func (e *memoryEngine) DeleteEntity(ctx context.Context, world, dimension string, uuid string) error {
	e.mu.Lock()
	defer e.mu.Unlock()

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
	return block, nil
}

func (e *memoryEngine) PutBlock(ctx context.Context, world, dimension string, x, y, z int32, blockID uint16) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	key := blockKey(world, dimension, x, y, z)
	e.blocks[key] = blockID
	return nil
}

func (e *memoryEngine) Close() error {
	return nil
}
