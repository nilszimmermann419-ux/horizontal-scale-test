package proto

import (
	"context"
	"fmt"

	"google.golang.org/protobuf/runtime/protoimpl"
	grpc "google.golang.org/grpc"
)

type ShardInfo struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Id          string        `protobuf:"bytes,1,opt,name=id,proto3" json:"id,omitempty"`
	Address     string        `protobuf:"bytes,2,opt,name=address,proto3" json:"address,omitempty"`
	Port        int32         `protobuf:"varint,3,opt,name=port,proto3" json:"port,omitempty"`
	Capacity    int32         `protobuf:"varint,4,opt,name=capacity,proto3" json:"capacity,omitempty"`
	PlayerCount int32         `protobuf:"varint,5,opt,name=player_count,json=playerCount,proto3" json:"player_count,omitempty"`
	Load        float64       `protobuf:"fixed64,6,opt,name=load,proto3" json:"load,omitempty"`
	Healthy     bool          `protobuf:"varint,7,opt,name=healthy,proto3" json:"healthy,omitempty"`
	Regions     []*RegionCoord `protobuf:"bytes,8,rep,name=regions,proto3" json:"regions,omitempty"`
}

func (x *ShardInfo) Reset()      { *x = ShardInfo{} }
func (x *ShardInfo) String() string {
	return fmt.Sprintf("ShardInfo{Id:%s, Address:%s, Port:%d, Capacity:%d, PlayerCount:%d, Load:%.3f, Healthy:%t, Regions:%d}",
		x.Id, x.Address, x.Port, x.Capacity, x.PlayerCount, x.Load, x.Healthy, len(x.Regions))
}
func (*ShardInfo) ProtoMessage() {}

func (x *ShardInfo) GetId() string            { return x.Id }
func (x *ShardInfo) GetAddress() string       { return x.Address }
func (x *ShardInfo) GetPort() int32           { return x.Port }
func (x *ShardInfo) GetCapacity() int32       { return x.Capacity }
func (x *ShardInfo) GetPlayerCount() int32    { return x.PlayerCount }
func (x *ShardInfo) GetLoad() float64         { return x.Load }
func (x *ShardInfo) GetHealthy() bool         { return x.Healthy }
func (x *ShardInfo) GetRegions() []*RegionCoord { return x.Regions }

type ShardRegistrationRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Shard *ShardInfo `protobuf:"bytes,1,opt,name=shard,proto3" json:"shard,omitempty"`
}

func (x *ShardRegistrationRequest) Reset()         { *x = ShardRegistrationRequest{} }
func (x *ShardRegistrationRequest) String() string { return fmt.Sprintf("ShardRegistrationRequest{Shard:%s}", x.Shard) }
func (*ShardRegistrationRequest) ProtoMessage()    {}

func (x *ShardRegistrationRequest) GetShard() *ShardInfo { return x.Shard }

type ShardRegistrationResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Success       bool   `protobuf:"varint,1,opt,name=success,proto3" json:"success,omitempty"`
	CoordinatorId string `protobuf:"bytes,2,opt,name=coordinator_id,json=coordinatorId,proto3" json:"coordinator_id,omitempty"`
}

func (x *ShardRegistrationResponse) Reset()         { *x = ShardRegistrationResponse{} }
func (x *ShardRegistrationResponse) String() string { return fmt.Sprintf("ShardRegistrationResponse{Success:%t, CoordinatorId:%s}", x.Success, x.CoordinatorId) }
func (*ShardRegistrationResponse) ProtoMessage()    {}

func (x *ShardRegistrationResponse) GetSuccess() bool       { return x.Success }
func (x *ShardRegistrationResponse) GetCoordinatorId() string { return x.CoordinatorId }

type HeartbeatRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	ShardId      string        `protobuf:"bytes,1,opt,name=shard_id,json=shardId,proto3" json:"shard_id,omitempty"`
	CpuUsage     float64       `protobuf:"fixed64,2,opt,name=cpu_usage,json=cpuUsage,proto3" json:"cpu_usage,omitempty"`
	MemoryUsage  float64       `protobuf:"fixed64,3,opt,name=memory_usage,json=memoryUsage,proto3" json:"memory_usage,omitempty"`
	PlayerCount  int32         `protobuf:"varint,4,opt,name=player_count,json=playerCount,proto3" json:"player_count,omitempty"`
	Load         float64       `protobuf:"fixed64,5,opt,name=load,proto3" json:"load,omitempty"`
	Healthy      bool          `protobuf:"varint,6,opt,name=healthy,proto3" json:"healthy,omitempty"`
	Regions      []*RegionCoord `protobuf:"bytes,7,rep,name=regions,proto3" json:"regions,omitempty"`
}

func (x *HeartbeatRequest) Reset()         { *x = HeartbeatRequest{} }
func (x *HeartbeatRequest) String() string {
	return fmt.Sprintf("HeartbeatRequest{ShardId:%s, CpuUsage:%.3f, MemoryUsage:%.3f, PlayerCount:%d, Load:%.3f, Healthy:%t, Regions:%d}",
		x.ShardId, x.CpuUsage, x.MemoryUsage, x.PlayerCount, x.Load, x.Healthy, len(x.Regions))
}
func (*HeartbeatRequest) ProtoMessage() {}

func (x *HeartbeatRequest) GetShardId() string          { return x.ShardId }
func (x *HeartbeatRequest) GetCpuUsage() float64        { return x.CpuUsage }
func (x *HeartbeatRequest) GetMemoryUsage() float64     { return x.MemoryUsage }
func (x *HeartbeatRequest) GetPlayerCount() int32       { return x.PlayerCount }
func (x *HeartbeatRequest) GetLoad() float64            { return x.Load }
func (x *HeartbeatRequest) GetHealthy() bool            { return x.Healthy }
func (x *HeartbeatRequest) GetRegions() []*RegionCoord  { return x.Regions }

type HeartbeatResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Accepted bool     `protobuf:"varint,1,opt,name=accepted,proto3" json:"accepted,omitempty"`
	Commands []string `protobuf:"bytes,2,rep,name=commands,proto3" json:"commands,omitempty"`
}

func (x *HeartbeatResponse) Reset()         { *x = HeartbeatResponse{} }
func (x *HeartbeatResponse) String() string { return fmt.Sprintf("HeartbeatResponse{Accepted:%t, Commands:%v}", x.Accepted, x.Commands) }
func (*HeartbeatResponse) ProtoMessage()    {}

func (x *HeartbeatResponse) GetAccepted() bool     { return x.Accepted }
func (x *HeartbeatResponse) GetCommands() []string { return x.Commands }

type GetChunkOwnerRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Coord *ChunkCoord `protobuf:"bytes,1,opt,name=coord,proto3" json:"coord,omitempty"`
}

func (x *GetChunkOwnerRequest) Reset()         { *x = GetChunkOwnerRequest{} }
func (x *GetChunkOwnerRequest) String() string { return fmt.Sprintf("GetChunkOwnerRequest{Coord:%s}", x.Coord) }
func (*GetChunkOwnerRequest) ProtoMessage()    {}

func (x *GetChunkOwnerRequest) GetCoord() *ChunkCoord { return x.Coord }

type GetChunkOwnerResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Shard *ShardInfo `protobuf:"bytes,1,opt,name=shard,proto3" json:"shard,omitempty"`
	Found bool       `protobuf:"varint,2,opt,name=found,proto3" json:"found,omitempty"`
}

func (x *GetChunkOwnerResponse) Reset()         { *x = GetChunkOwnerResponse{} }
func (x *GetChunkOwnerResponse) String() string { return fmt.Sprintf("GetChunkOwnerResponse{Shard:%s, Found:%t}", x.Shard, x.Found) }
func (*GetChunkOwnerResponse) ProtoMessage()    {}

func (x *GetChunkOwnerResponse) GetShard() *ShardInfo { return x.Shard }
func (x *GetChunkOwnerResponse) GetFound() bool       { return x.Found }

type GetRegionMapRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields
}

func (x *GetRegionMapRequest) Reset()         { *x = GetRegionMapRequest{} }
func (x *GetRegionMapRequest) String() string { return "GetRegionMapRequest{}" }
func (*GetRegionMapRequest) ProtoMessage()    {}

type GetRegionMapResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	RegionMap map[string]*ShardInfo `protobuf:"bytes,1,rep,name=region_map,json=regionMap,proto3" json:"region_map,omitempty" protobuf_key:"bytes,1,opt,name=key,proto3" protobuf_val:"bytes,2,opt,name=value,proto3"`
}

func (x *GetRegionMapResponse) Reset()         { *x = GetRegionMapResponse{} }
func (x *GetRegionMapResponse) String() string { return fmt.Sprintf("GetRegionMapResponse{RegionMap:%d}", len(x.RegionMap)) }
func (*GetRegionMapResponse) ProtoMessage()    {}

func (x *GetRegionMapResponse) GetRegionMap() map[string]*ShardInfo { return x.RegionMap }

type GetPlayerShardRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	PlayerUuid string `protobuf:"bytes,1,opt,name=player_uuid,json=playerUuid,proto3" json:"player_uuid,omitempty"`
}

func (x *GetPlayerShardRequest) Reset()         { *x = GetPlayerShardRequest{} }
func (x *GetPlayerShardRequest) String() string { return fmt.Sprintf("GetPlayerShardRequest{PlayerUuid:%s}", x.PlayerUuid) }
func (*GetPlayerShardRequest) ProtoMessage()    {}

func (x *GetPlayerShardRequest) GetPlayerUuid() string { return x.PlayerUuid }

type GetPlayerShardResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Shard *ShardInfo `protobuf:"bytes,1,opt,name=shard,proto3" json:"shard,omitempty"`
	Found bool       `protobuf:"varint,2,opt,name=found,proto3" json:"found,omitempty"`
}

func (x *GetPlayerShardResponse) Reset()         { *x = GetPlayerShardResponse{} }
func (x *GetPlayerShardResponse) String() string { return fmt.Sprintf("GetPlayerShardResponse{Shard:%s, Found:%t}", x.Shard, x.Found) }
func (*GetPlayerShardResponse) ProtoMessage()    {}

func (x *GetPlayerShardResponse) GetShard() *ShardInfo { return x.Shard }
func (x *GetPlayerShardResponse) GetFound() bool       { return x.Found }

type RecordPlayerPositionRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	PlayerUuid string `protobuf:"bytes,1,opt,name=player_uuid,json=playerUuid,proto3" json:"player_uuid,omitempty"`
	Position   *Vec3d `protobuf:"bytes,2,opt,name=position,proto3" json:"position,omitempty"`
	ShardId    string `protobuf:"bytes,3,opt,name=shard_id,json=shardId,proto3" json:"shard_id,omitempty"`
}

func (x *RecordPlayerPositionRequest) Reset()         { *x = RecordPlayerPositionRequest{} }
func (x *RecordPlayerPositionRequest) String() string { return fmt.Sprintf("RecordPlayerPositionRequest{PlayerUuid:%s, Position:%s, ShardId:%s}", x.PlayerUuid, x.Position, x.ShardId) }
func (*RecordPlayerPositionRequest) ProtoMessage()    {}

func (x *RecordPlayerPositionRequest) GetPlayerUuid() string { return x.PlayerUuid }
func (x *RecordPlayerPositionRequest) GetPosition() *Vec3d   { return x.Position }
func (x *RecordPlayerPositionRequest) GetShardId() string    { return x.ShardId }

type RecordPlayerPositionResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Success bool `protobuf:"varint,1,opt,name=success,proto3" json:"success,omitempty"`
}

func (x *RecordPlayerPositionResponse) Reset()         { *x = RecordPlayerPositionResponse{} }
func (x *RecordPlayerPositionResponse) String() string { return fmt.Sprintf("RecordPlayerPositionResponse{Success:%t}", x.Success) }
func (*RecordPlayerPositionResponse) ProtoMessage()    {}

func (x *RecordPlayerPositionResponse) GetSuccess() bool { return x.Success }

// CoordinatorServiceServer is the server API for CoordinatorService service.
type CoordinatorServiceServer interface {
	RegisterShard(context.Context, *ShardRegistrationRequest) (*ShardRegistrationResponse, error)
	Heartbeat(context.Context, *HeartbeatRequest) (*HeartbeatResponse, error)
	GetChunkOwner(context.Context, *GetChunkOwnerRequest) (*GetChunkOwnerResponse, error)
	GetRegionMap(context.Context, *GetRegionMapRequest) (*GetRegionMapResponse, error)
	GetPlayerShard(context.Context, *GetPlayerShardRequest) (*GetPlayerShardResponse, error)
	RecordPlayerPosition(context.Context, *RecordPlayerPositionRequest) (*RecordPlayerPositionResponse, error)
}

// UnimplementedCoordinatorServiceServer can be embedded to have forward compatible implementations.
type UnimplementedCoordinatorServiceServer struct{}

func (UnimplementedCoordinatorServiceServer) RegisterShard(context.Context, *ShardRegistrationRequest) (*ShardRegistrationResponse, error) {
	return nil, fmt.Errorf("method RegisterShard not implemented")
}
func (UnimplementedCoordinatorServiceServer) Heartbeat(context.Context, *HeartbeatRequest) (*HeartbeatResponse, error) {
	return nil, fmt.Errorf("method Heartbeat not implemented")
}
func (UnimplementedCoordinatorServiceServer) GetChunkOwner(context.Context, *GetChunkOwnerRequest) (*GetChunkOwnerResponse, error) {
	return nil, fmt.Errorf("method GetChunkOwner not implemented")
}
func (UnimplementedCoordinatorServiceServer) GetRegionMap(context.Context, *GetRegionMapRequest) (*GetRegionMapResponse, error) {
	return nil, fmt.Errorf("method GetRegionMap not implemented")
}
func (UnimplementedCoordinatorServiceServer) GetPlayerShard(context.Context, *GetPlayerShardRequest) (*GetPlayerShardResponse, error) {
	return nil, fmt.Errorf("method GetPlayerShard not implemented")
}
func (UnimplementedCoordinatorServiceServer) RecordPlayerPosition(context.Context, *RecordPlayerPositionRequest) (*RecordPlayerPositionResponse, error) {
	return nil, fmt.Errorf("method RecordPlayerPosition not implemented")
}

// CoordinatorServiceClient is the client API for CoordinatorService service.
type CoordinatorServiceClient interface {
	RegisterShard(ctx context.Context, in *ShardRegistrationRequest, opts ...grpc.CallOption) (*ShardRegistrationResponse, error)
	Heartbeat(ctx context.Context, in *HeartbeatRequest, opts ...grpc.CallOption) (*HeartbeatResponse, error)
	GetChunkOwner(ctx context.Context, in *GetChunkOwnerRequest, opts ...grpc.CallOption) (*GetChunkOwnerResponse, error)
	GetRegionMap(ctx context.Context, in *GetRegionMapRequest, opts ...grpc.CallOption) (*GetRegionMapResponse, error)
	GetPlayerShard(ctx context.Context, in *GetPlayerShardRequest, opts ...grpc.CallOption) (*GetPlayerShardResponse, error)
	RecordPlayerPosition(ctx context.Context, in *RecordPlayerPositionRequest, opts ...grpc.CallOption) (*RecordPlayerPositionResponse, error)
}

type coordinatorServiceClient struct {
	cc grpc.ClientConnInterface
}

func NewCoordinatorServiceClient(cc grpc.ClientConnInterface) CoordinatorServiceClient {
	return &coordinatorServiceClient{cc}
}

func (c *coordinatorServiceClient) RegisterShard(ctx context.Context, in *ShardRegistrationRequest, opts ...grpc.CallOption) (*ShardRegistrationResponse, error) {
	out := new(ShardRegistrationResponse)
	err := c.cc.Invoke(ctx, "/shardedmc.v2.CoordinatorService/RegisterShard", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *coordinatorServiceClient) Heartbeat(ctx context.Context, in *HeartbeatRequest, opts ...grpc.CallOption) (*HeartbeatResponse, error) {
	out := new(HeartbeatResponse)
	err := c.cc.Invoke(ctx, "/shardedmc.v2.CoordinatorService/Heartbeat", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *coordinatorServiceClient) GetChunkOwner(ctx context.Context, in *GetChunkOwnerRequest, opts ...grpc.CallOption) (*GetChunkOwnerResponse, error) {
	out := new(GetChunkOwnerResponse)
	err := c.cc.Invoke(ctx, "/shardedmc.v2.CoordinatorService/GetChunkOwner", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *coordinatorServiceClient) GetRegionMap(ctx context.Context, in *GetRegionMapRequest, opts ...grpc.CallOption) (*GetRegionMapResponse, error) {
	out := new(GetRegionMapResponse)
	err := c.cc.Invoke(ctx, "/shardedmc.v2.CoordinatorService/GetRegionMap", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *coordinatorServiceClient) GetPlayerShard(ctx context.Context, in *GetPlayerShardRequest, opts ...grpc.CallOption) (*GetPlayerShardResponse, error) {
	out := new(GetPlayerShardResponse)
	err := c.cc.Invoke(ctx, "/shardedmc.v2.CoordinatorService/GetPlayerShard", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *coordinatorServiceClient) RecordPlayerPosition(ctx context.Context, in *RecordPlayerPositionRequest, opts ...grpc.CallOption) (*RecordPlayerPositionResponse, error) {
	out := new(RecordPlayerPositionResponse)
	err := c.cc.Invoke(ctx, "/shardedmc.v2.CoordinatorService/RecordPlayerPosition", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

// CoordinatorService_ServiceDesc is the grpc.ServiceDesc for CoordinatorService service.
var CoordinatorService_ServiceDesc = grpc.ServiceDesc{
	ServiceName: "shardedmc.v2.CoordinatorService",
	HandlerType: (*CoordinatorServiceServer)(nil),
	Methods: []grpc.MethodDesc{
		{MethodName: "RegisterShard", Handler: nil},
		{MethodName: "Heartbeat", Handler: nil},
		{MethodName: "GetChunkOwner", Handler: nil},
		{MethodName: "GetRegionMap", Handler: nil},
		{MethodName: "GetPlayerShard", Handler: nil},
		{MethodName: "RecordPlayerPosition", Handler: nil},
	},
	Streams:  []grpc.StreamDesc{},
	Metadata: "shared/proto/coordinator.proto",
}
