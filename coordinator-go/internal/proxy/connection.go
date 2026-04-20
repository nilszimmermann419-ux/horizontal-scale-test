package proxy

import (
	"sync/atomic"

	"github.com/cloudwego/netpoll"
)

// ConnectionManager handles connection lifecycle and metrics
type ConnectionManager struct {
	activeConns   atomic.Int64
	totalConns    atomic.Uint64
	bytesReceived atomic.Uint64
	bytesSent     atomic.Uint64
}

// NewConnectionManager creates a new connection manager
func NewConnectionManager() *ConnectionManager {
	return &ConnectionManager{}
}

// AddConnection increments active connection count
func (cm *ConnectionManager) AddConnection() {
	cm.activeConns.Add(1)
	cm.totalConns.Add(1)
}

// RemoveConnection decrements active connection count
func (cm *ConnectionManager) RemoveConnection() {
	cm.activeConns.Add(-1)
}

// ActiveConnections returns the current number of active connections
func (cm *ConnectionManager) ActiveConnections() int64 {
	return cm.activeConns.Load()
}

// TotalConnections returns the total number of connections ever accepted
func (cm *ConnectionManager) TotalConnections() uint64 {
	return cm.totalConns.Load()
}

// RecordBytesReceived records received bytes
func (cm *ConnectionManager) RecordBytesReceived(n int) {
	cm.bytesReceived.Add(uint64(n))
}

// RecordBytesSent records sent bytes
func (cm *ConnectionManager) RecordBytesSent(n int) {
	cm.bytesSent.Add(uint64(n))
}

// BytesReceived returns total bytes received
func (cm *ConnectionManager) BytesReceived() uint64 {
	return cm.bytesReceived.Load()
}

// BytesSent returns total bytes sent
func (cm *ConnectionManager) BytesSent() uint64 {
	return cm.bytesSent.Load()
}

// ConnectionPool manages a pool of reusable connections
type ConnectionPool struct {
	pool chan netpoll.Connection
}

// NewConnectionPool creates a new connection pool
func NewConnectionPool(size int) *ConnectionPool {
	return &ConnectionPool{
		pool: make(chan netpoll.Connection, size),
	}
}

// Put adds a connection to the pool
func (cp *ConnectionPool) Put(conn netpoll.Connection) {
	select {
	case cp.pool <- conn:
	default:
		// Pool is full, close the connection
		conn.Close()
	}
}

// Get retrieves a connection from the pool
func (cp *ConnectionPool) Get() (netpoll.Connection, bool) {
	select {
	case conn := <-cp.pool:
		return conn, true
	default:
		return nil, false
	}
}
