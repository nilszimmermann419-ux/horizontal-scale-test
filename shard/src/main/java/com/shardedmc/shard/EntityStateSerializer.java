package com.shardedmc.shard;

import com.shardedmc.shared.EntityState;
import com.shardedmc.shared.PlayerState;
import com.shardedmc.shared.Vec3d;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class EntityStateSerializer {
    
    public static byte[] serializePlayer(Player player) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(baos);
            
            // UUID
            writer.writeLong(player.getUuid().getMostSignificantBits());
            writer.writeLong(player.getUuid().getLeastSignificantBits());
            
            // Username
            writer.writeUTF(player.getUsername());
            
            // Position
            var pos = player.getPosition();
            writer.writeDouble(pos.x());
            writer.writeDouble(pos.y());
            writer.writeDouble(pos.z());
            
            // Velocity
            var vel = player.getVelocity();
            writer.writeDouble(vel.x());
            writer.writeDouble(vel.y());
            writer.writeDouble(vel.z());
            
            // Health
            writer.writeFloat(player.getHealth());
            
            // Game mode
            writer.writeInt(player.getGameMode().ordinal());
            
            // Inventory (simplified - just serialize item count)
            var inventory = player.getInventory();
            writer.writeInt(inventory.getSize());
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItemStack(i);
                writer.writeBoolean(!item.isAir());
                if (!item.isAir()) {
                    writer.writeUTF(item.material().name());
                    writer.writeInt(item.amount());
                }
            }
            
            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize player", e);
        }
    }
    
    public static PlayerState deserializePlayer(byte[] data) {
        try {
            DataInputStream reader = new DataInputStream(new ByteArrayInputStream(data));
            
            UUID uuid = new UUID(reader.readLong(), reader.readLong());
            String username = reader.readUTF();
            
            Vec3d position = new Vec3d(reader.readDouble(), reader.readDouble(), reader.readDouble());
            Vec3d velocity = new Vec3d(reader.readDouble(), reader.readDouble(), reader.readDouble());
            
            double health = reader.readFloat();
            int gameMode = reader.readInt();
            
            // Skip inventory for now (simplified)
            int invSize = reader.readInt();
            for (int i = 0; i < invSize; i++) {
                if (reader.readBoolean()) {
                    reader.readUTF(); // material name
                    reader.readInt(); // amount
                }
            }
            
            return new PlayerState(uuid, username, position, velocity, health, new byte[0], gameMode, new byte[0]);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize player", e);
        }
    }
    
    public static byte[] serializeEntity(Entity entity) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(baos);
            
            writer.writeLong(entity.getUuid().getMostSignificantBits());
            writer.writeLong(entity.getUuid().getLeastSignificantBits());
            writer.writeUTF(entity.getEntityType().name());
            
            var pos = entity.getPosition();
            writer.writeDouble(pos.x());
            writer.writeDouble(pos.y());
            writer.writeDouble(pos.z());
            
            var vel = entity.getVelocity();
            writer.writeDouble(vel.x());
            writer.writeDouble(vel.y());
            writer.writeDouble(vel.z());
            
            if (entity instanceof EntityCreature creature) {
                writer.writeFloat(creature.getHealth());
            } else {
                writer.writeFloat(20.0f);
            }
            
            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize entity", e);
        }
    }
    
    public static EntityState deserializeEntity(byte[] data) {
        try {
            DataInputStream reader = new DataInputStream(new ByteArrayInputStream(data));
            
            UUID uuid = new UUID(reader.readLong(), reader.readLong());
            String type = reader.readUTF();
            
            Vec3d position = new Vec3d(reader.readDouble(), reader.readDouble(), reader.readDouble());
            Vec3d velocity = new Vec3d(reader.readDouble(), reader.readDouble(), reader.readDouble());
            double health = reader.readFloat();
            
            return new EntityState(uuid, type, position, velocity, health, new byte[0]);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize entity", e);
        }
    }
}
