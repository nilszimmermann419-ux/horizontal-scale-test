package main

import (
	"context"
	"fmt"
	"log"
	"net"

	"google.golang.org/grpc"
	pb "github.com/shardedmc/coordinator/pkg/proto"
	"github.com/shardedmc/coordinator/internal/shard"
)

// GRPCServer implements the CoordinatorService
type GRPCServer struct {
	pb.UnimplementedCoordinatorServiceServer
	shardMgr *shard.Manager
}

func NewGRPCServer(shardMgr *shard.Manager) *GRPCServer {
	return &GRPCServer{shardMgr: shardMgr}
}

func (s *GRPCServer) RegisterShard(ctx context.Context, req *pb.ShardInfo) (*pb.RegistrationResponse, error) {
	log.Printf("Shard registration request: %s at %s:%d (capacity: %d)", 
		req.ShardId, req.Address, req.Port, req.Capacity)
	
	_, err := s.shardMgr.RegisterShard(req.ShardId, req.Address, int(req.Port), int(req.Capacity))
	if err != nil {
		return &pb.RegistrationResponse{
			Success: false,
			Message: err.Error(),
		}, nil
	}
	
	return &pb.RegistrationResponse{
		Success: true,
		Message: "Shard registered successfully",
	}, nil
}

func (s *GRPCServer) SendHeartbeat(ctx context.Context, req *pb.HeartbeatRequest) (*pb.HeartbeatResponse, error) {
	sh, ok := s.shardMgr.GetShard(req.ShardId)
	if !ok {
		return &pb.HeartbeatResponse{
			Healthy: false,
		}, nil
	}
	
	sh.UpdateHeartbeat()
	
	return &pb.HeartbeatResponse{
		Healthy: true,
		ShouldShutdown: false,
	}, nil
}

func (s *GRPCServer) RequestPlayerTransfer(ctx context.Context, req *pb.TransferRequest) (*pb.TransferResponse, error) {
	return &pb.TransferResponse{
		Accepted: true,
		Message: "Transfer accepted",
	}, nil
}

func (s *GRPCServer) ConfirmPlayerTransfer(ctx context.Context, req *pb.TransferConfirmation) (*pb.ConfirmationResponse, error) {
	return &pb.ConfirmationResponse{
		Acknowledged: true,
	}, nil
}

func (s *GRPCServer) RequestChunkLoad(ctx context.Context, req *pb.ChunkLoadRequest) (*pb.ChunkLoadResponse, error) {
	return &pb.ChunkLoadResponse{
		Success: true,
	}, nil
}

func (s *GRPCServer) RequestChunkUnload(ctx context.Context, req *pb.ChunkUnloadRequest) (*pb.ChunkUnloadResponse, error) {
	return &pb.ChunkUnloadResponse{
		Success: true,
	}, nil
}

func (s *GRPCServer) RequestChunkLock(ctx context.Context, req *pb.LockRequest) (*pb.LockResponse, error) {
	return &pb.LockResponse{
		Success: true,
	}, nil
}

func (s *GRPCServer) ReleaseChunkLock(ctx context.Context, req *pb.LockRequest) (*pb.Empty, error) {
	return &pb.Empty{}, nil
}

func (s *GRPCServer) SyncEntityState(ctx context.Context, req *pb.EntityStateSync) (*pb.SyncResponse, error) {
	return &pb.SyncResponse{
		Success: true,
		SyncedCount: int32(len(req.Entities)),
	}, nil
}

func StartGRPCServer(addr string, shardMgr *shard.Manager) (*grpc.Server, error) {
	lis, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, fmt.Errorf("failed to listen: %v", err)
	}
	
	s := grpc.NewServer()
	pb.RegisterCoordinatorServiceServer(s, NewGRPCServer(shardMgr))
	
	go func() {
		log.Printf("gRPC server listening on %s", addr)
		if err := s.Serve(lis); err != nil {
			log.Printf("gRPC server error: %v", err)
		}
	}()
	
	return s, nil
}