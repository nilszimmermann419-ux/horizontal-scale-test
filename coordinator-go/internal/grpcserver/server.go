package grpcserver

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
	// Input validation
	if req.ShardId == "" {
		return &pb.RegistrationResponse{
			Success: false,
			Message: "shard ID is required",
		}, nil
	}
	if req.Address == "" {
		return &pb.RegistrationResponse{
			Success: false,
			Message: "address is required",
		}, nil
	}
	if req.Port <= 0 || req.Port > 65535 {
		return &pb.RegistrationResponse{
			Success: false,
			Message: "invalid port number",
		}, nil
	}
	// Basic authentication check
	if req.Token == "" {
		return &pb.RegistrationResponse{
			Success: false,
			Message: "authentication token is required",
		}, nil
	}

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
	// TODO: Implement proper player transfer logic with shard coordination
	return &pb.TransferResponse{
		Accepted: true,
		Message: "Transfer accepted",
	}, nil
}

func (s *GRPCServer) ConfirmPlayerTransfer(ctx context.Context, req *pb.TransferConfirmation) (*pb.ConfirmationResponse, error) {
	// TODO: Implement proper transfer confirmation with state cleanup
	return &pb.ConfirmationResponse{
		Acknowledged: true,
	}, nil
}

func (s *GRPCServer) RequestChunkLoad(ctx context.Context, req *pb.ChunkLoadRequest) (*pb.ChunkLoadResponse, error) {
	// TODO: Implement chunk loading with storage backend integration
	return &pb.ChunkLoadResponse{
		Success: true,
	}, nil
}

func (s *GRPCServer) RequestChunkUnload(ctx context.Context, req *pb.ChunkUnloadRequest) (*pb.ChunkUnloadResponse, error) {
	// TODO: Implement chunk unloading with persistence and cleanup
	return &pb.ChunkUnloadResponse{
		Success: true,
	}, nil
}

func (s *GRPCServer) RequestChunkLock(ctx context.Context, req *pb.LockRequest) (*pb.LockResponse, error) {
	// TODO: Implement distributed chunk locking with deadlock prevention
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

func StartGRPCServer(addr string, shardMgr *shard.Manager) (*grpc.Server, chan error, error) {
	lis, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to listen: %v", err)
	}
	
	s := grpc.NewServer()
	pb.RegisterCoordinatorServiceServer(s, NewGRPCServer(shardMgr))
	
	errCh := make(chan error, 1)
	go func() {
		log.Printf("gRPC server listening on %s", addr)
		if err := s.Serve(lis); err != nil {
			log.Printf("gRPC server error: %v", err)
			errCh <- err
		}
		close(errCh)
	}()
	
	return s, errCh, nil
}