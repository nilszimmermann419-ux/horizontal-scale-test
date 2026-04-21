package chaos

import (
	"flag"
	"fmt"
	"log"
	"math/rand"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"
)

// ChaosEngine runs chaos engineering tests
type ChaosEngine struct {
	coordinatorAddrs []string
	proxyAddrs       []string
	shardAddrs       []string
	interval         time.Duration
	stopCh           chan struct{}
	wg               sync.WaitGroup
	stats            *ChaosStats
}

// ChaosStats tracks chaos test results
type ChaosStats struct {
	mu              sync.RWMutex
	CoordinatorKills int
	ProxyKills      int
	ShardDisconnects int
	TotalEvents     int
	LostEvents      int
	RecoveryTimes   []time.Duration
}

// NewChaosEngine creates a new chaos engineering test runner
func NewChaosEngine(coordinators, proxies, shards []string, interval time.Duration) *ChaosEngine {
	return &ChaosEngine{
		coordinatorAddrs: coordinators,
		proxyAddrs:       proxies,
		shardAddrs:       shards,
		interval:         interval,
		stopCh:           make(chan struct{}),
		stats:            &ChaosStats{},
	}
}

// Start begins the chaos testing
func (c *ChaosEngine) Start() {
	log.Println("=== Chaos Engineering Tests Starting ===")
	log.Printf("Targets: %d coordinators, %d proxies, %d shards",
		len(c.coordinatorAddrs), len(c.proxyAddrs), len(c.shardAddrs))
	log.Printf("Interval: %v", c.interval)

	c.wg.Add(1)
	go c.chaosLoop()

	c.wg.Add(1)
	go c.monitorLoop()
}

// Stop halts chaos testing
func (c *ChaosEngine) Stop() {
	close(c.stopCh)
	c.wg.Wait()
}

// chaosLoop randomly kills/disconnects components
func (c *ChaosEngine) chaosLoop() {
	defer c.wg.Done()

	ticker := time.NewTicker(c.interval)
	defer ticker.Stop()

	for {
		select {
		case <-c.stopCh:
			return
		case <-ticker.C:
			c.injectChaos()
		}
	}
}

// injectChaos randomly selects and disrupts a component
func (c *ChaosEngine) injectChaos() {
	if len(c.coordinatorAddrs) == 0 && len(c.proxyAddrs) == 0 && len(c.shardAddrs) == 0 {
		return
	}

	// Randomly choose what to disrupt
	choices := []string{}
	if len(c.coordinatorAddrs) > 0 {
		choices = append(choices, "coordinator")
	}
	if len(c.proxyAddrs) > 0 {
		choices = append(choices, "proxy")
	}
	if len(c.shardAddrs) > 0 {
		choices = append(choices, "shard")
	}

	choice := choices[rand.Intn(len(choices))]

	switch choice {
	case "coordinator":
		c.killCoordinator()
	case "proxy":
		c.killProxy()
	case "shard":
		c.disconnectShard()
	}
}

// killCoordinator randomly kills a coordinator instance
func (c *ChaosEngine) killCoordinator() {
	addr := c.coordinatorAddrs[rand.Intn(len(c.coordinatorAddrs))]
	log.Printf("[CHAOS] Killing coordinator at %s", addr)

	start := time.Now()
	if err := c.killProcess(addr); err != nil {
		log.Printf("[CHAOS] Failed to kill coordinator: %v", err)
		return
	}

	c.stats.mu.Lock()
	c.stats.CoordinatorKills++
	c.stats.RecoveryTimes = append(c.stats.RecoveryTimes, time.Since(start))
	c.stats.mu.Unlock()

	// Simulate recovery by removing from targets temporarily
	go c.simulateRecovery(addr, "coordinator")
}

// killProxy randomly kills a proxy instance
func (c *ChaosEngine) killProxy() {
	addr := c.proxyAddrs[rand.Intn(len(c.proxyAddrs))]
	log.Printf("[CHAOS] Killing proxy at %s", addr)

	start := time.Now()
	if err := c.killProcess(addr); err != nil {
		log.Printf("[CHAOS] Failed to kill proxy: %v", err)
		return
	}

	c.stats.mu.Lock()
	c.stats.ProxyKills++
	c.stats.RecoveryTimes = append(c.stats.RecoveryTimes, time.Since(start))
	c.stats.mu.Unlock()

	go c.simulateRecovery(addr, "proxy")
}

// disconnectShard simulates a shard disconnection
func (c *ChaosEngine) disconnectShard() {
	addr := c.shardAddrs[rand.Intn(len(c.shardAddrs))]
	log.Printf("[CHAOS] Disconnecting shard at %s", addr)

	start := time.Now()

	// Simulate network partition by blocking connection
	if err := c.blockConnection(addr); err != nil {
		log.Printf("[CHAOS] Failed to disconnect shard: %v", err)
		return
	}

	c.stats.mu.Lock()
	c.stats.ShardDisconnects++
	c.stats.RecoveryTimes = append(c.stats.RecoveryTimes, time.Since(start))
	c.stats.mu.Unlock()

	go c.simulateRecovery(addr, "shard")
}

// killProcess attempts to kill a process (placeholder implementation)
func (c *ChaosEngine) killProcess(addr string) error {
	// In a real implementation, this would:
	// 1. SSH to the machine
	// 2. Find the process by port/address
	// 3. Kill it
	// For testing, we simulate this
	log.Printf("[CHAOS] Simulating process kill for %s", addr)
	time.Sleep(100 * time.Millisecond)
	return nil
}

// blockConnection simulates a network partition
func (c *ChaosEngine) blockConnection(addr string) error {
	// In a real implementation, this would:
	// 1. Use iptables to block connections
	// 2. Or use network namespace isolation
	log.Printf("[CHAOS] Simulating network partition for %s", addr)
	time.Sleep(100 * time.Millisecond)
	return nil
}

// simulateRecovery simulates component recovery after chaos
func (c *ChaosEngine) simulateRecovery(addr string, componentType string) {
	recoveryTime := time.Duration(rand.Intn(5)+2) * time.Second
	time.Sleep(recoveryTime)
	log.Printf("[CHAOS] %s at %s recovered after %v", componentType, addr, recoveryTime)
}

// monitorLoop continuously monitors system health
func (c *ChaosEngine) monitorLoop() {
	defer c.wg.Done()

	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-c.stopCh:
			return
		case <-ticker.C:
			c.checkHealth()
		}
	}
}

// checkHealth verifies system health and data integrity
func (c *ChaosEngine) checkHealth() {
	// In a real implementation, this would:
	// 1. Check coordinator health endpoints
	// 2. Verify proxy availability
	// 3. Check shard connectivity
	// 4. Verify data consistency
	// 5. Count lost events

	c.stats.mu.RLock()
	coordinatorKills := c.stats.CoordinatorKills
	proxyKills := c.stats.ProxyKills
	shardDisconnects := c.stats.ShardDisconnects
	c.stats.mu.RUnlock()

	log.Printf("[CHAOS] Health check - Coordinators killed: %d, Proxies killed: %d, Shards disconnected: %d",
		coordinatorKills, proxyKills, shardDisconnects)
}

// GetStats returns current chaos test statistics
func (c *ChaosEngine) GetStats() ChaosStats {
	c.stats.mu.RLock()
	defer c.stats.mu.RUnlock()

	return ChaosStats{
		CoordinatorKills: c.stats.CoordinatorKills,
		ProxyKills:      c.stats.ProxyKills,
		ShardDisconnects: c.stats.ShardDisconnects,
		TotalEvents:     c.stats.TotalEvents,
		LostEvents:      c.stats.LostEvents,
		RecoveryTimes:   append([]time.Duration{}, c.stats.RecoveryTimes...),
	}
}

// PrintStats prints the final chaos test statistics
func (c *ChaosEngine) PrintStats() {
	stats := c.GetStats()

	fmt.Println()
	fmt.Println("=== Chaos Engineering Test Results ===")
	fmt.Printf("Coordinator Kills:  %d\n", stats.CoordinatorKills)
	fmt.Printf("Proxy Kills:        %d\n", stats.ProxyKills)
	fmt.Printf("Shard Disconnects:  %d\n", stats.ShardDisconnects)
	fmt.Printf("Total Events:       %d\n", stats.TotalEvents)
	fmt.Printf("Lost Events:        %d\n", stats.LostEvents)
	fmt.Printf("Data Loss Rate:     %.4f%%\n", float64(stats.LostEvents)/float64(stats.TotalEvents+1)*100)

	if len(stats.RecoveryTimes) > 0 {
		var totalRecovery time.Duration
		for _, rt := range stats.RecoveryTimes {
			totalRecovery += rt
		}
		avgRecovery := totalRecovery / time.Duration(len(stats.RecoveryTimes))
		fmt.Printf("Avg Recovery Time:  %v\n", avgRecovery)
		fmt.Printf("Min Recovery Time:  %v\n", stats.RecoveryTimes[0])
		fmt.Printf("Max Recovery Time:  %v\n", stats.RecoveryTimes[len(stats.RecoveryTimes)-1])
	}
}

// RunChaosTest runs a complete chaos engineering test
func RunChaosTest(duration time.Duration) {
	engine := NewChaosEngine(
		[]string{"localhost:8080", "localhost:8081"}, // Coordinators
		[]string{"localhost:25565", "localhost:25566"}, // Proxies
		[]string{"localhost:25567", "localhost:25568", "localhost:25569"}, // Shards
		15*time.Second, // Chaos interval
	)

	engine.Start()

	// Wait for duration or interrupt
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	select {
	case <-time.After(duration):
		log.Println("Chaos test duration complete")
	case <-sigCh:
		log.Println("Chaos test interrupted")
	}

	engine.Stop()
	engine.PrintStats()
}

// main is the entry point for running chaos tests directly
func main() {
	duration := flag.Duration("duration", 5*time.Minute, "Chaos test duration")
	coordinators := flag.String("coordinators", "localhost:8080", "Comma-separated coordinator addresses")
	proxies := flag.String("proxies", "localhost:25565", "Comma-separated proxy addresses")
	shards := flag.String("shards", "localhost:25567", "Comma-separated shard addresses")
	interval := flag.Duration("interval", 15*time.Second, "Interval between chaos events")

	flag.Parse()

	engine := NewChaosEngine(
		parseAddrs(*coordinators),
		parseAddrs(*proxies),
		parseAddrs(*shards),
		*interval,
	)

	engine.Start()

	// Wait for duration or interrupt
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	select {
	case <-time.After(*duration):
		log.Println("Chaos test duration complete")
	case <-sigCh:
		log.Println("Chaos test interrupted")
	}

	engine.Stop()
	engine.PrintStats()
}

func parseAddrs(s string) []string {
	if s == "" {
		return nil
	}
	var addrs []string
	for _, addr := range strings.Split(s, ",") {
		addr = strings.TrimSpace(addr)
		if addr != "" {
			addrs = append(addrs, addr)
		}
	}
	return addrs
}
