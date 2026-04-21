package proto

import (
	"fmt"

	"google.golang.org/protobuf/runtime/protoimpl"
)

type InventoryItem struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Slot    int32  `protobuf:"varint,1,opt,name=slot,proto3" json:"slot,omitempty"`
	Material string `protobuf:"bytes,2,opt,name=material,proto3" json:"material,omitempty"`
	Amount  int32  `protobuf:"varint,3,opt,name=amount,proto3" json:"amount,omitempty"`
	NbtData []byte `protobuf:"bytes,4,opt,name=nbt_data,json=nbtData,proto3" json:"nbt_data,omitempty"`
}

func (x *InventoryItem) Reset()         { *x = InventoryItem{} }
func (x *InventoryItem) String() string {
	return fmt.Sprintf("InventoryItem{Slot:%d, Material:%s, Amount:%d, NbtData:%d}", x.Slot, x.Material, x.Amount, len(x.NbtData))
}
func (*InventoryItem) ProtoMessage() {}

func (x *InventoryItem) GetSlot() int32      { return x.Slot }
func (x *InventoryItem) GetMaterial() string { return x.Material }
func (x *InventoryItem) GetAmount() int32    { return x.Amount }
func (x *InventoryItem) GetNbtData() []byte  { return x.NbtData }

type PlayerState struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid     string           `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
	Username string           `protobuf:"bytes,2,opt,name=username,proto3" json:"username,omitempty"`
	Position *Vec3d           `protobuf:"bytes,3,opt,name=position,proto3" json:"position,omitempty"`
	Health   float32          `protobuf:"fixed32,4,opt,name=health,proto3" json:"health,omitempty"`
	Food     int32            `protobuf:"varint,5,opt,name=food,proto3" json:"food,omitempty"`
	Saturation float32        `protobuf:"fixed32,6,opt,name=saturation,proto3" json:"saturation,omitempty"`
	Gamemode string           `protobuf:"bytes,7,opt,name=gamemode,proto3" json:"gamemode,omitempty"`
	Inventory []*InventoryItem `protobuf:"bytes,8,rep,name=inventory,proto3" json:"inventory,omitempty"`
}

func (x *PlayerState) Reset()         { *x = PlayerState{} }
func (x *PlayerState) String() string {
	return fmt.Sprintf("PlayerState{Uuid:%s, Username:%s, Position:%s, Health:%.1f, Food:%d, Saturation:%.1f, Gamemode:%s, Inventory:%d}",
		x.Uuid, x.Username, x.Position, x.Health, x.Food, x.Saturation, x.Gamemode, len(x.Inventory))
}
func (*PlayerState) ProtoMessage() {}

func (x *PlayerState) GetUuid() string              { return x.Uuid }
func (x *PlayerState) GetUsername() string          { return x.Username }
func (x *PlayerState) GetPosition() *Vec3d          { return x.Position }
func (x *PlayerState) GetHealth() float32           { return x.Health }
func (x *PlayerState) GetFood() int32               { return x.Food }
func (x *PlayerState) GetSaturation() float32       { return x.Saturation }
func (x *PlayerState) GetGamemode() string          { return x.Gamemode }
func (x *PlayerState) GetInventory() []*InventoryItem { return x.Inventory }

type PlayerTransferState struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	PlayerUuid        string       `protobuf:"bytes,1,opt,name=player_uuid,json=playerUuid,proto3" json:"player_uuid,omitempty"`
	SourceShard       string       `protobuf:"bytes,2,opt,name=source_shard,json=sourceShard,proto3" json:"source_shard,omitempty"`
	TargetShard       string       `protobuf:"bytes,3,opt,name=target_shard,json=targetShard,proto3" json:"target_shard,omitempty"`
	State             *PlayerState `protobuf:"bytes,4,opt,name=state,proto3" json:"state,omitempty"`
	TransferStartTime int64        `protobuf:"varint,5,opt,name=transfer_start_time,json=transferStartTime,proto3" json:"transfer_start_time,omitempty"`
	Acknowledged      bool         `protobuf:"varint,6,opt,name=acknowledged,proto3" json:"acknowledged,omitempty"`
}

func (x *PlayerTransferState) Reset()         { *x = PlayerTransferState{} }
func (x *PlayerTransferState) String() string {
	return fmt.Sprintf("PlayerTransferState{PlayerUuid:%s, SourceShard:%s, TargetShard:%s, State:%s, TransferStartTime:%d, Acknowledged:%t}",
		x.PlayerUuid, x.SourceShard, x.TargetShard, x.State, x.TransferStartTime, x.Acknowledged)
}
func (*PlayerTransferState) ProtoMessage() {}

func (x *PlayerTransferState) GetPlayerUuid() string        { return x.PlayerUuid }
func (x *PlayerTransferState) GetSourceShard() string       { return x.SourceShard }
func (x *PlayerTransferState) GetTargetShard() string       { return x.TargetShard }
func (x *PlayerTransferState) GetState() *PlayerState       { return x.State }
func (x *PlayerTransferState) GetTransferStartTime() int64  { return x.TransferStartTime }
func (x *PlayerTransferState) GetAcknowledged() bool        { return x.Acknowledged }
