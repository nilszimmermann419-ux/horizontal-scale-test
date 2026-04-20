package protocol

type PacketType uint8

const (
	// Handshake
	PacketHandshake    PacketType = 0x00
	PacketHandshakeAck PacketType = 0x01

	// Player
	PacketPlayerJoin   PacketType = 0x10
	PacketPlayerLeave  PacketType = 0x11
	PacketPlayerMove   PacketType = 0x12
	PacketPlayerAction PacketType = 0x13

	// Chunk
	PacketChunkRequest PacketType = 0x20
	PacketChunkData    PacketType = 0x21
	PacketChunkUpdate  PacketType = 0x22

	// Entity
	PacketEntitySpawn   PacketType = 0x30
	PacketEntityDespawn PacketType = 0x31
	PacketEntityUpdate  PacketType = 0x32

	// Block
	PacketBlockChange PacketType = 0x40

	// Internal
	PacketHeartbeat     PacketType = 0xF0
	PacketShardRegister PacketType = 0xF1
	PacketShardStatus   PacketType = 0xF2
)
