package main

import (
	"flag"
	"fmt"
	"log"
	"math/rand"
	"net"
	"os"
	"os/signal"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/shardedmc/v2/proxy/pkg/protocol"
)

// Config holds load test configuration
type Config struct {
	PlayerCount int
	Duration    time.Duration
	PacketRate  int
	ProxyAddr   string
	ShardAddr   string
}

// Stats holds load test statistics
type Stats struct {
	Connections    int64
	PacketsSent    int64
	PacketsRecv    int64
	BytesSent      int64
	BytesRecv      int64
	Errors         int64
	LatencyTotal   int64
	LatencyCount   int64
	DisconnCount   int64
}

// Player simulates a game client
type Player struct {
	id       string
	conn     net.Conn
	config   *Config
	stats    *Stats
	stopCh   chan struct{}
	wg       sync.WaitGroup
}

// NewPlayer creates a new simulated player
func NewPlayer(id int, config *Config, stats *Stats) *Player {
	return &Player{
		id:     fmt.Sprintf("loadtest-player-%d", id),
		config: config,
		stats:  stats,
		stopCh: make(chan struct{}),
	}
}

// Connect establishes connection to proxy
func (p *Player) Connect() error {
	start := time.Now()
	conn, err := net.Dial("tcp", p.config.ProxyAddr)
	if err != nil {
		atomic.AddInt64(&p.stats.Errors, 1)
		return fmt.Errorf("connect to proxy: %w", err)
	}

	latency := time.Since(start).Milliseconds()
	atomic.AddInt64(&p.stats.LatencyTotal, latency)
	atomic.AddInt64(&p.stats.LatencyCount, 1)
	atomic.AddInt64(&p.stats.Connections, 1)

	p.conn = conn
	return nil
}

// Start begins sending packets and receiving responses
func (p *Player) Start() {
	p.wg.Add(2)
	go p.sendLoop()
	go p.recvLoop()
}

// sendLoop continuously sends packets at the configured rate
func (p *Player) sendLoop() {
	defer p.wg.Done()

	ticker := time.NewTicker(time.Second / time.Duration(p.config.PacketRate))
	defer ticker.Stop()

	for {
		select {
		case <-p.stopCh:
			return
		case <-ticker.C:
			if err := p.sendPacket(); err != nil {
				atomic.AddInt64(&p.stats.Errors, 1)
				return
			}
		}
	}
}

// sendPacket sends a single game packet
func (p *Player) sendPacket() error {
	// Create a simple position update packet
	packet := &protocol.Packet{
		ID:   0x12, // Position packet
		Data: generatePositionData(),
	}

	start := time.Now()
	if err := protocol.WritePacket(p.conn, packet); err != nil {
		return err
	}

	latency := time.Since(start).Milliseconds()
	atomic.AddInt64(&p.stats.LatencyTotal, latency)
	atomic.AddInt64(&p.stats.LatencyCount, 1)
	atomic.AddInt64(&p.stats.PacketsSent, 1)
	atomic.AddInt64(&p.stats.BytesSent, int64(len(packet.Data)+5))

	return nil
}

// recvLoop receives packets from the server
func (p *Player) recvLoop() {
	defer p.wg.Done()

	for {
		select {
		case <-p.stopCh:
			return
		default:
		}

		p.conn.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
		packet, err := protocol.ReadPacket(p.conn)
		if err != nil {
			if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
				continue
			}
			atomic.AddInt64(&p.stats.Errors, 1)
			return
		}

		atomic.AddInt64(&p.stats.PacketsRecv, 1)
		atomic.AddInt64(&p.stats.BytesRecv, int64(packet.Length))
	}
}

// Stop disconnects the player
func (p *Player) Stop() {
	close(p.stopCh)
	p.wg.Wait()
	if p.conn != nil {
		p.conn.Close()
		atomic.AddInt64(&p.stats.DisconnCount, 1)
	}
}

// generatePositionData creates random position data
func generatePositionData() []byte {
	x := rand.Float64() * 1000
	y := rand.Float64() * 100
	z := rand.Float64() * 1000
	return []byte(fmt.Sprintf("%.2f,%.2f,%.2f", x, y, z))
}

func main() {
	var config Config

	flag.IntVar(&config.PlayerCount, "players", 100, "Number of concurrent players to simulate")
	flag.DurationVar(&config.Duration, "duration", 60*time.Second, "Test duration")
	flag.IntVar(&config.PacketRate, "rate", 20, "Packets per second per player")
	flag.StringVar(&config.ProxyAddr, "proxy", "localhost:25565", "Proxy address")
	flag.StringVar(&config.ShardAddr, "shard", "localhost:25566", "Shard address (for direct connection)")
	flag.Parse()

	log.Printf("=== ShardedMC Load Test ===")
	log.Printf("Players: %d, Duration: %v, Rate: %d pkt/s", config.PlayerCount, config.Duration, config.PacketRate)
	log.Printf("Proxy: %s", config.ProxyAddr)

	var stats Stats
	players := make([]*Player, 0, config.PlayerCount)

	// Connect all players
	log.Printf("Connecting %d players...", config.PlayerCount)
	for i := 0; i < config.PlayerCount; i++ {
		player := NewPlayer(i, &config, &stats)
		if err := player.Connect(); err != nil {
			log.Printf("Player %d failed to connect: %v", i, err)
			continue
		}
		players = append(players, player)

		if (i+1)%10 == 0 {
			log.Printf("Connected %d/%d players...", i+1, config.PlayerCount)
		}
	}

	log.Printf("Successfully connected %d players", len(players))

	// Start all players
	for _, player := range players {
		player.Start()
	}

	// Setup signal handling for graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	// Progress reporting ticker
	progressTicker := time.NewTicker(10 * time.Second)
	defer progressTicker.Stop()

	// Test duration timer
	testTimer := time.NewTimer(config.Duration)
	defer testTimer.Stop()

	log.Printf("Load test running for %v...", config.Duration)

runLoop:
	for {
		select {
		case <-sigCh:
			log.Println("Received interrupt, stopping...")
			break runLoop
		case <-testTimer.C:
			log.Println("Test duration complete")
			break runLoop
		case <-progressTicker.C:
			printStats(&stats)
		}
	}

	// Stop all players
	log.Printf("Disconnecting %d players...", len(players))
	for _, player := range players {
		player.Stop()
	}

	// Print final results
	printFinalResults(&stats, config.Duration)
}

// printStats prints current statistics
func printStats(stats *Stats) {
	conns := atomic.LoadInt64(&stats.Connections)
	sent := atomic.LoadInt64(&stats.PacketsSent)
	recv := atomic.LoadInt64(&stats.PacketsRecv)
	errors := atomic.LoadInt64(&stats.Errors)
	latencyTotal := atomic.LoadInt64(&stats.LatencyTotal)
	latencyCount := atomic.LoadInt64(&stats.LatencyCount)

	avgLatency := int64(0)
	if latencyCount > 0 {
		avgLatency = latencyTotal / latencyCount
	}

	log.Printf("Stats: conns=%d sent=%d recv=%d errors=%d avg_latency=%dms",
		conns, sent, recv, errors, avgLatency)
}

// printFinalResults prints the final test results
func printFinalResults(stats *Stats, duration time.Duration) {
	conns := atomic.LoadInt64(&stats.Connections)
	sent := atomic.LoadInt64(&stats.PacketsSent)
	recv := atomic.LoadInt64(&stats.PacketsRecv)
	bytesSent := atomic.LoadInt64(&stats.BytesSent)
	bytesRecv := atomic.LoadInt64(&stats.BytesRecv)
	errors := atomic.LoadInt64(&stats.Errors)
	latencyTotal := atomic.LoadInt64(&stats.LatencyTotal)
	latencyCount := atomic.LoadInt64(&stats.LatencyCount)

	avgLatency := int64(0)
	if latencyCount > 0 {
		avgLatency = latencyTotal / latencyCount
	}

	throughput := float64(sent) / duration.Seconds()
	recvThroughput := float64(recv) / duration.Seconds()

	fmt.Println()
	fmt.Println("=== Load Test Results ===")
	fmt.Printf("Duration:         %v\n", duration)
	fmt.Printf("Connections:      %d\n", conns)
	fmt.Printf("Packets Sent:     %d (%.1f/sec)\n", sent, throughput)
	fmt.Printf("Packets Received: %d (%.1f/sec)\n", recv, recvThroughput)
	fmt.Printf("Bytes Sent:       %d\n", bytesSent)
	fmt.Printf("Bytes Received:   %d\n", bytesRecv)
	fmt.Printf("Errors:           %d\n", errors)
	fmt.Printf("Avg Latency:      %dms\n", avgLatency)
	fmt.Printf("Error Rate:       %.2f%%\n", float64(errors)/float64(sent)*100)
}
