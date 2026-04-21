package util

import (
	"hash/fnv"
	"strconv"
)

// HashString computes the FNV-1a 32-bit hash of a string.
func HashString(s string) uint32 {
	h := fnv.New32a()
	h.Write([]byte(s))
	return h.Sum32()
}

// HashRegion computes the FNV-1a 32-bit hash of a region coordinate.
func HashRegion(x, z int) uint32 {
	s := strconv.Itoa(x) + ":" + strconv.Itoa(z)
	return HashString(s)
}
