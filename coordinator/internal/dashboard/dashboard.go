package dashboard

import (
	"embed"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/shardedmc/v2/coordinator/internal/registry"
)

//go:embed template.html
var templateFS embed.FS

// Dashboard provides a web dashboard for the coordinator
type Dashboard struct {
	registry *registry.ShardRegistry
	server   *http.Server
}

// NewDashboard creates a new dashboard instance
func NewDashboard(reg *registry.ShardRegistry, port int) *Dashboard {
	d := &Dashboard{
		registry: reg,
	}

	mux := http.NewServeMux()
	
	// Dashboard UI
	mux.HandleFunc("/dashboard", d.handleDashboard)
	
	// API endpoints
	api := NewAPI(reg)
	api.RegisterRoutes(mux)
	
	d.server = &http.Server{
		Addr:         fmt.Sprintf(":%d", port),
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	return d
}

// Start starts the dashboard HTTP server
func (d *Dashboard) Start() error {
	log.Printf("Dashboard starting on http://localhost%s/dashboard", d.server.Addr)
	go func() {
		if err := d.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("Dashboard server error: %v", err)
		}
	}()
	return nil
}

// Stop stops the dashboard server
func (d *Dashboard) Stop() error {
	return d.server.Close()
}

// handleDashboard serves the dashboard HTML page
func (d *Dashboard) handleDashboard(w http.ResponseWriter, r *http.Request) {
	data, err := templateFS.ReadFile("template.html")
	if err != nil {
		http.Error(w, "Failed to load template", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write(data)
}
