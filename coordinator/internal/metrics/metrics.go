package metrics

import (
	"net/http"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
	// shards_total is a gauge tracking the total number of registered shards
	shardsTotal = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "shards_total",
		Help: "Total number of registered shards",
	})

	// shards_healthy is a gauge tracking the number of healthy shards
	shardsHealthy = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "shards_healthy",
		Help: "Number of healthy shards",
	})

	// regions_total is a gauge tracking the total number of allocated regions
	regionsTotal = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "regions_total",
		Help: "Total number of allocated regions",
	})

	// region_allocations is a counter for region allocation operations
	regionAllocations = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "region_allocations_total",
		Help: "Total number of region allocations",
	}, []string{"shard_id"})

	// grpc_requests_total is a counter for gRPC requests
	grpcRequestsTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "grpc_requests_total",
		Help: "Total number of gRPC requests",
	}, []string{"method", "status"})

	// grpc_request_duration is a histogram for gRPC request durations
	grpcRequestDuration = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "grpc_request_duration_seconds",
		Help:    "Duration of gRPC requests in seconds",
		Buckets: prometheus.DefBuckets,
	}, []string{"method"})

	// player_routes_total is a counter for player routing operations
	playerRoutesTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "player_routes_total",
		Help: "Total number of player routing operations",
	}, []string{"shard_id"})
)

func init() {
	prometheus.MustRegister(shardsTotal)
	prometheus.MustRegister(shardsHealthy)
	prometheus.MustRegister(regionsTotal)
	prometheus.MustRegister(regionAllocations)
	prometheus.MustRegister(grpcRequestsTotal)
	prometheus.MustRegister(grpcRequestDuration)
	prometheus.MustRegister(playerRoutesTotal)
}

// SetShardsTotal updates the shards_total gauge
func SetShardsTotal(count float64) {
	shardsTotal.Set(count)
}

// SetShardsHealthy updates the shards_healthy gauge
func SetShardsHealthy(count float64) {
	shardsHealthy.Set(count)
}

// SetRegionsTotal updates the regions_total gauge
func SetRegionsTotal(count float64) {
	regionsTotal.Set(count)
}

// IncRegionAllocations increments the region_allocations counter for a shard
func IncRegionAllocations(shardID string) {
	regionAllocations.WithLabelValues(shardID).Inc()
}

// ObserveGRPCRequest records a gRPC request with method, status, and duration
func ObserveGRPCRequest(method, status string, duration time.Duration) {
	grpcRequestsTotal.WithLabelValues(method, status).Inc()
	grpcRequestDuration.WithLabelValues(method).Observe(duration.Seconds())
}

// IncPlayerRoutes increments the player_routes counter for a shard
func IncPlayerRoutes(shardID string) {
	playerRoutesTotal.WithLabelValues(shardID).Inc()
}

// Handler returns an HTTP handler for the /metrics endpoint
func Handler() http.Handler {
	return promhttp.Handler()
}
