package com.shardedmc.api;

import com.shardedmc.api.events.BlockBreakEvent;
import com.shardedmc.api.events.PlayerJoinEvent;
import com.shardedmc.shared.Vec3d;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PluginApiTest {
    
    @Test
    void eventCreation() {
        UUID playerId = UUID.randomUUID();
        PlayerJoinEvent event = new PlayerJoinEvent(playerId, "TestPlayer", new Vec3d(0, 64, 0));
        
        assertEquals(playerId, event.getPlayerId());
        assertEquals("TestPlayer", event.getUsername());
        assertTrue(event.isGlobal());
        assertNotNull(event.getEventId());
    }
    
    @Test
    void blockBreakEventCancellation() {
        UUID playerId = UUID.randomUUID();
        BlockBreakEvent event = new BlockBreakEvent(playerId, new Vec3d(10, 64, 10), Block.STONE);
        
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertFalse(event.isGlobal());
    }
    
    @Test
    void pluginInfo() {
        PluginInfo info = new PluginInfo("TestPlugin", "1.0.0", "TestAuthor");
        
        assertEquals("TestPlugin", info.name());
        assertEquals("1.0.0", info.version());
        assertEquals("TestAuthor", info.author());
        assertTrue(info.isValid());
    }
    
    @Test
    void vec3dOperations() {
        Vec3d a = new Vec3d(1, 2, 3);
        Vec3d b = new Vec3d(4, 5, 6);
        
        Vec3d added = a.add(b);
        assertEquals(5, added.x());
        assertEquals(7, added.y());
        assertEquals(9, added.z());
        
        double dist = a.distance(b);
        assertEquals(Math.sqrt(27), dist, 0.001);
    }
}
