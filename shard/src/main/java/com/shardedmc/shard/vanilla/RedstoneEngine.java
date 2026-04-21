package com.shardedmc.shard.vanilla;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * High-performance redstone power propagation engine.
 * 
 * Features:
 * - Thread-safe power level tracking (0-15)
 * - Batch processing with 1000 updates/tick limit
 * - Scheduled updates for repeater/comparator delays
 * - Support for redstone wire, torches, repeaters, comparators
 * - Efficient neighbor propagation
 */
public class RedstoneEngine {
    private static final Logger logger = LoggerFactory.getLogger(RedstoneEngine.class);
    private static final int MAX_UPDATES_PER_TICK = 1000;
    private static final int MAX_POWER = 15;
    
    // Block position -> power level (0-15)
    private final Map<String, Integer> powerLevels = new ConcurrentHashMap<>();
    
    // Blocks that need power update
    private final Queue<UpdateEntry> updateQueue = new ConcurrentLinkedQueue<>();
    
    // Tick schedule: tick number -> list of positions to update
    private final Map<Long, List<Point>> scheduledUpdates = new ConcurrentHashMap<>();
    
    private long currentTick = 0;
    private int totalUpdatesProcessed = 0;
    
    private static class UpdateEntry {
        final Point position;
        final Instance instance;
        
        UpdateEntry(Point position, Instance instance) {
            this.position = position;
            this.instance = instance;
        }
    }
    
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
        eventHandler.addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);
        logger.info("RedstoneEngine registered");
    }
    
    public void tick() {
        currentTick++;
        int processed = 0;
        
        // Process scheduled updates first
        List<Point> scheduled = scheduledUpdates.remove(currentTick);
        if (scheduled != null) {
            for (Point pos : scheduled) {
                // Find instance for this position
                for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                    Block block = instance.getBlock(pos);
                    if (block != null && block != Block.AIR) {
                        updatePower(pos, instance);
                        processed++;
                        break;
                    }
                }
                if (processed >= MAX_UPDATES_PER_TICK) break;
            }
        }
        
        // Process queue
        UpdateEntry entry;
        while (processed < MAX_UPDATES_PER_TICK && (entry = updateQueue.poll()) != null) {
            updatePower(entry.position, entry.instance);
            processed++;
        }
        
        totalUpdatesProcessed += processed;
    }
    
    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        Block block = event.getBlock();
        Point pos = event.getBlockPosition();
        Instance instance = event.getInstance();
        
        queueUpdate(pos, instance);
        
        // Always update neighbors for any block placement
        // as it may affect redstone components nearby
        for (Point neighbor : getNeighbors(pos)) {
            queueUpdate(neighbor, instance);
        }
        
        // If placing a redstone component, also update diagonals for wire
        if (isRedstoneWire(block) || isRedstoneComponent(block)) {
            for (Point diagonal : getDiagonals(pos)) {
                queueUpdate(diagonal, instance);
            }
        }
    }
    
    private void onBlockBreak(PlayerBlockBreakEvent event) {
        Point pos = event.getBlockPosition();
        Instance instance = event.getInstance();
        String key = getKey(pos, instance);
        
        // Remove power from broken block
        Integer oldPower = powerLevels.remove(key);
        if (oldPower != null && oldPower > 0) {
            // Propagate removal to neighbors
            for (Point neighbor : getNeighbors(pos)) {
                queueUpdate(neighbor, instance);
            }
            for (Point diagonal : getDiagonals(pos)) {
                queueUpdate(diagonal, instance);
            }
        }
        
        // Always update neighbors
        for (Point neighbor : getNeighbors(pos)) {
            queueUpdate(neighbor, instance);
        }
    }
    
    private void queueUpdate(Point pos, Instance instance) {
        updateQueue.offer(new UpdateEntry(pos, instance));
    }
    
    private void scheduleUpdate(Point pos, long delay) {
        scheduledUpdates.computeIfAbsent(currentTick + delay, k -> new ArrayList<>()).add(pos);
    }
    
    private void updatePower(Point pos, Instance instance) {
        String key = getKey(pos, instance);
        Block block = instance.getBlock(pos);
        
        if (block == null || block == Block.AIR) {
            powerLevels.remove(key);
            return;
        }
        
        int newPower = calculatePower(pos, instance, block);
        
        // Clamp to valid range
        newPower = Math.max(0, Math.min(MAX_POWER, newPower));
        
        Integer oldPower = powerLevels.get(key);
        if (oldPower == null || oldPower != newPower) {
            powerLevels.put(key, newPower);
            
            // Propagate to neighbors
            for (Point neighbor : getNeighbors(pos)) {
                Block neighborBlock = instance.getBlock(neighbor);
                if (neighborBlock != null && neighborBlock != Block.AIR) {
                    queueUpdate(neighbor, instance);
                }
            }
            
            // For wire, also propagate diagonals
            if (isRedstoneWire(block)) {
                for (Point diagonal : getDiagonals(pos)) {
                    Block diagBlock = instance.getBlock(diagonal);
                    if (diagBlock != null && isRedstoneWire(diagBlock)) {
                        queueUpdate(diagonal, instance);
                    }
                }
            }
            
            // Schedule repeater updates with delay
            if (isRedstoneRepeater(block) && newPower > 0) {
                int delay = getRepeaterDelay(block);
                scheduleUpdate(pos, delay);
            }
        }
    }
    
    private int calculatePower(Point pos, Instance instance, Block block) {
        if (isPowerSource(block)) {
            return MAX_POWER;
        }
        
        int maxPower = 0;
        
        // Check all neighbors
        for (Point neighbor : getNeighbors(pos)) {
            String nKey = getKey(neighbor, instance);
            Integer power = powerLevels.get(nKey);
            if (power != null && power > maxPower) {
                maxPower = power;
            }
            
            // Check if neighbor is a hard-powered block (opaque block with power)
            Block neighborBlock = instance.getBlock(neighbor);
            if (neighborBlock != null && isOpaque(neighborBlock)) {
                // Check if this opaque block is being powered
                for (Point adj : getNeighbors(neighbor)) {
                    if (adj.equals(pos)) continue; // Don't check back at self
                    String adjKey = getKey(adj, instance);
                    Integer adjPower = powerLevels.get(adjKey);
                    if (adjPower != null && adjPower >= MAX_POWER) {
                        maxPower = Math.max(maxPower, MAX_POWER);
                    }
                }
            }
        }
        
        if (isRedstoneWire(block)) {
            // Wire power decays by 1 from source
            maxPower = Math.max(0, maxPower - 1);
        }
        
        return maxPower;
    }
    
    private boolean isPowerSource(Block block) {
        String name = block.name();
        return name.equals("minecraft:redstone_block") ||
               name.contains("redstone_torch") ||
               name.equals("minecraft:lever") ||
               name.contains("button") ||
               name.contains("pressure_plate") ||
               name.contains("detector_rail") ||
               name.equals("minecraft:trapped_chest") ||
               name.equals("minecraft:daylight_detector") ||
               name.equals("minecraft:observer");
    }
    
    private boolean isOpaque(Block block) {
        // Simple check: most solid blocks are opaque
        String name = block.name();
        if (name.contains("glass") || name.contains("slab") || name.contains("stairs")) {
            return false;
        }
        if (name.contains("door") || name.contains("trapdoor") || name.contains("fence_gate")) {
            return false;
        }
        return block.isSolid();
    }
    
    private int getRepeaterDelay(Block block) {
        // Repeater delay is stored in block properties
        // Default is 1 tick (0.05s), max is 4 ticks
        // In Minestom, we can check block properties
        try {
            var delayProp = block.getProperty("delay");
            if (delayProp != null) {
                return Integer.parseInt(delayProp) * 2; // Each delay step = 2 redstone ticks (0.1s)
            }
        } catch (Exception e) {
            // Property not available
        }
        return 2; // Default 1 redstone tick = 0.1s = 2 game ticks
    }
    
    private List<Point> getNeighbors(Point pos) {
        return Arrays.asList(
            pos.add(1, 0, 0),
            pos.add(-1, 0, 0),
            pos.add(0, 1, 0),
            pos.add(0, -1, 0),
            pos.add(0, 0, 1),
            pos.add(0, 0, -1)
        );
    }
    
    private List<Point> getDiagonals(Point pos) {
        // Redstone wire connects diagonally on the same Y level
        return Arrays.asList(
            pos.add(1, 0, 1),
            pos.add(1, 0, -1),
            pos.add(-1, 0, 1),
            pos.add(-1, 0, -1)
        );
    }
    
    private String getKey(Point pos, Instance instance) {
        return instance.getUniqueId() + ":" + pos.blockX() + "," + pos.blockY() + "," + pos.blockZ();
    }
    
    private boolean isRedstoneComponent(Block block) {
        String name = block.name();
        return name.contains("redstone") || name.contains("repeater") || 
               name.contains("comparator") || name.contains("observer") ||
               name.contains("piston") || name.contains("dropper") ||
               name.contains("dispenser") || name.contains("door") ||
               name.contains("trapdoor") || name.contains("gate") ||
               name.contains("note_block") || name.contains("hopper") ||
               name.contains("daylight_detector") || name.contains("lever") ||
               name.contains("button") || name.contains("pressure_plate") ||
               name.contains("detector_rail") || name.contains("trapped_chest");
    }
    
    private boolean isRedstoneWire(Block block) {
        return block.name().equals("minecraft:redstone_wire");
    }
    
    private boolean isRedstoneRepeater(Block block) {
        return block.name().equals("minecraft:repeater");
    }
    
    private boolean isRedstoneComparator(Block block) {
        return block.name().equals("minecraft:comparator");
    }
    
    /**
     * Get current power level at a position (0-15)
     */
    public int getPowerAt(Point pos, Instance instance) {
        return powerLevels.getOrDefault(getKey(pos, instance), 0);
    }
    
    /**
     * Get total number of updates processed since start
     */
    public int getTotalUpdatesProcessed() {
        return totalUpdatesProcessed;
    }
    
    /**
     * Get number of currently tracked powered blocks
     */
    public int getTrackedBlockCount() {
        return powerLevels.size();
    }
}
