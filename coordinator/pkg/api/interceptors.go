package api

import (
	"context"
	"log"
	"runtime/debug"

	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

// requestIDKey is the metadata key for request tracking
const requestIDKey = "x-request-id"

// LoggingInterceptor logs all incoming RPC calls
func LoggingInterceptor(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
	log.Printf("[gRPC] %s - incoming request", info.FullMethod)

	resp, err := handler(ctx, req)
	if err != nil {
		log.Printf("[gRPC] %s - error: %v", info.FullMethod, err)
	} else {
		log.Printf("[gRPC] %s - completed successfully", info.FullMethod)
	}

	return resp, err
}

// RecoveryInterceptor recovers from panics and returns an internal error
func RecoveryInterceptor(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resp interface{}, err error) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("[gRPC] %s - panic recovered: %v\n%s", info.FullMethod, r, string(debug.Stack()))
			err = grpc.Errorf(13, "internal server error") // codes.Internal
		}
	}()

	return handler(ctx, req)
}

// MetadataInterceptor extracts request ID from incoming metadata and adds it to context
func MetadataInterceptor(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if ok {
		if ids := md.Get(requestIDKey); len(ids) > 0 {
			ctx = context.WithValue(ctx, requestIDKey, ids[0])
			log.Printf("[gRPC] %s - request-id: %s", info.FullMethod, ids[0])
		}
	}

	return handler(ctx, req)
}

// UnaryInterceptorChain chains multiple unary interceptors together
func UnaryInterceptorChain(interceptors ...grpc.UnaryServerInterceptor) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		// Build the chain from last to first
		current := handler
		for i := len(interceptors) - 1; i >= 0; i-- {
			interceptor := interceptors[i]
			next := current
			current = func(ctx context.Context, req interface{}) (interface{}, error) {
				return interceptor(ctx, req, info, next)
			}
		}
		return current(ctx, req)
	}
}
