package metrics

import (
	"fmt"
	"net/http"
	"sync/atomic"
)

// Metrics holds all proxy metrics
type Metrics struct {
	connections   int64
	bytesIn       int64
	bytesOut      int64
	packetsIn     int64
	packetsOut    int64
	activePlayers int64
}

// NewMetrics creates a new metrics collector
func NewMetrics() *Metrics {
	return &Metrics{}
}

// RecordConnection increments the connection count
func (m *Metrics) RecordConnection() {
	atomic.AddInt64(&m.connections, 1)
	atomic.AddInt64(&m.activePlayers, 1)
}

// RecordDisconnection decrements active players
func (m *Metrics) RecordDisconnection() {
	atomic.AddInt64(&m.activePlayers, -1)
}

// RecordBytesIn records incoming bytes
func (m *Metrics) RecordBytesIn(n int64) {
	atomic.AddInt64(&m.bytesIn, n)
}

// RecordBytesOut records outgoing bytes
func (m *Metrics) RecordBytesOut(n int64) {
	atomic.AddInt64(&m.bytesOut, n)
}

// RecordPacketIn records an incoming packet
func (m *Metrics) RecordPacketIn() {
	atomic.AddInt64(&m.packetsIn, 1)
}

// RecordPacketOut records an outgoing packet
func (m *Metrics) RecordPacketOut() {
	atomic.AddInt64(&m.packetsOut, 1)
}

// GetConnections returns total connections
func (m *Metrics) GetConnections() int64 {
	return atomic.LoadInt64(&m.connections)
}

// GetActivePlayers returns currently active players
func (m *Metrics) GetActivePlayers() int64 {
	return atomic.LoadInt64(&m.activePlayers)
}

// GetBytesTransferred returns total bytes in and out
func (m *Metrics) GetBytesTransferred() (int64, int64) {
	return atomic.LoadInt64(&m.bytesIn), atomic.LoadInt64(&m.bytesOut)
}

// GetPacketsForwarded returns total packets in and out
func (m *Metrics) GetPacketsForwarded() (int64, int64) {
	return atomic.LoadInt64(&m.packetsIn), atomic.LoadInt64(&m.packetsOut)
}

// Handler returns an HTTP handler for Prometheus metrics
func (m *Metrics) Handler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; version=0.0.4")

		connections := m.GetConnections()
		activePlayers := m.GetActivePlayers()
		bytesIn, bytesOut := m.GetBytesTransferred()
		packetsIn, packetsOut := m.GetPacketsForwarded()

		fmt.Fprintf(w, "# HELP shardedmc_connections_total Total number of connections\n")
		fmt.Fprintf(w, "# TYPE shardedmc_connections_total counter\n")
		fmt.Fprintf(w, "shardedmc_connections_total %d\n\n", connections)

		fmt.Fprintf(w, "# HELP shardedmc_active_players Current number of active players\n")
		fmt.Fprintf(w, "# TYPE shardedmc_active_players gauge\n")
		fmt.Fprintf(w, "shardedmc_active_players %d\n\n", activePlayers)

		fmt.Fprintf(w, "# HELP shardedmc_bytes_transferred_total Total bytes transferred\n")
		fmt.Fprintf(w, "# TYPE shardedmc_bytes_transferred_total counter\n")
		fmt.Fprintf(w, "shardedmc_bytes_transferred_total{direction=\"in\"} %d\n", bytesIn)
		fmt.Fprintf(w, "shardedmc_bytes_transferred_total{direction=\"out\"} %d\n\n", bytesOut)

		fmt.Fprintf(w, "# HELP shardedmc_packets_forwarded_total Total packets forwarded\n")
		fmt.Fprintf(w, "# TYPE shardedmc_packets_forwarded_total counter\n")
		fmt.Fprintf(w, "shardedmc_packets_forwarded_total{direction=\"in\"} %d\n", packetsIn)
		fmt.Fprintf(w, "shardedmc_packets_forwarded_total{direction=\"out\"} %d\n", packetsOut)
	}
}

// StartMetricsServer starts an HTTP server for Prometheus metrics
func (m *Metrics) StartMetricsServer(addr string) error {
	http.Handle("/metrics", m.Handler())
	return http.ListenAndServe(addr, nil)
}
