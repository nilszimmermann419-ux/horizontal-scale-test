package util

import (
	"sort"
	"strconv"
	"sync"
)

// ConsistentHashRing implements a consistent hash ring for distributing
// keys across nodes with minimal redistribution when nodes are added or removed.
type ConsistentHashRing struct {
	mu      sync.RWMutex
	nodes   map[string]struct{}
	replicas int
	ring    map[uint32]string
	sorted  []uint32
}

// NewConsistentHashRing creates a new consistent hash ring with the given
// number of virtual replicas per node.
func NewConsistentHashRing(replicas int) *ConsistentHashRing {
	if replicas <= 0 {
		replicas = 150
	}
	return &ConsistentHashRing{
		nodes:    make(map[string]struct{}),
		replicas: replicas,
		ring:     make(map[uint32]string),
		sorted:   make([]uint32, 0),
	}
}

// Add adds a node to the hash ring.
func (c *ConsistentHashRing) Add(node string) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if _, ok := c.nodes[node]; ok {
		return
	}

	c.nodes[node] = struct{}{}
	for i := 0; i < c.replicas; i++ {
		key := HashString(node + ":" + strconv.Itoa(i))
		c.ring[key] = node
		c.sorted = append(c.sorted, key)
	}

	sort.Slice(c.sorted, func(i, j int) bool {
		return c.sorted[i] < c.sorted[j]
	})
}

// Remove removes a node from the hash ring.
func (c *ConsistentHashRing) Remove(node string) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if _, ok := c.nodes[node]; !ok {
		return
	}

	delete(c.nodes, node)
	newSorted := make([]uint32, 0, len(c.sorted)-c.replicas)
	for _, key := range c.sorted {
		if c.ring[key] != node {
			newSorted = append(newSorted, key)
		} else {
			delete(c.ring, key)
		}
	}
	c.sorted = newSorted
}

// Get returns the node responsible for the given key.
func (c *ConsistentHashRing) Get(key string) string {
	c.mu.RLock()
	defer c.mu.RUnlock()

	if len(c.sorted) == 0 {
		return ""
	}

	hash := HashString(key)
	idx := sort.Search(len(c.sorted), func(i int) bool {
		return c.sorted[i] >= hash
	})

	if idx == len(c.sorted) {
		idx = 0
	}

	return c.ring[c.sorted[idx]]
}

// Nodes returns a copy of the current nodes in the ring.
func (c *ConsistentHashRing) Nodes() []string {
	c.mu.RLock()
	defer c.mu.RUnlock()

	nodes := make([]string, 0, len(c.nodes))
	for node := range c.nodes {
		nodes = append(nodes, node)
	}
	return nodes
}
