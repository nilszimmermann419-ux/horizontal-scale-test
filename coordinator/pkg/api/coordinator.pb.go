package api

import (
	"context"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Message types for CoordinatorService

type RegisterShardRequest struct {
	ShardId  string
	Address  string
	Port     int32
	Capacity int32
	Regions  []string
}

type RegisterShardResponse struct {
	AllocatedRegions map[string]string
	Success          bool
}

type HeartbeatRequest struct {
	ShardId     string
	PlayerCount int32
	Load        float64
}

type HeartbeatResponse struct {
	Success bool
}

type GetChunkOwnerRequest struct {
	ChunkX int32
	ChunkZ int32
}

type GetChunkOwnerResponse struct {
	ShardId string
}

type GetRegionMapRequest struct{}

type GetRegionMapResponse struct {
	RegionMap map[string]string
}

type GetPlayerShardRequest struct {
	PlayerId string
}

type GetPlayerShardResponse struct {
	ShardId string
}

type RecordPlayerPositionRequest struct {
	PlayerId string
	ChunkX   int32
	ChunkZ   int32
}

type RecordPlayerPositionResponse struct {
	Success bool
}

// CoordinatorServiceServer is the server API for CoordinatorService
type CoordinatorServiceServer interface {
	RegisterShard(context.Context, *RegisterShardRequest) (*RegisterShardResponse, error)
	Heartbeat(context.Context, *HeartbeatRequest) (*HeartbeatResponse, error)
	GetChunkOwner(context.Context, *GetChunkOwnerRequest) (*GetChunkOwnerResponse, error)
	GetRegionMap(context.Context, *GetRegionMapRequest) (*GetRegionMapResponse, error)
	GetPlayerShard(context.Context, *GetPlayerShardRequest) (*GetPlayerShardResponse, error)
	RecordPlayerPosition(context.Context, *RecordPlayerPositionRequest) (*RecordPlayerPositionResponse, error)
}

// UnimplementedCoordinatorServiceServer can be embedded to have forward compatible implementations
type UnimplementedCoordinatorServiceServer struct{}

func (UnimplementedCoordinatorServiceServer) RegisterShard(context.Context, *RegisterShardRequest) (*RegisterShardResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method RegisterShard not implemented")
}

func (UnimplementedCoordinatorServiceServer) Heartbeat(context.Context, *HeartbeatRequest) (*HeartbeatResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method Heartbeat not implemented")
}

func (UnimplementedCoordinatorServiceServer) GetChunkOwner(context.Context, *GetChunkOwnerRequest) (*GetChunkOwnerResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method GetChunkOwner not implemented")
}

func (UnimplementedCoordinatorServiceServer) GetRegionMap(context.Context, *GetRegionMapRequest) (*GetRegionMapResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method GetRegionMap not implemented")
}

func (UnimplementedCoordinatorServiceServer) GetPlayerShard(context.Context, *GetPlayerShardRequest) (*GetPlayerShardResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method GetPlayerShard not implemented")
}

func (UnimplementedCoordinatorServiceServer) RecordPlayerPosition(context.Context, *RecordPlayerPositionRequest) (*RecordPlayerPositionResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method RecordPlayerPosition not implemented")
}

// RegisterCoordinatorServiceServer registers the service with a gRPC server
func RegisterCoordinatorServiceServer(s *grpc.Server, srv CoordinatorServiceServer) {
	s.RegisterService(&_CoordinatorService_serviceDesc, srv)
}

func _CoordinatorService_RegisterShard_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(RegisterShardRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(CoordinatorServiceServer).RegisterShard(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/coordinator.CoordinatorService/RegisterShard",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(CoordinatorServiceServer).RegisterShard(ctx, req.(*RegisterShardRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _CoordinatorService_Heartbeat_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(HeartbeatRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(CoordinatorServiceServer).Heartbeat(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/coordinator.CoordinatorService/Heartbeat",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(CoordinatorServiceServer).Heartbeat(ctx, req.(*HeartbeatRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _CoordinatorService_GetChunkOwner_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(GetChunkOwnerRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(CoordinatorServiceServer).GetChunkOwner(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/coordinator.CoordinatorService/GetChunkOwner",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(CoordinatorServiceServer).GetChunkOwner(ctx, req.(*GetChunkOwnerRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _CoordinatorService_GetRegionMap_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(GetRegionMapRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(CoordinatorServiceServer).GetRegionMap(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/coordinator.CoordinatorService/GetRegionMap",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(CoordinatorServiceServer).GetRegionMap(ctx, req.(*GetRegionMapRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _CoordinatorService_GetPlayerShard_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(GetPlayerShardRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(CoordinatorServiceServer).GetPlayerShard(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/coordinator.CoordinatorService/GetPlayerShard",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(CoordinatorServiceServer).GetPlayerShard(ctx, req.(*GetPlayerShardRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _CoordinatorService_RecordPlayerPosition_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(RecordPlayerPositionRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(CoordinatorServiceServer).RecordPlayerPosition(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/coordinator.CoordinatorService/RecordPlayerPosition",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(CoordinatorServiceServer).RecordPlayerPosition(ctx, req.(*RecordPlayerPositionRequest))
	}
	return interceptor(ctx, in, info, handler)
}

var _CoordinatorService_serviceDesc = grpc.ServiceDesc{
	ServiceName: "coordinator.CoordinatorService",
	HandlerType: (*CoordinatorServiceServer)(nil),
	Methods: []grpc.MethodDesc{
		{
			MethodName: "RegisterShard",
			Handler:    _CoordinatorService_RegisterShard_Handler,
		},
		{
			MethodName: "Heartbeat",
			Handler:    _CoordinatorService_Heartbeat_Handler,
		},
		{
			MethodName: "GetChunkOwner",
			Handler:    _CoordinatorService_GetChunkOwner_Handler,
		},
		{
			MethodName: "GetRegionMap",
			Handler:    _CoordinatorService_GetRegionMap_Handler,
		},
		{
			MethodName: "GetPlayerShard",
			Handler:    _CoordinatorService_GetPlayerShard_Handler,
		},
		{
			MethodName: "RecordPlayerPosition",
			Handler:    _CoordinatorService_RecordPlayerPosition_Handler,
		},
	},
	Streams:  []grpc.StreamDesc{},
	Metadata: "coordinator.proto",
}
