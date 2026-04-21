package proxy

import (
	"fmt"
	"io"
	"log"
	"net"
	"sync"
	"sync/atomic"
	"time"

	"github.com/shardedmc/coordinator/internal/shard"
)

const (
	MaxConcurrentConnections = 10000
	ConnectionTimeout        = 30 * time.Second
	BufferSize               = 32768
)

// Proxy handles incoming player connections and routes them to shards
type Proxy struct {
	listener      net.Listener
	shards        *shard.Manager
	conns         sync.Map // map[uint64]*PlayerConnection
	nextID        uint64
	closed        atomic.Bool
	connSemaphore chan struct{}
	connCount     int64 // atomic counter for active connections
}

// PlayerConnection represents a connected Minecraft player
type PlayerConnection struct {
	id       uint64
	conn     net.Conn
	shard    *shard.Shard
	username string
	uuid     string
}

// NewProxy creates a new proxy server
func NewProxy(addr string, shards *shard.Manager) (*Proxy, error) {
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, err
	}

	return &Proxy{
		listener:      listener,
		shards:        shards,
		connSemaphore: make(chan struct{}, MaxConcurrentConnections),
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
			// Got slot
		default:
			log.Printf("Connection limit reached, waiting...")
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

		wg.Add(1)
		go func() {
			defer wg.Done()
			defer func() { <-p.connSemaphore }()
			p.handleConnection(conn)
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

func (p *Proxy) handleConnection(conn net.Conn) {
	id := atomic.AddUint64(&p.nextID, 1)
	
	// Set connection timeout
	if err := conn.SetDeadline(time.Now().Add(ConnectionTimeout)); err != nil {
		log.Printf("Failed to set deadline for player %d: %v", id, err)
		if closeErr := conn.Close(); closeErr != nil {
			log.Printf("Failed to close connection for player %d: %v", id, closeErr)
		}
		return
	}
	
	// Route to least-loaded shard
	shard := p.shards.GetLeastLoadedShard()
	if shard == nil {
		log.Printf("No available shards for player %d", id)
		if closeErr := conn.Close(); closeErr != nil {
			log.Printf("Failed to close connection for player %d: %v", id, closeErr)
		}
		return
	}

	// Connect to the shard
	shardAddr := fmt.Sprintf("%s:%d", shard.Address, shard.Port)
	shardConn, err := net.DialTimeout("tcp", shardAddr, 5*time.Second)
	if err != nil {
		log.Printf("Failed to connect to shard %s for player %d: %v", shard.ID, id, err)
		if closeErr := conn.Close(); closeErr != nil {
			log.Printf("Failed to close connection for player %d: %v", id, closeErr)
		}
		return
	}

	pc := &PlayerConnection{
		id:    id,
		conn:  conn,
		shard: shard,
	}

	p.conns.Store(id, pc)
	atomic.AddInt64(&p.connCount, 1)
	defer func() {
		p.conns.Delete(id)
		atomic.AddInt64(&p.connCount, -1)
	}()

	shard.AddPlayer()
	defer shard.RemovePlayer()

	log.Printf("Player %d routed to shard %s (%s)", id, shard.ID, shardAddr)

	// Start bidirectional forwarding
	var wg sync.WaitGroup
	wg.Add(2)

	// Player -> Shard
	go func() {
		wg.Done()
		copied, err := io.Copy(shardConn, conn)
		if err != nil && err != io.EOF {
			log.Printf("Player %d -> Shard copy error: %v", id, err)
		}
		log.Printf("Player %d -> Shard: %d bytes", id, copied)
		if closeErr := shardConn.Close(); closeErr != nil {
			log.Printf("Error closing shard connection for player %d: %v", id, closeErr)
		}
	}()

	// Shard -> Player
	go func() {
		wg.Done()
		copied, err := io.Copy(conn, shardConn)
		if err != nil && err != io.EOF {
			log.Printf("Shard -> Player %d copy error: %v", id, err)
		}
		log.Printf("Shard -> Player %d: %d bytes", id, copied)
		if closeErr := conn.Close(); closeErr != nil {
			log.Printf("Error closing player connection for player %d: %v", id, closeErr)
		}
	}()

	wg.Wait()
	log.Printf("Player %d disconnected from shard %s", id, shard.ID)
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
	return int(atomic.LoadInt64(&p.connCount))
}