package proto

import (
	"fmt"

	"google.golang.org/protobuf/runtime/protoimpl"
)

type SectionData struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Y            int32  `protobuf:"varint,1,opt,name=y,proto3" json:"y,omitempty"`
	BlockPalette []byte `protobuf:"bytes,2,opt,name=block_palette,json=blockPalette,proto3" json:"block_palette,omitempty"`
	BlockData    []byte `protobuf:"bytes,3,opt,name=block_data,json=blockData,proto3" json:"block_data,omitempty"`
	SkyLight     []byte `protobuf:"bytes,4,opt,name=sky_light,json=skyLight,proto3" json:"sky_light,omitempty"`
	BlockLight   []byte `protobuf:"bytes,5,opt,name=block_light,json=blockLight,proto3" json:"block_light,omitempty"`
}

func (x *SectionData) Reset()         { *x = SectionData{} }
func (x *SectionData) String() string {
	return fmt.Sprintf("SectionData{Y:%d, BlockPalette:%d, BlockData:%d, SkyLight:%d, BlockLight:%d}",
		x.Y, len(x.BlockPalette), len(x.BlockData), len(x.SkyLight), len(x.BlockLight))
}
func (*SectionData) ProtoMessage() {}

func (x *SectionData) GetY() int32             { return x.Y }
func (x *SectionData) GetBlockPalette() []byte { return x.BlockPalette }
func (x *SectionData) GetBlockData() []byte    { return x.BlockData }
func (x *SectionData) GetSkyLight() []byte     { return x.SkyLight }
func (x *SectionData) GetBlockLight() []byte   { return x.BlockLight }

type ChunkData struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	X           int32          `protobuf:"varint,1,opt,name=x,proto3" json:"x,omitempty"`
	Z           int32          `protobuf:"varint,2,opt,name=z,proto3" json:"z,omitempty"`
	Sections    []*SectionData `protobuf:"bytes,3,rep,name=sections,proto3" json:"sections,omitempty"`
	LastModified int64         `protobuf:"varint,4,opt,name=last_modified,json=lastModified,proto3" json:"last_modified,omitempty"`
	Version     int32          `protobuf:"varint,5,opt,name=version,proto3" json:"version,omitempty"`
}

func (x *ChunkData) Reset()         { *x = ChunkData{} }
func (x *ChunkData) String() string {
	return fmt.Sprintf("ChunkData{X:%d, Z:%d, Sections:%d, LastModified:%d, Version:%d}",
		x.X, x.Z, len(x.Sections), x.LastModified, x.Version)
}
func (*ChunkData) ProtoMessage() {}

func (x *ChunkData) GetX() int32              { return x.X }
func (x *ChunkData) GetZ() int32              { return x.Z }
func (x *ChunkData) GetSections() []*SectionData { return x.Sections }
func (x *ChunkData) GetLastModified() int64   { return x.LastModified }
func (x *ChunkData) GetVersion() int32        { return x.Version }

type ChunkRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Coord           *ChunkCoord `protobuf:"bytes,1,opt,name=coord,proto3" json:"coord,omitempty"`
	RequestingShard string      `protobuf:"bytes,2,opt,name=requesting_shard,json=requestingShard,proto3" json:"requesting_shard,omitempty"`
}

func (x *ChunkRequest) Reset()         { *x = ChunkRequest{} }
func (x *ChunkRequest) String() string {
	return fmt.Sprintf("ChunkRequest{Coord:%s, RequestingShard:%s}", x.Coord, x.RequestingShard)
}
func (*ChunkRequest) ProtoMessage() {}

func (x *ChunkRequest) GetCoord() *ChunkCoord   { return x.Coord }
func (x *ChunkRequest) GetRequestingShard() string { return x.RequestingShard }

type ChunkResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Chunk      *ChunkData `protobuf:"bytes,1,opt,name=chunk,proto3" json:"chunk,omitempty"`
	Found      bool       `protobuf:"varint,2,opt,name=found,proto3" json:"found,omitempty"`
	OwnerShard string     `protobuf:"bytes,3,opt,name=owner_shard,json=ownerShard,proto3" json:"owner_shard,omitempty"`
}

func (x *ChunkResponse) Reset()         { *x = ChunkResponse{} }
func (x *ChunkResponse) String() string {
	return fmt.Sprintf("ChunkResponse{Chunk:%s, Found:%t, OwnerShard:%s}", x.Chunk, x.Found, x.OwnerShard)
}
func (*ChunkResponse) ProtoMessage() {}

func (x *ChunkResponse) GetChunk() *ChunkData { return x.Chunk }
func (x *ChunkResponse) GetFound() bool       { return x.Found }
func (x *ChunkResponse) GetOwnerShard() string { return x.OwnerShard }
