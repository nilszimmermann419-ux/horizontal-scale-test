package proto

import (
	"fmt"
	"google.golang.org/protobuf/runtime/protoimpl"
)

type Vec3d struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	X float64 `protobuf:"fixed64,1,opt,name=x,proto3" json:"x,omitempty"`
	Y float64 `protobuf:"fixed64,2,opt,name=y,proto3" json:"y,omitempty"`
	Z float64 `protobuf:"fixed64,3,opt,name=z,proto3" json:"z,omitempty"`
}

func (x *Vec3d) Reset()         { *x = Vec3d{} }
func (x *Vec3d) String() string { return fmt.Sprintf("Vec3d{X:%.3f, Y:%.3f, Z:%.3f}", x.X, x.Y, x.Z) }
func (*Vec3d) ProtoMessage()    {}

func (x *Vec3d) GetX() float64 { return x.X }
func (x *Vec3d) GetY() float64 { return x.Y }
func (x *Vec3d) GetZ() float64 { return x.Z }

type Vec3i struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	X int32 `protobuf:"varint,1,opt,name=x,proto3" json:"x,omitempty"`
	Y int32 `protobuf:"varint,2,opt,name=y,proto3" json:"y,omitempty"`
	Z int32 `protobuf:"varint,3,opt,name=z,proto3" json:"z,omitempty"`
}

func (x *Vec3i) Reset()         { *x = Vec3i{} }
func (x *Vec3i) String() string { return fmt.Sprintf("Vec3i{X:%d, Y:%d, Z:%d}", x.X, x.Y, x.Z) }
func (*Vec3i) ProtoMessage()    {}

func (x *Vec3i) GetX() int32 { return x.X }
func (x *Vec3i) GetY() int32 { return x.Y }
func (x *Vec3i) GetZ() int32 { return x.Z }

type ChunkCoord struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	X int32 `protobuf:"varint,1,opt,name=x,proto3" json:"x,omitempty"`
	Z int32 `protobuf:"varint,2,opt,name=z,proto3" json:"z,omitempty"`
}

func (x *ChunkCoord) Reset()         { *x = ChunkCoord{} }
func (x *ChunkCoord) String() string { return fmt.Sprintf("ChunkCoord{X:%d, Z:%d}", x.X, x.Z) }
func (*ChunkCoord) ProtoMessage()    {}

func (x *ChunkCoord) GetX() int32 { return x.X }
func (x *ChunkCoord) GetZ() int32 { return x.Z }

type RegionCoord struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	X int32 `protobuf:"varint,1,opt,name=x,proto3" json:"x,omitempty"`
	Z int32 `protobuf:"varint,2,opt,name=z,proto3" json:"z,omitempty"`
}

func (x *RegionCoord) Reset()         { *x = RegionCoord{} }
func (x *RegionCoord) String() string { return fmt.Sprintf("RegionCoord{X:%d, Z:%d}", x.X, x.Z) }
func (*RegionCoord) ProtoMessage()    {}

func (x *RegionCoord) GetX() int32 { return x.X }
func (x *RegionCoord) GetZ() int32 { return x.Z }

type PlayerId struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Uuid string `protobuf:"bytes,1,opt,name=uuid,proto3" json:"uuid,omitempty"`
}

func (x *PlayerId) Reset()         { *x = PlayerId{} }
func (x *PlayerId) String() string { return fmt.Sprintf("PlayerId{Uuid:%s}", x.Uuid) }
func (*PlayerId) ProtoMessage()    {}

func (x *PlayerId) GetUuid() string { return x.Uuid }

type ShardId struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Id string `protobuf:"bytes,1,opt,name=id,proto3" json:"id,omitempty"`
}

func (x *ShardId) Reset()         { *x = ShardId{} }
func (x *ShardId) String() string { return fmt.Sprintf("ShardId{Id:%s}", x.Id) }
func (*ShardId) ProtoMessage()    {}

func (x *ShardId) GetId() string { return x.Id }
