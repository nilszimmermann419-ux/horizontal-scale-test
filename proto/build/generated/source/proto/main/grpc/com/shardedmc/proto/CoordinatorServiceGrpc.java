package com.shardedmc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Coordinator service
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.60.0)",
    comments = "Source: coordinator.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class CoordinatorServiceGrpc {

  private CoordinatorServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "shardedmc.CoordinatorService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.ShardInfo,
      com.shardedmc.proto.RegistrationResponse> getRegisterShardMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterShard",
      requestType = com.shardedmc.proto.ShardInfo.class,
      responseType = com.shardedmc.proto.RegistrationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.ShardInfo,
      com.shardedmc.proto.RegistrationResponse> getRegisterShardMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.ShardInfo, com.shardedmc.proto.RegistrationResponse> getRegisterShardMethod;
    if ((getRegisterShardMethod = CoordinatorServiceGrpc.getRegisterShardMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getRegisterShardMethod = CoordinatorServiceGrpc.getRegisterShardMethod) == null) {
          CoordinatorServiceGrpc.getRegisterShardMethod = getRegisterShardMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.ShardInfo, com.shardedmc.proto.RegistrationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterShard"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.ShardInfo.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.RegistrationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("RegisterShard"))
              .build();
        }
      }
    }
    return getRegisterShardMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.HeartbeatRequest,
      com.shardedmc.proto.HeartbeatResponse> getSendHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendHeartbeat",
      requestType = com.shardedmc.proto.HeartbeatRequest.class,
      responseType = com.shardedmc.proto.HeartbeatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.HeartbeatRequest,
      com.shardedmc.proto.HeartbeatResponse> getSendHeartbeatMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.HeartbeatRequest, com.shardedmc.proto.HeartbeatResponse> getSendHeartbeatMethod;
    if ((getSendHeartbeatMethod = CoordinatorServiceGrpc.getSendHeartbeatMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getSendHeartbeatMethod = CoordinatorServiceGrpc.getSendHeartbeatMethod) == null) {
          CoordinatorServiceGrpc.getSendHeartbeatMethod = getSendHeartbeatMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.HeartbeatRequest, com.shardedmc.proto.HeartbeatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendHeartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.HeartbeatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.HeartbeatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("SendHeartbeat"))
              .build();
        }
      }
    }
    return getSendHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.TransferRequest,
      com.shardedmc.proto.TransferResponse> getRequestPlayerTransferMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RequestPlayerTransfer",
      requestType = com.shardedmc.proto.TransferRequest.class,
      responseType = com.shardedmc.proto.TransferResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.TransferRequest,
      com.shardedmc.proto.TransferResponse> getRequestPlayerTransferMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.TransferRequest, com.shardedmc.proto.TransferResponse> getRequestPlayerTransferMethod;
    if ((getRequestPlayerTransferMethod = CoordinatorServiceGrpc.getRequestPlayerTransferMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getRequestPlayerTransferMethod = CoordinatorServiceGrpc.getRequestPlayerTransferMethod) == null) {
          CoordinatorServiceGrpc.getRequestPlayerTransferMethod = getRequestPlayerTransferMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.TransferRequest, com.shardedmc.proto.TransferResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RequestPlayerTransfer"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.TransferRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.TransferResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("RequestPlayerTransfer"))
              .build();
        }
      }
    }
    return getRequestPlayerTransferMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.TransferConfirmation,
      com.shardedmc.proto.ConfirmationResponse> getConfirmPlayerTransferMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmPlayerTransfer",
      requestType = com.shardedmc.proto.TransferConfirmation.class,
      responseType = com.shardedmc.proto.ConfirmationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.TransferConfirmation,
      com.shardedmc.proto.ConfirmationResponse> getConfirmPlayerTransferMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.TransferConfirmation, com.shardedmc.proto.ConfirmationResponse> getConfirmPlayerTransferMethod;
    if ((getConfirmPlayerTransferMethod = CoordinatorServiceGrpc.getConfirmPlayerTransferMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getConfirmPlayerTransferMethod = CoordinatorServiceGrpc.getConfirmPlayerTransferMethod) == null) {
          CoordinatorServiceGrpc.getConfirmPlayerTransferMethod = getConfirmPlayerTransferMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.TransferConfirmation, com.shardedmc.proto.ConfirmationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmPlayerTransfer"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.TransferConfirmation.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.ConfirmationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("ConfirmPlayerTransfer"))
              .build();
        }
      }
    }
    return getConfirmPlayerTransferMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.ChunkLoadRequest,
      com.shardedmc.proto.ChunkLoadResponse> getRequestChunkLoadMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RequestChunkLoad",
      requestType = com.shardedmc.proto.ChunkLoadRequest.class,
      responseType = com.shardedmc.proto.ChunkLoadResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.ChunkLoadRequest,
      com.shardedmc.proto.ChunkLoadResponse> getRequestChunkLoadMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.ChunkLoadRequest, com.shardedmc.proto.ChunkLoadResponse> getRequestChunkLoadMethod;
    if ((getRequestChunkLoadMethod = CoordinatorServiceGrpc.getRequestChunkLoadMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getRequestChunkLoadMethod = CoordinatorServiceGrpc.getRequestChunkLoadMethod) == null) {
          CoordinatorServiceGrpc.getRequestChunkLoadMethod = getRequestChunkLoadMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.ChunkLoadRequest, com.shardedmc.proto.ChunkLoadResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RequestChunkLoad"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.ChunkLoadRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.ChunkLoadResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("RequestChunkLoad"))
              .build();
        }
      }
    }
    return getRequestChunkLoadMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.ChunkUnloadRequest,
      com.shardedmc.proto.ChunkUnloadResponse> getRequestChunkUnloadMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RequestChunkUnload",
      requestType = com.shardedmc.proto.ChunkUnloadRequest.class,
      responseType = com.shardedmc.proto.ChunkUnloadResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.ChunkUnloadRequest,
      com.shardedmc.proto.ChunkUnloadResponse> getRequestChunkUnloadMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.ChunkUnloadRequest, com.shardedmc.proto.ChunkUnloadResponse> getRequestChunkUnloadMethod;
    if ((getRequestChunkUnloadMethod = CoordinatorServiceGrpc.getRequestChunkUnloadMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getRequestChunkUnloadMethod = CoordinatorServiceGrpc.getRequestChunkUnloadMethod) == null) {
          CoordinatorServiceGrpc.getRequestChunkUnloadMethod = getRequestChunkUnloadMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.ChunkUnloadRequest, com.shardedmc.proto.ChunkUnloadResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RequestChunkUnload"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.ChunkUnloadRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.ChunkUnloadResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("RequestChunkUnload"))
              .build();
        }
      }
    }
    return getRequestChunkUnloadMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.shardedmc.proto.EntityStateSync,
      com.shardedmc.proto.SyncResponse> getSyncEntityStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SyncEntityState",
      requestType = com.shardedmc.proto.EntityStateSync.class,
      responseType = com.shardedmc.proto.SyncResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.shardedmc.proto.EntityStateSync,
      com.shardedmc.proto.SyncResponse> getSyncEntityStateMethod() {
    io.grpc.MethodDescriptor<com.shardedmc.proto.EntityStateSync, com.shardedmc.proto.SyncResponse> getSyncEntityStateMethod;
    if ((getSyncEntityStateMethod = CoordinatorServiceGrpc.getSyncEntityStateMethod) == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        if ((getSyncEntityStateMethod = CoordinatorServiceGrpc.getSyncEntityStateMethod) == null) {
          CoordinatorServiceGrpc.getSyncEntityStateMethod = getSyncEntityStateMethod =
              io.grpc.MethodDescriptor.<com.shardedmc.proto.EntityStateSync, com.shardedmc.proto.SyncResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SyncEntityState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.EntityStateSync.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.shardedmc.proto.SyncResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CoordinatorServiceMethodDescriptorSupplier("SyncEntityState"))
              .build();
        }
      }
    }
    return getSyncEntityStateMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static CoordinatorServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CoordinatorServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CoordinatorServiceStub>() {
        @java.lang.Override
        public CoordinatorServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CoordinatorServiceStub(channel, callOptions);
        }
      };
    return CoordinatorServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static CoordinatorServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CoordinatorServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CoordinatorServiceBlockingStub>() {
        @java.lang.Override
        public CoordinatorServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CoordinatorServiceBlockingStub(channel, callOptions);
        }
      };
    return CoordinatorServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static CoordinatorServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CoordinatorServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CoordinatorServiceFutureStub>() {
        @java.lang.Override
        public CoordinatorServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CoordinatorServiceFutureStub(channel, callOptions);
        }
      };
    return CoordinatorServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Coordinator service
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default void registerShard(com.shardedmc.proto.ShardInfo request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.RegistrationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterShardMethod(), responseObserver);
    }

    /**
     */
    default void sendHeartbeat(com.shardedmc.proto.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendHeartbeatMethod(), responseObserver);
    }

    /**
     */
    default void requestPlayerTransfer(com.shardedmc.proto.TransferRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.TransferResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestPlayerTransferMethod(), responseObserver);
    }

    /**
     */
    default void confirmPlayerTransfer(com.shardedmc.proto.TransferConfirmation request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.ConfirmationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmPlayerTransferMethod(), responseObserver);
    }

    /**
     */
    default void requestChunkLoad(com.shardedmc.proto.ChunkLoadRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.ChunkLoadResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestChunkLoadMethod(), responseObserver);
    }

    /**
     */
    default void requestChunkUnload(com.shardedmc.proto.ChunkUnloadRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.ChunkUnloadResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestChunkUnloadMethod(), responseObserver);
    }

    /**
     */
    default void syncEntityState(com.shardedmc.proto.EntityStateSync request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.SyncResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSyncEntityStateMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service CoordinatorService.
   * <pre>
   * Coordinator service
   * </pre>
   */
  public static abstract class CoordinatorServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return CoordinatorServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service CoordinatorService.
   * <pre>
   * Coordinator service
   * </pre>
   */
  public static final class CoordinatorServiceStub
      extends io.grpc.stub.AbstractAsyncStub<CoordinatorServiceStub> {
    private CoordinatorServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CoordinatorServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CoordinatorServiceStub(channel, callOptions);
    }

    /**
     */
    public void registerShard(com.shardedmc.proto.ShardInfo request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.RegistrationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterShardMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void sendHeartbeat(com.shardedmc.proto.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void requestPlayerTransfer(com.shardedmc.proto.TransferRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.TransferResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestPlayerTransferMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void confirmPlayerTransfer(com.shardedmc.proto.TransferConfirmation request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.ConfirmationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmPlayerTransferMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void requestChunkLoad(com.shardedmc.proto.ChunkLoadRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.ChunkLoadResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestChunkLoadMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void requestChunkUnload(com.shardedmc.proto.ChunkUnloadRequest request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.ChunkUnloadResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestChunkUnloadMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void syncEntityState(com.shardedmc.proto.EntityStateSync request,
        io.grpc.stub.StreamObserver<com.shardedmc.proto.SyncResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSyncEntityStateMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service CoordinatorService.
   * <pre>
   * Coordinator service
   * </pre>
   */
  public static final class CoordinatorServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<CoordinatorServiceBlockingStub> {
    private CoordinatorServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CoordinatorServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CoordinatorServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.shardedmc.proto.RegistrationResponse registerShard(com.shardedmc.proto.ShardInfo request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterShardMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.shardedmc.proto.HeartbeatResponse sendHeartbeat(com.shardedmc.proto.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendHeartbeatMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.shardedmc.proto.TransferResponse requestPlayerTransfer(com.shardedmc.proto.TransferRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestPlayerTransferMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.shardedmc.proto.ConfirmationResponse confirmPlayerTransfer(com.shardedmc.proto.TransferConfirmation request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmPlayerTransferMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.shardedmc.proto.ChunkLoadResponse requestChunkLoad(com.shardedmc.proto.ChunkLoadRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestChunkLoadMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.shardedmc.proto.ChunkUnloadResponse requestChunkUnload(com.shardedmc.proto.ChunkUnloadRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestChunkUnloadMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.shardedmc.proto.SyncResponse syncEntityState(com.shardedmc.proto.EntityStateSync request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSyncEntityStateMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service CoordinatorService.
   * <pre>
   * Coordinator service
   * </pre>
   */
  public static final class CoordinatorServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<CoordinatorServiceFutureStub> {
    private CoordinatorServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CoordinatorServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CoordinatorServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.RegistrationResponse> registerShard(
        com.shardedmc.proto.ShardInfo request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterShardMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.HeartbeatResponse> sendHeartbeat(
        com.shardedmc.proto.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendHeartbeatMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.TransferResponse> requestPlayerTransfer(
        com.shardedmc.proto.TransferRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestPlayerTransferMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.ConfirmationResponse> confirmPlayerTransfer(
        com.shardedmc.proto.TransferConfirmation request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmPlayerTransferMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.ChunkLoadResponse> requestChunkLoad(
        com.shardedmc.proto.ChunkLoadRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestChunkLoadMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.ChunkUnloadResponse> requestChunkUnload(
        com.shardedmc.proto.ChunkUnloadRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestChunkUnloadMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.shardedmc.proto.SyncResponse> syncEntityState(
        com.shardedmc.proto.EntityStateSync request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSyncEntityStateMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_SHARD = 0;
  private static final int METHODID_SEND_HEARTBEAT = 1;
  private static final int METHODID_REQUEST_PLAYER_TRANSFER = 2;
  private static final int METHODID_CONFIRM_PLAYER_TRANSFER = 3;
  private static final int METHODID_REQUEST_CHUNK_LOAD = 4;
  private static final int METHODID_REQUEST_CHUNK_UNLOAD = 5;
  private static final int METHODID_SYNC_ENTITY_STATE = 6;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REGISTER_SHARD:
          serviceImpl.registerShard((com.shardedmc.proto.ShardInfo) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.RegistrationResponse>) responseObserver);
          break;
        case METHODID_SEND_HEARTBEAT:
          serviceImpl.sendHeartbeat((com.shardedmc.proto.HeartbeatRequest) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.HeartbeatResponse>) responseObserver);
          break;
        case METHODID_REQUEST_PLAYER_TRANSFER:
          serviceImpl.requestPlayerTransfer((com.shardedmc.proto.TransferRequest) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.TransferResponse>) responseObserver);
          break;
        case METHODID_CONFIRM_PLAYER_TRANSFER:
          serviceImpl.confirmPlayerTransfer((com.shardedmc.proto.TransferConfirmation) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.ConfirmationResponse>) responseObserver);
          break;
        case METHODID_REQUEST_CHUNK_LOAD:
          serviceImpl.requestChunkLoad((com.shardedmc.proto.ChunkLoadRequest) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.ChunkLoadResponse>) responseObserver);
          break;
        case METHODID_REQUEST_CHUNK_UNLOAD:
          serviceImpl.requestChunkUnload((com.shardedmc.proto.ChunkUnloadRequest) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.ChunkUnloadResponse>) responseObserver);
          break;
        case METHODID_SYNC_ENTITY_STATE:
          serviceImpl.syncEntityState((com.shardedmc.proto.EntityStateSync) request,
              (io.grpc.stub.StreamObserver<com.shardedmc.proto.SyncResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterShardMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.ShardInfo,
              com.shardedmc.proto.RegistrationResponse>(
                service, METHODID_REGISTER_SHARD)))
        .addMethod(
          getSendHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.HeartbeatRequest,
              com.shardedmc.proto.HeartbeatResponse>(
                service, METHODID_SEND_HEARTBEAT)))
        .addMethod(
          getRequestPlayerTransferMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.TransferRequest,
              com.shardedmc.proto.TransferResponse>(
                service, METHODID_REQUEST_PLAYER_TRANSFER)))
        .addMethod(
          getConfirmPlayerTransferMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.TransferConfirmation,
              com.shardedmc.proto.ConfirmationResponse>(
                service, METHODID_CONFIRM_PLAYER_TRANSFER)))
        .addMethod(
          getRequestChunkLoadMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.ChunkLoadRequest,
              com.shardedmc.proto.ChunkLoadResponse>(
                service, METHODID_REQUEST_CHUNK_LOAD)))
        .addMethod(
          getRequestChunkUnloadMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.ChunkUnloadRequest,
              com.shardedmc.proto.ChunkUnloadResponse>(
                service, METHODID_REQUEST_CHUNK_UNLOAD)))
        .addMethod(
          getSyncEntityStateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.shardedmc.proto.EntityStateSync,
              com.shardedmc.proto.SyncResponse>(
                service, METHODID_SYNC_ENTITY_STATE)))
        .build();
  }

  private static abstract class CoordinatorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    CoordinatorServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.shardedmc.proto.CoordinatorProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("CoordinatorService");
    }
  }

  private static final class CoordinatorServiceFileDescriptorSupplier
      extends CoordinatorServiceBaseDescriptorSupplier {
    CoordinatorServiceFileDescriptorSupplier() {}
  }

  private static final class CoordinatorServiceMethodDescriptorSupplier
      extends CoordinatorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    CoordinatorServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (CoordinatorServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new CoordinatorServiceFileDescriptorSupplier())
              .addMethod(getRegisterShardMethod())
              .addMethod(getSendHeartbeatMethod())
              .addMethod(getRequestPlayerTransferMethod())
              .addMethod(getConfirmPlayerTransferMethod())
              .addMethod(getRequestChunkLoadMethod())
              .addMethod(getRequestChunkUnloadMethod())
              .addMethod(getSyncEntityStateMethod())
              .build();
        }
      }
    }
    return result;
  }
}
