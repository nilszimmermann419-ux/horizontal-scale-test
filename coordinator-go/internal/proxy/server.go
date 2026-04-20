package proxy

import (
	"context"
	"errors"
	"log"
	"net"
	"sync"
	"sync/atomic"
	"time"

	"github.com/cloudwego/netpoll"
	"github.com/shardedmc/coordinator/internal/shard"
)

const (
	MaxConcurrentConnections = 10000
	ConnectionReadTimeout    = 30 * time.Second
	ConnectionWriteTimeout   = 10 * time.Second
	BufferPoolSize           = 65536
)

// Proxy handles incoming player connections and routes them to shards
type Proxy struct {
	listener          netpoll.Listener
	shards            *shard.Manager
	conns             sync.Map // map[uint64]*PlayerConnection
	nextID            uint64
	mu                sync.RWMutex
	closed            atomic.Bool
	connSemaphore     chan struct{} // Limits concurrent connections
	bufferPool        sync.Pool
	connectionTimeout time.Duration
}

// PlayerConnection represents a connected Minecraft player
type PlayerConnection struct {
	id       uint64
	conn     netpoll.Connection
	shard    *shard.Shard
	username string
	uuid     string
}

// NewProxy creates a new proxy server
func NewProxy(addr string, shards *shard.Manager) (*Proxy, error) {
	listener, err := netpoll.CreateListener("tcp", addr)
	if err != nil {
		return nil, err
	}

	return &Proxy{
		listener:          listener,
		shards:            shards,
		connSemaphore:     make(chan struct{}, MaxConcurrentConnections),
		connectionTimeout: ConnectionReadTimeout,
		bufferPool: sync.Pool{
			New: func() interface{} {
				b := make([]byte, BufferPoolSize)
				return &b
			},
		},
	}, nil
}

// Start begins accepting connections
func (p *Proxy) Start() error {
	log.Printf("Proxy listening on %s", p.listener.Addr())

	var wg sync.WaitGroup
	for {
		if p.closed.Load() {
			break
		}

		// Acquire semaphore slot
		select {
		case p.connSemaphore <- struct{}{}:
			// Got slot, continue
		default:
			// Too many connections, wait and retry
			log.Printf("Connection limit reached (%d), waiting...", MaxConcurrentConnections)
			select {
			case p.connSemaphore <- struct{}{}:
			case <-time.After(time.Second):
				continue
			}
		}

		conn, err := p.listener.Accept()
		if err != nil {
			<-p.connSemaphore // Release slot
			if p.closed.Load() {
				break
			}
			log.Printf("Accept error: %v", err)
			continue
		}

		// Type assert to netpoll.Connection
		npConn, ok := conn.(netpoll.Connection)
		if !ok {
			<-p.connSemaphore // Release slot
			log.Printf("Failed to cast connection to netpoll.Connection, closing")
			if conn != nil {
				conn.Close()
			}
			continue
		}

		// Set read deadline to prevent hanging connections
		if tcpConn, ok := npConn.(interface{ SetReadDeadline(t time.Time) error }); ok {
			tcpConn.SetReadDeadline(time.Now().Add(ConnectionReadTimeout))
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
			defer func() { <-p.connSemaphore }() // Release slot when done
			p.handleConnection(npConn)
		}()
	}

	wg.Wait()
	return nil
}

// Stop gracefully shuts down the proxy
func (p *Proxy) Stop() error {
	p.closed.Store(true)
	return p.listener.Close()
}

func (p *Proxy) handleConnection(conn netpoll.Connection) {
	id := atomic.AddUint64(&p.nextID, 1)
	pc := &PlayerConnection{
		id:   id,
		conn: conn,
	}

	p.conns.Store(id, pc)
	defer p.conns.Delete(id)

	// Route to least-loaded shard
	shard := p.shards.GetLeastLoadedShard()
	if shard == nil {
		log.Printf("No available shards for player %d", id)
		conn.Close()
		return
	}

	pc.shard = shard
	shard.AddPlayer()
	defer shard.RemovePlayer()

	// Start packet forwarding with timeout context
	ctx, cancel := context.WithTimeout(context.Background(), p.connectionTimeout)
	defer cancel()

	p.forwardPackets(ctx, pc)
}

func (p *Proxy) forwardPackets(ctx context.Context, pc *PlayerConnection) {
	bufPtr := p.bufferPool.Get().(*[]byte)
	buf := *bufPtr
	defer p.bufferPool.Put(bufPtr)

	for {
		// Check for context cancellation
		select {
		case <-ctx.Done():
			log.Printf("Player %d connection timed out", pc.id)
			return
		default:
		}

		n, err := pc.conn.Read(buf)
		if err != nil {
			if errors.Is(err, net.ErrClosed) || errors.Is(err, context.DeadlineExceeded) {
				log.Printf("Player %d disconnected", pc.id)
			} else {
				log.Printf("Player %d read error: %v", pc.id, err)
			}
			return
		}

		// Forward to shard - for now just log
		// In production, this would route to the shard's connection
		_ = buf[:n]
	}
}

// GetConnection returns a player connection by ID
func (p *Proxy) GetConnection(id uint64) (*PlayerConnection, bool) {
	val, ok := p.conns.Load(id)
	if !ok {
		return nil, false
	}
	return val.(*PlayerConnection), true
}

// ConnectionCount returns the number of active connections
func (p *Proxy) ConnectionCount() int {
	count := 0
	p.conns.Range(func(_, _ interface{}) bool {
		count++
		return true
	})
	return count
}
