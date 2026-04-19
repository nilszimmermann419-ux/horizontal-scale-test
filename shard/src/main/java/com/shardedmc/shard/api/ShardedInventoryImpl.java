package com.shardedmc.shard.api;

import com.shardedmc.api.ShardedInventory;
import net.minestom.server.item.ItemStack;

import java.util.concurrent.CompletableFuture;

public class ShardedInventoryImpl implements ShardedInventory {
    
    private final net.minestom.server.inventory.PlayerInventory inventory;
    
    public ShardedInventoryImpl(net.minestom.server.inventory.PlayerInventory inventory) {
        this.inventory = inventory;
    }
    
    @Override
    public CompletableFuture<ItemStack> getItem(int slot) {
        return CompletableFuture.completedFuture(inventory.getItemStack(slot));
    }
    
    @Override
    public CompletableFuture<Void> setItem(int slot, ItemStack item) {
        return CompletableFuture.runAsync(() -> inventory.setItemStack(slot, item));
    }
    
    @Override
    public CompletableFuture<Integer> getSize() {
        return CompletableFuture.completedFuture(inventory.getSize());
    }
    
    @Override
    public CompletableFuture<Void> clear() {
        return CompletableFuture.runAsync(() -> inventory.clear());
    }
    
    @Override
    public CompletableFuture<Boolean> isEmpty() {
        return CompletableFuture.supplyAsync(() -> {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (!inventory.getItemStack(i).isAir()) {
                    return false;
                }
            }
            return true;
        });
    }
    
    @Override
    public CompletableFuture<Void> addItem(ItemStack item) {
        return CompletableFuture.runAsync(() -> inventory.addItemStack(item));
    }
}
