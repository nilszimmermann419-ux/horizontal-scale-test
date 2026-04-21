package util

import "math"

// Vec3d represents a 3D vector with float64 coordinates
type Vec3d struct {
	X, Y, Z float64
}

// Add returns the sum of two vectors
func (v Vec3d) Add(other Vec3d) Vec3d {
	return Vec3d{
		X: v.X + other.X,
		Y: v.Y + other.Y,
		Z: v.Z + other.Z,
	}
}

// Sub returns the difference of two vectors
func (v Vec3d) Sub(other Vec3d) Vec3d {
	return Vec3d{
		X: v.X - other.X,
		Y: v.Y - other.Y,
		Z: v.Z - other.Z,
	}
}

// Distance returns the Euclidean distance between two vectors
func (v Vec3d) Distance(other Vec3d) float64 {
	dx := v.X - other.X
	dy := v.Y - other.Y
	dz := v.Z - other.Z
	return math.Sqrt(dx*dx + dy*dy + dz*dz)
}

// Length returns the magnitude of the vector
func (v Vec3d) Length() float64 {
	return math.Sqrt(v.X*v.X + v.Y*v.Y + v.Z*v.Z)
}

// Scale returns the vector multiplied by a scalar
func (v Vec3d) Scale(s float64) Vec3d {
	return Vec3d{
		X: v.X * s,
		Y: v.Y * s,
		Z: v.Z * s,
	}
}

// Vec3i represents a 3D vector with integer coordinates
type Vec3i struct {
	X, Y, Z int
}

// Add returns the sum of two integer vectors
func (v Vec3i) Add(other Vec3i) Vec3i {
	return Vec3i{
		X: v.X + other.X,
		Y: v.Y + other.Y,
		Z: v.Z + other.Z,
	}
}

// Sub returns the difference of two integer vectors
func (v Vec3i) Sub(other Vec3i) Vec3i {
	return Vec3i{
		X: v.X - other.X,
		Y: v.Y - other.Y,
		Z: v.Z - other.Z,
	}
}

// ChunkPos represents a chunk position in the world
type ChunkPos struct {
	X, Z int
}

// ToChunkPos converts a Vec3i block position to a ChunkPos
// A chunk is 16x16 blocks, so we divide by 16 (using floor division for negative coordinates)
func ToChunkPos(v Vec3i) ChunkPos {
	return ChunkPos{
		X: int(math.Floor(float64(v.X) / 16.0)),
		Z: int(math.Floor(float64(v.Z) / 16.0)),
	}
}

// BlockX returns the X coordinate of a block within the chunk (0-15)
func (c ChunkPos) BlockX(blockX int) int {
	return ((blockX % 16) + 16) % 16
}

// BlockZ returns the Z coordinate of a block within the chunk (0-15)
func (c ChunkPos) BlockZ(blockZ int) int {
	return ((blockZ % 16) + 16) % 16
}

// RegionPos represents a region position in the world
type RegionPos struct {
	X, Z int
}

// ToRegionPos converts a ChunkPos to a RegionPos
// A region is 32x32 chunks, so we divide by 32 (using floor division for negative coordinates)
func ToRegionPos(c ChunkPos) RegionPos {
	return RegionPos{
		X: int(math.Floor(float64(c.X) / 32.0)),
		Z: int(math.Floor(float64(c.Z) / 32.0)),
	}
}

// FromVec3i converts a Vec3i directly to a RegionPos
func FromVec3i(v Vec3i) RegionPos {
	return ToRegionPos(ToChunkPos(v))
}

// ContainsChunk checks if a given chunk is within this region
func (r RegionPos) ContainsChunk(chunk ChunkPos) bool {
	regionOfChunk := ToRegionPos(chunk)
	return r.X == regionOfChunk.X && r.Z == regionOfChunk.Z
}
