package com.shardedmc.api;

import net.minestom.server.item.ItemStack;

import java.util.concurrent.CompletableFuture;

public interface ShardedInventory {
    
    CompletableFuture<ItemStack> getItem(int slot);
    CompletableFuture<Void> setItem(int slot, ItemStack item);
    CompletableFuture<Integer> getSize();
    CompletableFuture<Void> clear();
    CompletableFuture<Boolean> isEmpty();
    CompletableFuture<Void> addItem(ItemStack item);
}
