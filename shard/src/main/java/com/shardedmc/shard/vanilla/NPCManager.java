package com.shardedmc.shard.vanilla;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.PlayerMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NPC Manager for spawning and managing fake player entities.
 * NPCs appear as real players to other players with custom names and head tracking.
 */
public class NPCManager {
    private static final Logger logger = LoggerFactory.getLogger(NPCManager.class);
    
    private final Map<Integer, NPC> npcs = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private boolean headTrackingEnabled = true;
    
    public NPCManager() {
        // Start head tracking task
        startHeadTrackingTask();
    }
    
    /**
     * Spawn an NPC at the given position
     */
    public NPC spawnNPC(Instance instance, Pos pos, String name) {
        return spawnNPC(instance, pos, name, null, null);
    }
    
    /**
     * Spawn an NPC at the given position with custom skin
     */
    public NPC spawnNPC(Instance instance, Pos pos, String name, String skinTexture, String skinSignature) {
        int id = nextId.getAndIncrement();
        
        // Create a fake player entity using PLAYER entity type
        // This will appear as a real player to other players
        Entity entity = new Entity(EntityType.PLAYER) {
            @Override
            public void update(long time) {
                super.update(time);
                // NPCs don't need player-specific updates
            }
        };
        
        // Set custom name visible above head
        entity.setCustomName(Component.text(name));
        entity.setCustomNameVisible(true);
        
        // Set up player metadata for skin
        PlayerMeta meta = (PlayerMeta) entity.getEntityMeta();
        if (skinTexture != null && skinSignature != null) {
            // Skin setting in Minestom requires proper profile setup
            // For now, we set the cape enabled bit which makes it look more like a player
            meta.setCapeEnabled(true);
        }
        
        // Make the NPC always look like it's not sleeping
        meta.setBedInWhichSleepingPosition(null);
        
        // Spawn the entity
        entity.setInstance(instance, pos);
        
        // Create NPC record
        NPC npc = new NPC(id, entity, name, instance, pos, skinTexture, skinSignature);
        npcs.put(id, npc);
        
        logger.info("Spawned NPC '{}' (ID: {}) at {} in {}", name, id, pos, instance.getUniqueId());
        return npc;
    }
    
    /**
     * Remove an NPC by ID
     */
    public boolean removeNPC(int id) {
        NPC npc = npcs.remove(id);
        if (npc != null) {
            npc.entity.remove();
            logger.info("Removed NPC '{}' (ID: {})", npc.name, id);
            return true;
        }
        return false;
    }
    
    /**
     * Remove an NPC by UUID
     */
    public boolean removeNPC(UUID uuid) {
        for (Map.Entry<Integer, NPC> entry : npcs.entrySet()) {
            if (entry.getValue().entity.getUuid().equals(uuid)) {
                return removeNPC(entry.getKey());
            }
        }
        return false;
    }
    
    /**
     * Get an NPC by ID
     */
    public NPC getNPC(int id) {
        return npcs.get(id);
    }
    
    /**
     * Get an NPC by UUID
     */
    public NPC getNPC(UUID uuid) {
        for (NPC npc : npcs.values()) {
            if (npc.entity.getUuid().equals(uuid)) {
                return npc;
            }
        }
        return null;
    }
    
    /**
     * Get all NPCs
     */
    public Collection<NPC> getAllNPCs() {
        return Collections.unmodifiableCollection(npcs.values());
    }
    
    /**
     * Get NPC count
     */
    public int getNPCCount() {
        return npcs.size();
    }
    
    /**
     * Make an NPC look at a specific position
     */
    public void lookAt(int npcId, Pos targetPos) {
        NPC npc = npcs.get(npcId);
        if (npc == null) return;
        
        Pos npcPos = npc.entity.getPosition();
        
        // Calculate yaw (horizontal rotation)
        double dx = targetPos.x() - npcPos.x();
        double dz = targetPos.z() - npcPos.z();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        
        // Calculate pitch (vertical rotation)
        double dy = targetPos.y() - (npcPos.y() + 1.62); // Eye level
        double distance = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distance));
        
        // Teleport to update rotation (smooth look would require packets)
        npc.entity.teleport(npcPos.withYaw(yaw).withPitch(pitch));
    }
    
    /**
     * Make an NPC look at a player
     */
    public void lookAtPlayer(int npcId, Player player) {
        lookAt(npcId, player.getPosition());
    }
    
    /**
     * Enable/disable head tracking
     */
    public void setHeadTrackingEnabled(boolean enabled) {
        this.headTrackingEnabled = enabled;
    }
    
    public boolean isHeadTrackingEnabled() {
        return headTrackingEnabled;
    }
    
    /**
     * Start the head tracking task that makes NPCs look at nearest players
     */
    private void startHeadTrackingTask() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!headTrackingEnabled) return;
            
            for (NPC npc : npcs.values()) {
                try {
                    Instance instance = npc.entity.getInstance();
                    if (instance == null) continue;
                    
                    // Find nearest player
                    Player nearestPlayer = null;
                    double nearestDist = Double.MAX_VALUE;
                    Pos npcPos = npc.entity.getPosition();
                    
                    for (Player player : instance.getPlayers()) {
                        double dist = player.getPosition().distance(npcPos);
                        if (dist < nearestDist && dist < 10.0) { // Only track within 10 blocks
                            nearestDist = dist;
                            nearestPlayer = player;
                        }
                    }
                    
                    if (nearestPlayer != null) {
                        lookAtPlayer(npc.id, nearestPlayer);
                    }
                } catch (Exception e) {
                    logger.error("Error in head tracking for NPC {}", npc.id, e);
                }
            }
        }).repeat(TaskSchedule.tick(5)).schedule(); // Update every 5 ticks (0.25s)
    }
    
    /**
     * Remove all NPCs in an instance
     */
    public void removeAllNPCs(Instance instance) {
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, NPC> entry : npcs.entrySet()) {
            if (entry.getValue().instance.equals(instance)) {
                toRemove.add(entry.getKey());
            }
        }
        for (int id : toRemove) {
            removeNPC(id);
        }
    }
    
    /**
     * Remove all NPCs
     */
    public void removeAllNPCs() {
        for (int id : new ArrayList<>(npcs.keySet())) {
            removeNPC(id);
        }
    }
    
    /**
     * Save all NPCs to file
     */
    public void saveNPCs(Path file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(npcs.size());
            
            for (NPC npc : npcs.values()) {
                out.writeInt(npc.id);
                out.writeUTF(npc.name);
                out.writeUTF(npc.instance.getUniqueId().toString());
                out.writeDouble(npc.spawnPos.x());
                out.writeDouble(npc.spawnPos.y());
                out.writeDouble(npc.spawnPos.z());
                out.writeFloat(npc.spawnPos.yaw());
                out.writeFloat(npc.spawnPos.pitch());
                out.writeBoolean(npc.skinTexture != null);
                if (npc.skinTexture != null) {
                    out.writeUTF(npc.skinTexture);
                    out.writeUTF(npc.skinSignature);
                }
            }
        }
    }
    
    /**
     * Load NPCs from file
     */
    public void loadNPCs(Path file) throws IOException {
        if (!Files.exists(file)) return;
        
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            int count = in.readInt();
            
            for (int i = 0; i < count; i++) {
                try {
                    int id = in.readInt();
                    String name = in.readUTF();
                    String instanceId = in.readUTF();
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();
                    float yaw = in.readFloat();
                    float pitch = in.readFloat();
                    boolean hasSkin = in.readBoolean();
                    String skinTexture = null;
                    String skinSignature = null;
                    if (hasSkin) {
                        skinTexture = in.readUTF();
                        skinSignature = in.readUTF();
                    }
                    
                    // Find instance
                    UUID targetUuid = UUID.fromString(instanceId);
                    Instance instance = MinecraftServer.getInstanceManager().getInstances().stream()
                        .filter(inst -> inst.getUniqueId().equals(targetUuid))
                        .findFirst()
                        .orElse(null);
                    
                    if (instance == null) continue;
                    
                    Pos pos = new Pos(x, y, z, yaw, pitch);
                    spawnNPC(instance, pos, name, skinTexture, skinSignature);
                    
                    // Update nextId if needed
                    if (id >= nextId.get()) {
                        nextId.set(id + 1);
                    }
                } catch (Exception e) {
                    logger.error("Error loading NPC", e);
                }
            }
        }
    }
    
    /**
     * NPC data class
     */
    public static class NPC {
        public final int id;
        public final Entity entity;
        public final String name;
        public final Instance instance;
        public final Pos spawnPos;
        public final String skinTexture;
        public final String skinSignature;
        
        NPC(int id, Entity entity, String name, Instance instance, Pos spawnPos, 
            String skinTexture, String skinSignature) {
            this.id = id;
            this.entity = entity;
            this.name = name;
            this.instance = instance;
            this.spawnPos = spawnPos;
            this.skinTexture = skinTexture;
            this.skinSignature = skinSignature;
        }
        
        public UUID getUuid() {
            return entity.getUuid();
        }
        
        public Pos getPosition() {
            return entity.getPosition();
        }
        
        public boolean isValid() {
            return !entity.isRemoved();
        }
    }
}
