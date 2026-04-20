package com.shardedmc.shard.vanilla;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedstoneEngine {
    private static final Logger logger = LoggerFactory.getLogger(RedstoneEngine.class);
    
    private final Map<String, Integer> powerLevels = new ConcurrentHashMap<>();
    private final Queue<Point> updateQueue = new ConcurrentLinkedQueue<>();
    private final Map<Long, List<Point>> scheduledUpdates = new ConcurrentHashMap<>();
    
    private long currentTick = 0;
    
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
    }
    
    public void tick() {
        currentTick++;
        
        List<Point> updates = scheduledUpdates.remove(currentTick);
        if (updates != null) {
            for (Point pos : updates) {
                updatePower(pos);
            }
        }
        
        Point pos;
        int processed = 0;
        while ((pos = updateQueue.poll()) != null && processed < 1000) {
            updatePower(pos);
            processed++;
        }
    }
    
    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        Block block = event.getBlock();
        Point pos = event.getBlockPosition();
        
        if (isRedstoneComponent(block)) {
            queueUpdate(pos);
            for (Point neighbor : getNeighbors(pos)) {
                queueUpdate(neighbor);
            }
        }
    }
    
    private void queueUpdate(Point pos) {
        updateQueue.offer(pos);
    }
    
    private void scheduleUpdate(Point pos, long delay) {
        scheduledUpdates.computeIfAbsent(currentTick + delay, k -> new ArrayList<>()).add(pos);
    }
    
    private void updatePower(Point pos) {
        String key = pos.blockX() + "," + pos.blockY() + "," + pos.blockZ();
        
        var instances = MinecraftServer.getInstanceManager().getInstances();
        if (instances.isEmpty()) return;
        
        Block block = instances.iterator().next().getBlock(pos);
        if (block == null || block == Block.AIR) return;
        
        int maxPower = calculateReceivedPower(pos, block);
        
        if (isRedstoneWire(block)) {
            maxPower = Math.max(0, maxPower - 1);
        }
        
        Integer currentPower = powerLevels.get(key);
        if (currentPower == null || currentPower != maxPower) {
            powerLevels.put(key, maxPower);
            
            for (Point neighbor : getNeighbors(pos)) {
                queueUpdate(neighbor);
            }
        }
    }
    
    private int calculateReceivedPower(Point pos, Block block) {
        int maxPower = 0;
        
        for (Point neighbor : getNeighbors(pos)) {
            String nKey = neighbor.blockX() + "," + neighbor.blockY() + "," + neighbor.blockZ();
            Integer power = powerLevels.get(nKey);
            if (power != null && power > maxPower) {
                maxPower = power;
            }
        }
        
        if (isRedstoneTorch(block)) {
            maxPower = 15;
        } else if (isRedstoneRepeater(block)) {
            maxPower = Math.max(maxPower, getRepeaterOutputPower(pos, block));
        } else if (isRedstoneComparator(block)) {
            maxPower = Math.max(maxPower, getComparatorOutputPower(pos, block));
        }
        
        return maxPower;
    }
    
    private int getRepeaterOutputPower(Point pos, Block block) {
        return powerLevels.getOrDefault(
            pos.blockX() + "," + pos.blockY() + "," + pos.blockZ(), 0);
    }
    
    private int getComparatorOutputPower(Point pos, Block block) {
        return powerLevels.getOrDefault(
            pos.blockX() + "," + pos.blockY() + "," + pos.blockZ(), 0);
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
    
    private boolean isRedstoneComponent(Block block) {
        String name = block.name();
        return name.contains("redstone") || name.contains("repeater") || 
               name.contains("comparator") || name.contains("observer") ||
               name.contains("piston") || name.contains("dropper") ||
               name.contains("dispenser") || name.contains("door") ||
               name.contains("trapdoor") || name.contains("gate") ||
               name.contains("note_block") || name.contains("hopper") ||
               name.contains("daylight_detector") || name.contains("lever") ||
               name.contains("button") || name.contains("pressure_plate");
    }
    
    private boolean isRedstoneWire(Block block) {
        return block.name().equals("minecraft:redstone_wire");
    }
    
    private boolean isRedstoneTorch(Block block) {
        return block.name().contains("redstone_torch") || block.name().contains("redstone_wall_torch");
    }
    
    private boolean isRedstoneRepeater(Block block) {
        return block.name().equals("minecraft:repeater");
    }
    
    private boolean isRedstoneComparator(Block block) {
        return block.name().equals("minecraft:comparator");
    }
}
