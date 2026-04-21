package metrics

import (
	"time"

	"github.com/prometheus/client_golang/prometheus"
)

var (
	// connections_active tracks the number of active connections
	connectionsActive = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "connections_active",
		Help: "Number of active connections",
	})

	// connections_total is a counter for total connections
	connectionsTotal = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "connections_total",
		Help: "Total number of connections",
	})

	// bytes_transferred_total is a counter for total bytes transferred
	bytesTransferredTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "bytes_transferred_total",
		Help: "Total number of bytes transferred",
	}, []string{"direction"})

	// packets_forwarded_total is a counter for total packets forwarded
	packetsForwardedTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "packets_forwarded_total",
		Help: "Total number of packets forwarded",
	}, []string{"direction"})

	// packet_forward_latency is a histogram for packet forwarding latency
	packetForwardLatency = prometheus.NewHistogram(prometheus.HistogramOpts{
		Name:    "packet_forward_latency_seconds",
		Help:    "Latency of packet forwarding in seconds",
		Buckets: prometheus.ExponentialBuckets(0.0001, 2, 16),
	})

	// player_routes_total is a counter for player routing operations
	playerRoutesTotal = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "player_routes_total",
		Help: "Total number of player routing operations",
	})

	// route_errors_total is a counter for routing errors
	routeErrorsTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "route_errors_total",
		Help: "Total number of routing errors",
	}, []string{"reason"})
)

func init() {
	prometheus.MustRegister(connectionsActive)
	prometheus.MustRegister(connectionsTotal)
	prometheus.MustRegister(bytesTransferredTotal)
	prometheus.MustRegister(packetsForwardedTotal)
	prometheus.MustRegister(packetForwardLatency)
	prometheus.MustRegister(playerRoutesTotal)
	prometheus.MustRegister(routeErrorsTotal)
}

// IncConnectionsActive increments the active connections gauge
func IncConnectionsActive() {
	connectionsActive.Inc()
}

// DecConnectionsActive decrements the active connections gauge
func DecConnectionsActive() {
	connectionsActive.Dec()
}

// IncConnectionsTotal increments the total connections counter
func IncConnectionsTotal() {
	connectionsTotal.Inc()
}

// IncBytesTransferred increments the bytes transferred counter for a direction ("in" or "out")
func IncBytesTransferred(direction string, bytes float64) {
	bytesTransferredTotal.WithLabelValues(direction).Add(bytes)
}

// IncPacketsForwarded increments the packets forwarded counter for a direction ("in" or "out")
func IncPacketsForwarded(direction string) {
	packetsForwardedTotal.WithLabelValues(direction).Inc()
}

// ObservePacketForwardLatency records packet forwarding latency
func ObservePacketForwardLatency(duration time.Duration) {
	packetForwardLatency.Observe(duration.Seconds())
}

// IncPlayerRoutes increments the player routes counter
func IncPlayerRoutes() {
	playerRoutesTotal.Inc()
}

// IncRouteErrors increments the route errors counter for a reason
func IncRouteErrors(reason string) {
	routeErrorsTotal.WithLabelValues(reason).Inc()
}
