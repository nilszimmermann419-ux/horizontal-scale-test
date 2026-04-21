package proto

import (
	"fmt"

	"google.golang.org/protobuf/runtime/protoimpl"
)

type BlockChangeEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	X        int32  `protobuf:"varint,1,opt,name=x,proto3" json:"x,omitempty"`
	Y        int32  `protobuf:"varint,2,opt,name=y,proto3" json:"y,omitempty"`
	Z        int32  `protobuf:"varint,3,opt,name=z,proto3" json:"z,omitempty"`
	BlockId  string `protobuf:"bytes,4,opt,name=block_id,json=blockId,proto3" json:"block_id,omitempty"`
	PlayerId string `protobuf:"bytes,5,opt,name=player_id,json=playerId,proto3" json:"player_id,omitempty"`
}

func (x *BlockChangeEvent) Reset()         { *x = BlockChangeEvent{} }
func (x *BlockChangeEvent) String() string {
	return fmt.Sprintf("BlockChangeEvent{X:%d, Y:%d, Z:%d, BlockId:%s, PlayerId:%s}", x.X, x.Y, x.Z, x.BlockId, x.PlayerId)
}
func (*BlockChangeEvent) ProtoMessage() {}

func (x *BlockChangeEvent) GetX() int32       { return x.X }
func (x *BlockChangeEvent) GetY() int32       { return x.Y }
func (x *BlockChangeEvent) GetZ() int32       { return x.Z }
func (x *BlockChangeEvent) GetBlockId() string { return x.BlockId }
func (x *BlockChangeEvent) GetPlayerId() string { return x.PlayerId }

type EntitySpawnEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid string `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
	Type string `protobuf:"bytes,2,opt,name=type,proto3" json:"type,omitempty"`
	X    float64 `protobuf:"fixed64,3,opt,name=x,proto3" json:"x,omitempty"`
	Y    float64 `protobuf:"fixed64,4,opt,name=y,proto3" json:"y,omitempty"`
	Z    float64 `protobuf:"fixed64,5,opt,name=z,proto3" json:"z,omitempty"`
}

func (x *EntitySpawnEvent) Reset()         { *x = EntitySpawnEvent{} }
func (x *EntitySpawnEvent) String() string {
	return fmt.Sprintf("EntitySpawnEvent{Uuid:%s, Type:%s, X:%.3f, Y:%.3f, Z:%.3f}", x.Uuid, x.Type, x.X, x.Y, x.Z)
}
func (*EntitySpawnEvent) ProtoMessage() {}

func (x *EntitySpawnEvent) GetUuid() string  { return x.Uuid }
func (x *EntitySpawnEvent) GetType() string  { return x.Type }
func (x *EntitySpawnEvent) GetX() float64    { return x.X }
func (x *EntitySpawnEvent) GetY() float64    { return x.Y }
func (x *EntitySpawnEvent) GetZ() float64    { return x.Z }

type EntityMoveEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid string  `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
	X    float64 `protobuf:"fixed64,2,opt,name=x,proto3" json:"x,omitempty"`
	Y    float64 `protobuf:"fixed64,3,opt,name=y,proto3" json:"y,omitempty"`
	Z    float64 `protobuf:"fixed64,4,opt,name=z,proto3" json:"z,omitempty"`
	Vx   float64 `protobuf:"fixed64,5,opt,name=vx,proto3" json:"vx,omitempty"`
	Vy   float64 `protobuf:"fixed64,6,opt,name=vy,proto3" json:"vy,omitempty"`
	Vz   float64 `protobuf:"fixed64,7,opt,name=vz,proto3" json:"vz,omitempty"`
}

func (x *EntityMoveEvent) Reset()         { *x = EntityMoveEvent{} }
func (x *EntityMoveEvent) String() string {
	return fmt.Sprintf("EntityMoveEvent{Uuid:%s, X:%.3f, Y:%.3f, Z:%.3f, Vx:%.3f, Vy:%.3f, Vz:%.3f}", x.Uuid, x.X, x.Y, x.Z, x.Vx, x.Vy, x.Vz)
}
func (*EntityMoveEvent) ProtoMessage() {}

func (x *EntityMoveEvent) GetUuid() string  { return x.Uuid }
func (x *EntityMoveEvent) GetX() float64    { return x.X }
func (x *EntityMoveEvent) GetY() float64    { return x.Y }
func (x *EntityMoveEvent) GetZ() float64    { return x.Z }
func (x *EntityMoveEvent) GetVx() float64   { return x.Vx }
func (x *EntityMoveEvent) GetVy() float64   { return x.Vy }
func (x *EntityMoveEvent) GetVz() float64   { return x.Vz }

type PlayerJoinEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid    string `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
	Username string `protobuf:"bytes,2,opt,name=username,proto3" json:"username,omitempty"`
	ShardId  string `protobuf:"bytes,3,opt,name=shard_id,json=shardId,proto3" json:"shard_id,omitempty"`
}

func (x *PlayerJoinEvent) Reset()         { *x = PlayerJoinEvent{} }
func (x *PlayerJoinEvent) String() string {
	return fmt.Sprintf("PlayerJoinEvent{Uuid:%s, Username:%s, ShardId:%s}", x.Uuid, x.Username, x.ShardId)
}
func (*PlayerJoinEvent) ProtoMessage() {}

func (x *PlayerJoinEvent) GetUuid() string     { return x.Uuid }
func (x *PlayerJoinEvent) GetUsername() string { return x.Username }
func (x *PlayerJoinEvent) GetShardId() string  { return x.ShardId }

type PlayerLeaveEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid    string `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
	ShardId string `protobuf:"bytes,2,opt,name=shard_id,json=shardId,proto3" json:"shard_id,omitempty"`
}

func (x *PlayerLeaveEvent) Reset()         { *x = PlayerLeaveEvent{} }
func (x *PlayerLeaveEvent) String() string {
	return fmt.Sprintf("PlayerLeaveEvent{Uuid:%s, ShardId:%s}", x.Uuid, x.ShardId)
}
func (*PlayerLeaveEvent) ProtoMessage() {}

func (x *PlayerLeaveEvent) GetUuid() string    { return x.Uuid }
func (x *PlayerLeaveEvent) GetShardId() string { return x.ShardId }

type PlayerMoveEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid   string  `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
	X      float64 `protobuf:"fixed64,2,opt,name=x,proto3" json:"x,omitempty"`
	Y      float64 `protobuf:"fixed64,3,opt,name=y,proto3" json:"y,omitempty"`
	Z      float64 `protobuf:"fixed64,4,opt,name=z,proto3" json:"z,omitempty"`
	Yaw    float32 `protobuf:"fixed32,5,opt,name=yaw,proto3" json:"yaw,omitempty"`
	Pitch  float32 `protobuf:"fixed32,6,opt,name=pitch,proto3" json:"pitch,omitempty"`
}

func (x *PlayerMoveEvent) Reset()         { *x = PlayerMoveEvent{} }
func (x *PlayerMoveEvent) String() string {
	return fmt.Sprintf("PlayerMoveEvent{Uuid:%s, X:%.3f, Y:%.3f, Z:%.3f, Yaw:%.3f, Pitch:%.3f}", x.Uuid, x.X, x.Y, x.Z, x.Yaw, x.Pitch)
}
func (*PlayerMoveEvent) ProtoMessage() {}

func (x *PlayerMoveEvent) GetUuid() string    { return x.Uuid }
func (x *PlayerMoveEvent) GetX() float64      { return x.X }
func (x *PlayerMoveEvent) GetY() float64      { return x.Y }
func (x *PlayerMoveEvent) GetZ() float64      { return x.Z }
func (x *PlayerMoveEvent) GetYaw() float32    { return x.Yaw }
func (x *PlayerMoveEvent) GetPitch() float32  { return x.Pitch }

type WorldEvent struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Timestamp int64 `protobuf:"varint,1,opt,name=timestamp,proto3" json:"timestamp,omitempty"`
	Sequence  int64 `protobuf:"varint,2,opt,name=sequence,proto3" json:"sequence,omitempty"`
	ShardId   string `protobuf:"bytes,3,opt,name=shard_id,json=shardId,proto3" json:"shard_id,omitempty"`

	// Types that are assignable to Payload:
	//	*WorldEvent_BlockChange
	//	*WorldEvent_EntitySpawn
	//	*WorldEvent_EntityMove
	//	*WorldEvent_PlayerJoin
	//	*WorldEvent_PlayerLeave
	//	*WorldEvent_PlayerMove
	Payload isWorldEvent_Payload `protobuf_oneof:"payload"`
}

type isWorldEvent_Payload interface {
	isWorldEvent_Payload()
}

type WorldEvent_BlockChange struct {
	BlockChange *BlockChangeEvent `protobuf:"bytes,10,opt,name=block_change,json=blockChange,proto3,oneof" json:"block_change,omitempty"`
}

type WorldEvent_EntitySpawn struct {
	EntitySpawn *EntitySpawnEvent `protobuf:"bytes,11,opt,name=entity_spawn,json=entitySpawn,proto3,oneof" json:"entity_spawn,omitempty"`
}

type WorldEvent_EntityMove struct {
	EntityMove *EntityMoveEvent `protobuf:"bytes,12,opt,name=entity_move,json=entityMove,proto3,oneof" json:"entity_move,omitempty"`
}

type WorldEvent_PlayerJoin struct {
	PlayerJoin *PlayerJoinEvent `protobuf:"bytes,13,opt,name=player_join,json=playerJoin,proto3,oneof" json:"player_join,omitempty"`
}

type WorldEvent_PlayerLeave struct {
	PlayerLeave *PlayerLeaveEvent `protobuf:"bytes,14,opt,name=player_leave,json=playerLeave,proto3,oneof" json:"player_leave,omitempty"`
}

type WorldEvent_PlayerMove struct {
	PlayerMove *PlayerMoveEvent `protobuf:"bytes,15,opt,name=player_move,json=playerMove,proto3,oneof" json:"player_move,omitempty"`
}

func (*WorldEvent_BlockChange) isWorldEvent_Payload() {}
func (*WorldEvent_EntitySpawn) isWorldEvent_Payload() {}
func (*WorldEvent_EntityMove) isWorldEvent_Payload()  {}
func (*WorldEvent_PlayerJoin) isWorldEvent_Payload()  {}
func (*WorldEvent_PlayerLeave) isWorldEvent_Payload() {}
func (*WorldEvent_PlayerMove) isWorldEvent_Payload()  {}

func (x *WorldEvent) Reset()         { *x = WorldEvent{} }
func (x *WorldEvent) String() string {
	return fmt.Sprintf("WorldEvent{Timestamp:%d, Sequence:%d, ShardId:%s}", x.Timestamp, x.Sequence, x.ShardId)
}
func (*WorldEvent) ProtoMessage() {}

func (x *WorldEvent) GetTimestamp() int64  { return x.Timestamp }
func (x *WorldEvent) GetSequence() int64   { return x.Sequence }
func (x *WorldEvent) GetShardId() string   { return x.ShardId }
func (x *WorldEvent) GetPayload() isWorldEvent_Payload { return x.Payload }

func (x *WorldEvent) GetBlockChange() *BlockChangeEvent {
	if x, ok := x.GetPayload().(*WorldEvent_BlockChange); ok {
		return x.BlockChange
	}
	return nil
}

func (x *WorldEvent) GetEntitySpawn() *EntitySpawnEvent {
	if x, ok := x.GetPayload().(*WorldEvent_EntitySpawn); ok {
		return x.EntitySpawn
	}
	return nil
}

func (x *WorldEvent) GetEntityMove() *EntityMoveEvent {
	if x, ok := x.GetPayload().(*WorldEvent_EntityMove); ok {
		return x.EntityMove
	}
	return nil
}

func (x *WorldEvent) GetPlayerJoin() *PlayerJoinEvent {
	if x, ok := x.GetPayload().(*WorldEvent_PlayerJoin); ok {
		return x.PlayerJoin
	}
	return nil
}

func (x *WorldEvent) GetPlayerLeave() *PlayerLeaveEvent {
	if x, ok := x.GetPayload().(*WorldEvent_PlayerLeave); ok {
		return x.PlayerLeave
	}
	return nil
}

func (x *WorldEvent) GetPlayerMove() *PlayerMoveEvent {
	if x, ok := x.GetPayload().(*WorldEvent_PlayerMove); ok {
		return x.PlayerMove
	}
	return nil
}
