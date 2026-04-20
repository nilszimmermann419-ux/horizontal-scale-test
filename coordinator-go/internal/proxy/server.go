package proxy

import (
	"log"
	"sync"
	"sync/atomic"

	"github.com/cloudwego/netpoll"
	"github.com/shardedmc/coordinator/internal/shard"
)

// Proxy handles incoming player connections and routes them to shards
type Proxy struct {
	listener netpoll.Listener
	shards   *shard.Manager
	conns    sync.Map // map[uint64]*PlayerConnection
	nextID   uint64
	mu       sync.RWMutex
	closed   atomic.Bool
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
		listener: listener,
		shards:   shards,
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

		conn, err := p.listener.Accept()
		if err != nil {
			if p.closed.Load() {
				break
			}
			log.Printf("Accept error: %v", err)
			continue
		}

		// Type assert to netpoll.Connection
		npConn, ok := conn.(netpoll.Connection)
		if !ok {
			log.Printf("Failed to cast connection to netpoll.Connection, closing")
			if conn != nil {
				conn.Close()
			}
			continue
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
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

	// Start packet forwarding
	p.forwardPackets(pc)
}

func (p *Proxy) forwardPackets(pc *PlayerConnection) {
	buf := make([]byte, 65536)

	for {
		n, err := pc.conn.Read(buf)
		if err != nil {
			log.Printf("Player %d disconnected: %v", pc.id, err)
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
