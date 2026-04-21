package auth

import (
	"crypto/md5"
	"fmt"
	"strings"
)

// GenerateOfflineUUID creates a deterministic UUID from a username for offline mode
// This follows the standard Minecraft offline mode UUID generation:
// UUID.nameUUIDFromBytes("OfflinePlayer:<username>".getBytes())
func GenerateOfflineUUID(username string) string {
	// Standard Minecraft offline UUID uses "OfflinePlayer:" prefix
	input := "OfflinePlayer:" + username
	hash := md5.Sum([]byte(input))

	// Set version (4) and variant (2) bits according to RFC 4122
	hash[6] = (hash[6] & 0x0F) | 0x30 // Version 3 (name-based)
	hash[8] = (hash[8] & 0x3F) | 0x80 // Variant 2

	return fmt.Sprintf("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
		hash[0], hash[1], hash[2], hash[3],
		hash[4], hash[5],
		hash[6], hash[7],
		hash[8], hash[9],
		hash[10], hash[11], hash[12], hash[13], hash[14], hash[15])
}

// IsOfflineMode checks if the proxy is running in offline mode
// This can be determined by an environment variable or configuration
func IsOfflineMode() bool {
	// Check environment variable
	// In production, this should be read from configuration
	return true // Default to offline mode for development/testing
}

// ValidateUsername performs basic username validation for offline mode
func ValidateUsername(username string) error {
	if len(username) == 0 {
		return fmt.Errorf("username cannot be empty")
	}
	if len(username) > 16 {
		return fmt.Errorf("username too long (max 16 characters)")
	}
	// Minecraft usernames only allow alphanumeric characters and underscores
	for _, r := range username {
		if !((r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '_') {
			return fmt.Errorf("invalid character in username: %c", r)
		}
	}
	return nil
}

// NormalizeUsername normalizes a username for offline mode
func NormalizeUsername(username string) string {
	return strings.ToLower(username)
}
