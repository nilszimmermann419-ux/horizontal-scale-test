package health

import (
	"encoding/json"
	"net/http"

	"github.com/shardedmc/v2/coordinator/internal/allocator"
	"github.com/shardedmc/v2/coordinator/internal/registry"
)

// Handler provides HTTP health endpoints
type Handler struct {
	registry *registry.ShardRegistry
}

// NewHandler creates a new health HTTP handler
func NewHandler(reg *registry.ShardRegistry) *Handler {
	return &Handler{registry: reg}
}

// Status represents the overall health status
type Status struct {
	Status string `json:"status"`
	Shards int    `json:"shards"`
}

// RegisterRoutes registers all health endpoints on the given mux
func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("/health", h.handleHealth)
	mux.HandleFunc("/health/shards", h.handleShards)
	mux.HandleFunc("/health/regions", h.handleRegions)
}

// handleHealth returns the overall coordinator health status
func (h *Handler) handleHealth(w http.ResponseWriter, r *http.Request) {
	shards := h.registry.GetAllShards()
	status := Status{
		Status: "healthy",
		Shards: len(shards),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(status)
}

// handleShards returns a list of all registered shards
func (h *Handler) handleShards(w http.ResponseWriter, r *http.Request) {
	shards := h.registry.GetAllShards()

	var response []map[string]interface{}
	for _, shard := range shards {
		response = append(response, map[string]interface{}{
			"id":           shard.ID,
			"address":      shard.Address,
			"port":         shard.Port,
			"capacity":     shard.Capacity,
			"player_count": shard.PlayerCount,
			"load":         shard.Load,
			"healthy":      shard.Healthy,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleRegions returns the region allocation map
func (h *Handler) handleRegions(w http.ResponseWriter, r *http.Request) {
	shards := h.registry.GetAllShards()

	// Build region allocation map
	regionMap := make(map[string]string)
	if len(shards) > 0 {
		// Generate a sample region map for all registered shards
		// In production, this would come from persistent storage
		for _, shard := range shards {
			owner, err := allocator.GetRegionOwner([2]int{0, 0}, shards)
			if err == nil {
				regionMap[shard.ID] = owner
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(regionMap)
}
