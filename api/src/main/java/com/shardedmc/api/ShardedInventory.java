package com.shardedmc.api;

import net.minestom.server.item.ItemStack;

import java.util.concurrent.CompletableFuture;

public interface ShardedInventory {

    /**
     * Gets the item in the specified slot.
     * @param slot the slot index, must be within bounds [0, size)
     * @return the item in the slot
     */
    CompletableFuture<ItemStack> getItem(int slot);

    /**
     * Sets the item in the specified slot.
     * @param slot the slot index, must be within bounds [0, size)
     * @param item the item to set
     */
    CompletableFuture<Void> setItem(int slot, ItemStack item);

    CompletableFuture<Integer> getSize();
    CompletableFuture<Void> clear();
    CompletableFuture<Boolean> isEmpty();
    CompletableFuture<Void> addItem(ItemStack item);
}
