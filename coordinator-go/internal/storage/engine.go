package storage

import (
	"context"
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
