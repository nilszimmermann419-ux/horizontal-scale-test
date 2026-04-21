package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config holds all proxy configuration settings
type Config struct {
	ListenAddr        string        `json:"listen_addr"`
	CoordinatorAddr   string        `json:"coordinator_addr"`
	MaxConnections    int           `json:"max_connections"`
	ReadTimeout       time.Duration `json:"read_timeout"`
	WriteTimeout      time.Duration `json:"write_timeout"`
	ShutdownTimeout   time.Duration `json:"shutdown_timeout"`
	ReconnectInterval time.Duration `json:"reconnect_interval"`
	ReconnectMaxRetries int         `json:"reconnect_max_retries"`
}

// Load creates a Config from environment variables with sensible defaults
func Load() (*Config, error) {
	cfg := &Config{
		ListenAddr:          getEnv("LISTEN_ADDR", ":25565"),
		CoordinatorAddr:     getEnv("COORDINATOR_ADDR", "localhost:50051"),
		MaxConnections:      getIntEnv("MAX_CONNECTIONS", 10000),
		ReadTimeout:         getDurationEnv("READ_TIMEOUT", 30*time.Second),
		WriteTimeout:        getDurationEnv("WRITE_TIMEOUT", 30*time.Second),
		ShutdownTimeout:     getDurationEnv("SHUTDOWN_TIMEOUT", 30*time.Second),
		ReconnectInterval:   getDurationEnv("RECONNECT_INTERVAL", 5*time.Second),
		ReconnectMaxRetries: getIntEnv("RECONNECT_MAX_RETRIES", -1),
	}

	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

// Validate checks that all required fields are present and within valid ranges
func (c *Config) Validate() error {
	var errs []string

	if c.ListenAddr == "" {
		errs = append(errs, "LISTEN_ADDR is required")
	}
	if c.CoordinatorAddr == "" {
		errs = append(errs, "COORDINATOR_ADDR is required")
	}
	if c.MaxConnections <= 0 {
		errs = append(errs, "MAX_CONNECTIONS must be positive")
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
	if c.ReconnectInterval <= 0 {
		errs = append(errs, "RECONNECT_INTERVAL must be positive")
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}

	return nil
}

// String returns a sanitized string representation of the config
func (c *Config) String() string {
	return fmt.Sprintf(
		"Config{ListenAddr=%s CoordinatorAddr=%s MaxConnections=%d ReadTimeout=%s WriteTimeout=%s}",
		c.ListenAddr, c.CoordinatorAddr, c.MaxConnections, c.ReadTimeout, c.WriteTimeout,
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
