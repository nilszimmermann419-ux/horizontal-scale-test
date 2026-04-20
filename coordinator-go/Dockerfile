# Build stage
FROM golang:1.21-alpine AS builder

WORKDIR /app

# Install dependencies
RUN apk add --no-cache git

# Copy go mod files
COPY coordinator-go/go.mod coordinator-go/go.sum ./
RUN go mod download

# Copy source code
COPY coordinator-go/ .

# Build
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o coordinator ./cmd/coordinator

# Final stage
FROM alpine:latest

RUN apk --no-cache add ca-certificates

WORKDIR /root/

# Copy binary from builder
COPY --from=builder /app/coordinator .

# Create data directory
RUN mkdir -p /data

# Expose ports
EXPOSE 25565 50051

# Run
CMD ["./coordinator", "-proxy", ":25565", "-grpc", ":50051", "-storage", "/data"]