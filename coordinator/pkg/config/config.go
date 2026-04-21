package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config holds all coordinator configuration settings
type Config struct {
	Port             string        `json:"port"`
	HTTPPort         string        `json:"http_port"`
	GRPCPort         string        `json:"grpc_port"`
	NATSURL          string        `json:"nats_url"`
	HeartbeatTimeout time.Duration `json:"heartbeat_timeout"`
	CheckInterval    time.Duration `json:"check_interval"`
	MaxShards        int           `json:"max_shards"`
	EnableTLS        bool          `json:"enable_tls"`
	TLSCert          string        `json:"tls_cert"`
	TLSKey           string        `json:"tls_key"`
	ReadTimeout      time.Duration `json:"read_timeout"`
	WriteTimeout     time.Duration `json:"write_timeout"`
	ShutdownTimeout  time.Duration `json:"shutdown_timeout"`
}

// Load creates a Config from environment variables with sensible defaults
func Load() (*Config, error) {
	cfg := &Config{
		Port:             getEnv("PORT", "8080"),
		HTTPPort:         getEnv("HTTP_PORT", "8080"),
		GRPCPort:         getEnv("GRPC_PORT", "50051"),
		NATSURL:          getEnv("NATS_URL", "nats://localhost:4222"),
		HeartbeatTimeout: getDurationEnv("HEARTBEAT_TIMEOUT", 30*time.Second),
		CheckInterval:    getDurationEnv("CHECK_INTERVAL", 10*time.Second),
		MaxShards:        getIntEnv("MAX_SHARDS", 100),
		EnableTLS:        getBoolEnv("ENABLE_TLS", false),
		TLSCert:          getEnv("TLS_CERT", ""),
		TLSKey:           getEnv("TLS_KEY", ""),
		ReadTimeout:      getDurationEnv("READ_TIMEOUT", 15*time.Second),
		WriteTimeout:     getDurationEnv("WRITE_TIMEOUT", 15*time.Second),
		ShutdownTimeout:  getDurationEnv("SHUTDOWN_TIMEOUT", 30*time.Second),
	}

	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

// Validate checks that all required fields are present and within valid ranges
func (c *Config) Validate() error {
	var errs []string

	if c.Port == "" {
		errs = append(errs, "PORT is required")
	}
	if c.HTTPPort == "" {
		errs = append(errs, "HTTP_PORT is required")
	}
	if c.GRPCPort == "" {
		errs = append(errs, "GRPC_PORT is required")
	}
	if c.NATSURL == "" {
		errs = append(errs, "NATS_URL is required")
	}
	if c.HeartbeatTimeout <= 0 {
		errs = append(errs, "HEARTBEAT_TIMEOUT must be positive")
	}
	if c.CheckInterval <= 0 {
		errs = append(errs, "CHECK_INTERVAL must be positive")
	}
	if c.MaxShards <= 0 {
		errs = append(errs, "MAX_SHARDS must be positive")
	}
	if c.ReadTimeout <= 0 {
		errs = append(errs, "READ_TIMEOUT must be positive")
	}
	if c.WriteTimeout <= 0 {
		errs = append(errs, "WRITE_TIMEOUT must be positive")
	}
	if c.ShutdownTimeout <= 0 {
		errs = append(errs, "SHUTDOWN_TIMEOUT must be positive")
	}
	if c.EnableTLS {
		if c.TLSCert == "" {
			errs = append(errs, "TLS_CERT is required when ENABLE_TLS is true")
		}
		if c.TLSKey == "" {
			errs = append(errs, "TLS_KEY is required when ENABLE_TLS is true")
		}
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}

	return nil
}

// String returns a sanitized string representation of the config
func (c *Config) String() string {
	return fmt.Sprintf(
		"Config{HTTPPort=%s GRPCPort=%s NATSURL=%s HeartbeatTimeout=%s CheckInterval=%s MaxShards=%d EnableTLS=%v}",
		c.HTTPPort, c.GRPCPort, c.NATSURL, c.HeartbeatTimeout, c.CheckInterval, c.MaxShards, c.EnableTLS,
	)
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getIntEnv(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if i, err := strconv.Atoi(value); err == nil {
			return i
		}
	}
	return defaultValue
}

func getDurationEnv(key string, defaultValue time.Duration) time.Duration {
	if value := os.Getenv(key); value != "" {
		if seconds, err := strconv.Atoi(value); err == nil {
			return time.Duration(seconds) * time.Second
		}
	}
	return defaultValue
}

func getBoolEnv(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if b, err := strconv.ParseBool(value); err == nil {
			return b
		}
	}
	return defaultValue
}
