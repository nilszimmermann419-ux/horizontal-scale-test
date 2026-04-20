package protocol

import (
	"fmt"
)

// Vec3D represents a 3D vector
type Vec3D struct {
	X, Y, Z float64
}

func (v Vec3D) String() string {
	return fmt.Sprintf("(%.2f, %.2f, %.2f)", v.X, v.Y, v.Z)
}

// Vec3F represents a 3D vector with float32 components
type Vec3F struct {
	X, Y, Z float32
}

// PlayerPosition represents a player's position and rotation
type PlayerPosition struct {
	X        float64
	Y        float64
	Z        float64
	Yaw      float32
	Pitch    float32
	OnGround bool
}

// ChunkCoord represents chunk coordinates
type ChunkCoord struct {
	X int32
	Z int32
}

func (c ChunkCoord) String() string {
	return fmt.Sprintf("(%d, %d)", c.X, c.Z)
}

// BlockPos represents a block position
type BlockPos struct {
	X int32
	Y int32
	Z int32
}

func (p BlockPos) String() string {
	return fmt.Sprintf("(%d, %d, %d)", p.X, p.Y, p.Z)
}

// PacketHeader is the header for all packets
type PacketHeader struct {
	Length   uint32
	Type     PacketType
	Sequence uint32
}

// HandshakePacket is sent when a shard connects
type HandshakePacket struct {
	ProtocolVersion uint16
	ShardID         string
	Token           string
}

// PlayerJoinPacket is sent when a player joins
type PlayerJoinPacket struct {
	PlayerID uint64
	UUID     string
	Username string
	Position Vec3D
}

// PlayerMovePacket is sent when a player moves
type PlayerMovePacket struct {
	PlayerID uint64
	Position PlayerPosition
}

// ChunkRequestPacket requests chunk data
type ChunkRequestPacket struct {
	World     string
	Dimension string
	X         int32
	Z         int32
}

// ChunkDataPacket contains chunk data
type ChunkDataPacket struct {
	World     string
	Dimension string
	X         int32
	Z         int32
	Data      []byte
}

// EntityUpdatePacket updates entity state
type EntityUpdatePacket struct {
	EntityID uint64
	UUID     string
	Type     string
	Position Vec3D
	Velocity Vec3F
}

// BlockChangePacket updates a block
type BlockChangePacket struct {
	World    string
	Position BlockPos
	BlockID  uint16
}

// HeartbeatPacket is sent periodically
type HeartbeatPacket struct {
	Timestamp   uint64
	PlayerCount uint32
	Load        float32
}

// ShardRegisterPacket registers a shard
type ShardRegisterPacket struct {
	ShardID  string
	Address  string
	Port     uint16
	Capacity uint32
}
