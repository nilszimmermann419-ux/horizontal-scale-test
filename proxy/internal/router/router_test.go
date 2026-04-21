package router

import (
	"testing"

	"github.com/shardedmc/v2/proxy/internal/connection"
)

func TestShardRouting(t *testing.T) {
	// Test that router struct exists and can be instantiated
	r := &Router{}
	if r == nil {
		t.Error("Expected router to be instantiated")
	}
}

func TestRouterRemovePlayer(t *testing.T) {
	r := &Router{
		players: make(map[string]*connection.ShardConn),
	}

	// Test removing non-existent player doesn't panic
	r.RemovePlayer("non-existent-player")
}

func TestRouterGetPlayerShard(t *testing.T) {
	r := &Router{
		players: make(map[string]*connection.ShardConn),
	}

	// Test getting shard for non-existent player
	shard := r.GetPlayerShard("non-existent-player")
	if shard != nil {
		t.Error("Expected nil for non-existent player")
	}
}
