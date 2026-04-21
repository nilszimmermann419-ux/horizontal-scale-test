package dashboard

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"runtime"
	"sync"
	"time"

	"github.com/shardedmc/v2/coordinator/internal/allocator"
	"github.com/shardedmc/v2/coordinator/internal/registry"
)

// API provides REST endpoints for the dashboard
type API struct {
	registry     *registry.ShardRegistry
	startTime    time.Time
	eventCounter int64
	eventMu      sync.RWMutex
}

// NewAPI creates a new API instance
func NewAPI(reg *registry.ShardRegistry) *API {
	return &API{
		registry:  reg,
		startTime: time.Now(),
	}
}

// RegisterRoutes registers all API endpoints on the given mux
func (a *API) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("/api/shards", a.handleShards)
	mux.HandleFunc("/api/regions", a.handleRegions)
	mux.HandleFunc("/api/players", a.handlePlayers)
	mux.HandleFunc("/api/metrics", a.handleMetrics)
}

// ShardResponse represents a shard in API responses
type ShardResponse struct {
	ID       string  `json:"id"`
	Address  string  `json:"address"`
	Port     int     `json:"port"`
	Capacity int     `json:"capacity"`
	Players  int     `json:"players"`
	Load     float64 `json:"load"`
	Healthy  bool    `json:"healthy"`
}

// handleShards returns all registered shards as JSON
func (a *API) handleShards(w http.ResponseWriter, r *http.Request) {
	shards := a.registry.GetAllShards()

	response := make([]ShardResponse, 0, len(shards))
	for _, shard := range shards {
		response = append(response, ShardResponse{
			ID:       shard.ID,
			Address:  shard.Address,
			Port:     shard.Port,
			Capacity: shard.Capacity,
			Players:  shard.PlayerCount,
			Load:     shard.Load,
			Healthy:  shard.Healthy,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(response)
}

// handleRegions returns the region allocation map as JSON
func (a *API) handleRegions(w http.ResponseWriter, r *http.Request) {
	shards := a.registry.GetAllShards()

	// Build a sample region allocation map
	// In production, this would come from persistent storage
	regionMap := make(map[string]string)
	if len(shards) > 0 {
		// Generate a grid of regions around 0,0
		for x := -5; x <= 5; x++ {
			for z := -5; z <= 5; z++ {
				coord := [2]int{x, z}
				owner, err := allocator.GetRegionOwner(coord, shards)
				if err == nil {
					key := fmt.Sprintf("%d:%d", x, z)
					regionMap[key] = owner
				}
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(regionMap)
}

// PlayerResponse represents a player in API responses
type PlayerResponse struct {
	ID     string `json:"id"`
	Shard  string `json:"shard"`
	Region string `json:"region,omitempty"`
}

// PlayersResponse represents the players API response
type PlayersResponse struct {
	Count   int              `json:"count"`
	Players []PlayerResponse `json:"players"`
}

// handlePlayers returns player count and locations as JSON
func (a *API) handlePlayers(w http.ResponseWriter, r *http.Request) {
	shards := a.registry.GetAllShards()

	var totalPlayers int
	var players []PlayerResponse

	for _, shard := range shards {
		totalPlayers += shard.PlayerCount
		// Generate synthetic player data for demonstration
		for i := 0; i < shard.PlayerCount; i++ {
			players = append(players, PlayerResponse{
				ID:    fmt.Sprintf("player-%s-%d", shard.ID, i),
				Shard: shard.ID,
			})
		}
	}

	response := PlayersResponse{
		Count:   totalPlayers,
		Players: players,
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(response)
}

// MetricsResponse represents system metrics
type MetricsResponse struct {
	MemoryMB     float64 `json:"memory_mb"`
	CPUPercent   float64 `json:"cpu_percent"`
	Goroutines   int     `json:"goroutines"`
	Uptime       string  `json:"uptime"`
	EventsPerSec int64   `json:"events_per_sec"`
	TotalEvents  int64   `json:"total_events"`
}

// handleMetrics returns system metrics as JSON
func (a *API) handleMetrics(w http.ResponseWriter, r *http.Request) {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	// Calculate uptime
	uptime := time.Since(a.startTime)
	uptimeStr := formatDuration(uptime)

	// Get event throughput
	a.eventMu.RLock()
	totalEvents := a.eventCounter
	a.eventMu.RUnlock()

	eventsPerSec := int64(0)
	if uptime.Seconds() > 0 {
		eventsPerSec = int64(float64(totalEvents) / uptime.Seconds())
	}

	response := MetricsResponse{
		MemoryMB:     float64(m.Alloc) / 1024 / 1024,
		CPUPercent:   0.0, // Would need platform-specific code for real CPU metrics
		Goroutines:   runtime.NumGoroutine(),
		Uptime:       uptimeStr,
		EventsPerSec: eventsPerSec,
		TotalEvents:  totalEvents,
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(response)
}

// formatDuration formats a duration as a human-readable string
func formatDuration(d time.Duration) string {
	if d < time.Minute {
		return fmt.Sprintf("%.0fs", d.Seconds())
	}
	if d < time.Hour {
		return fmt.Sprintf("%.0fm%0.fs", d.Minutes(), d.Seconds()-float64(int(d.Minutes()))*60)
	}
	return fmt.Sprintf("%.0fh%0.fm", d.Hours(), d.Minutes()-float64(int(d.Hours()))*60)
}

// RecordEvent increments the event counter
func (a *API) RecordEvent() {
	a.eventMu.Lock()
	a.eventCounter++
	a.eventMu.Unlock()
}

// GetHostname returns the hostname of the machine
func GetHostname() string {
	hostname, err := os.Hostname()
	if err != nil {
		return "unknown"
	}
	return hostname
}
