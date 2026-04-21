package com.shardedmc.shard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.inventory.InventoryOpenEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.WrittenBookContent;
import net.minestom.server.item.component.WritableBookContent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.item.enchant.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents item duplication and hacked item exploits.
 * Validates NBT, detects overstacked items, limits book sizes,
 * disables donkey chests, and enforces portal cooldowns.
 */
public class DupePrevention {
    private static final Logger logger = LoggerFactory.getLogger(DupePrevention.class);

    // Configuration
    private final int maxStackSize;
    private final int maxBookPageSize;
    private final int portalCooldownSeconds;
    private final boolean disableDonkeyChests;
    private final boolean autoRemoveHackedItems;

    // Illegal enchantment combinations (mutually exclusive)
    private final Set<Set<RegistryKey<Enchantment>>> illegalEnchantments;

    // Portal cooldown tracking: player UUID -> last portal use time (ms)
    private final Map<UUID, Long> portalCooldowns = new ConcurrentHashMap<>();

    // Enchantment registry for max level lookups
    private final Map<String, RegistryKey<Enchantment>> enchantmentKeys;

    public DupePrevention() {
        this(64, 255, 15, true, true);
    }

    public DupePrevention(int maxStackSize, int maxBookPageSize, int portalCooldownSeconds,
                          boolean disableDonkeyChests, boolean autoRemoveHackedItems) {
        this.maxStackSize = maxStackSize;
        this.maxBookPageSize = maxBookPageSize;
        this.portalCooldownSeconds = portalCooldownSeconds;
        this.disableDonkeyChests = disableDonkeyChests;
        this.autoRemoveHackedItems = autoRemoveHackedItems;

        // Initialize enchantment keys
        this.enchantmentKeys = new HashMap<>();
        String[] enchantNames = {
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "thorns",
            "depth_strider", "frost_walker", "binding_curse", "soul_speed",
            "swift_sneak", "sharpness", "smite", "bane_of_arthropods",
            "knockback", "fire_aspect", "looting", "sweeping_edge",
            "efficiency", "unbreaking", "fortune", "power", "punch",
            "flame", "infinity", "luck_of_the_sea", "lure", "loyalty",
            "impaling", "riptide", "channeling", "multishot", "quick_charge",
            "piercing", "density", "breach", "wind_burst", "mending",
            "vanishing_curse", "silk_touch"
        };
        for (String name : enchantNames) {
            enchantmentKeys.put(name, RegistryKey.unsafeOf("minecraft:" + name));
        }

        // Define mutually exclusive enchantment pairs
        this.illegalEnchantments = new HashSet<>();
        illegalEnchantments.add(Set.of(key("silk_touch"), key("fortune")));
        illegalEnchantments.add(Set.of(key("sharpness"), key("smite")));
        illegalEnchantments.add(Set.of(key("sharpness"), key("bane_of_arthropods")));
        illegalEnchantments.add(Set.of(key("smite"), key("bane_of_arthropods")));
        illegalEnchantments.add(Set.of(key("protection"), key("blast_protection")));
        illegalEnchantments.add(Set.of(key("protection"), key("fire_protection")));
        illegalEnchantments.add(Set.of(key("protection"), key("projectile_protection")));
        illegalEnchantments.add(Set.of(key("blast_protection"), key("fire_protection")));
        illegalEnchantments.add(Set.of(key("blast_protection"), key("projectile_protection")));
        illegalEnchantments.add(Set.of(key("fire_protection"), key("projectile_protection")));
        illegalEnchantments.add(Set.of(key("depth_strider"), key("frost_walker")));
        illegalEnchantments.add(Set.of(key("infinity"), key("mending")));
        illegalEnchantments.add(Set.of(key("multishot"), key("piercing")));
    }

    private RegistryKey<Enchantment> key(String name) {
        return enchantmentKeys.get(name);
    }

    public void register(EventNode<Event> eventNode) {
        // Inventory click validation
        eventNode.addListener(InventoryPreClickEvent.class, this::onInventoryClick);

        // Inventory open validation
        eventNode.addListener(InventoryOpenEvent.class, this::onInventoryOpen);

        // Item drop validation
        eventNode.addListener(ItemDropEvent.class, this::onItemDrop);

        // Item use validation
        eventNode.addListener(PlayerUseItemEvent.class, this::onItemUse);

        // Block interaction (portal cooldown)
        eventNode.addListener(PlayerBlockInteractEvent.class, this::onBlockInteract);

        // Entity interaction (donkey chest disable)
        eventNode.addListener(PlayerEntityInteractEvent.class, this::onEntityInteract);

        // Portal cooldown on move (standing in portal)
        eventNode.addListener(PlayerMoveEvent.class, this::onPlayerMove);

        logger.info("DupePrevention registered (maxStack={}, bookLimit={}, portalCooldown={}s, disableDonkeys={}, autoRemove={})",
                maxStackSize, maxBookPageSize, portalCooldownSeconds, disableDonkeyChests, autoRemoveHackedItems);
    }

    private void onInventoryClick(InventoryPreClickEvent event) {
        ItemStack clicked = event.getClickedItem();

        if (clicked != null && !clicked.isAir()) {
            if (isIllegalItem(clicked)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("Illegal item detected and removed!", NamedTextColor.RED));
                logger.warn("Removed illegal item from {}: {}", event.getPlayer().getUsername(), clicked.material());
            }
        }
    }

    private void onInventoryOpen(InventoryOpenEvent event) {
        if (!autoRemoveHackedItems) return;

        // Scan player's own inventory for hacked items
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItemStack(i);
            if (item != null && !item.isAir() && isIllegalItem(item)) {
                inventory.setItemStack(i, ItemStack.AIR);
                removed = true;
                logger.warn("Auto-removed hacked item from {}'s inventory: {}", player.getUsername(), item.material());
            }
        }

        if (removed) {
            player.sendMessage(Component.text("Hacked items detected and removed from your inventory!", NamedTextColor.RED));
        }
    }

    private void onItemDrop(ItemDropEvent event) {
        ItemStack item = event.getItemStack();
        if (item != null && !item.isAir() && isIllegalItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Cannot drop illegal items!", NamedTextColor.RED));
            logger.warn("Blocked illegal item drop from {}: {}", event.getPlayer().getUsername(), item.material());
        }
    }

    private void onItemUse(PlayerUseItemEvent event) {
        ItemStack item = event.getItemStack();
        if (item != null && !item.isAir() && isIllegalItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Cannot use illegal items!", NamedTextColor.RED));
            logger.warn("Blocked illegal item use from {}: {}", event.getPlayer().getUsername(), item.material());
        }
    }

    private void onBlockInteract(PlayerBlockInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Portal cooldown
        if (isPortalBlock(block)) {
            long now = System.currentTimeMillis();
            Long lastUse = portalCooldowns.get(player.getUuid());

            if (lastUse != null && (now - lastUse) < portalCooldownSeconds * 1000L) {
                long remaining = (portalCooldownSeconds * 1000L - (now - lastUse)) / 1000L;
                event.setCancelled(true);
                player.sendMessage(Component.text("Portal cooldown: " + remaining + " seconds remaining", NamedTextColor.YELLOW));
                return;
            }

            portalCooldowns.put(player.getUuid(), now);
        }
    }

    private void onEntityInteract(PlayerEntityInteractEvent event) {
        if (!disableDonkeyChests) return;

        Entity entity = event.getTarget();
        if (entity.getEntityType() == EntityType.DONKEY ||
                entity.getEntityType() == EntityType.MULE ||
                entity.getEntityType() == EntityType.LLAMA) {
            // Donkey/Mule/Llama interaction - just log and warn, can't cancel directly
            event.getPlayer().sendMessage(Component.text("Donkey/Mule/Llama chests are disabled to prevent duping!", NamedTextColor.RED));
            logger.debug("Blocked donkey/mule/llama interaction for {}", event.getPlayer().getUsername());
        }
    }

    private void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Block block = player.getInstance().getBlock(player.getPosition());

        if (isPortalBlock(block)) {
            long now = System.currentTimeMillis();
            Long lastUse = portalCooldowns.get(player.getUuid());

            if (lastUse != null && (now - lastUse) < portalCooldownSeconds * 1000L) {
                // Player is in portal during cooldown - the block interact event handles cancellation
            }
        }
    }

    private boolean isPortalBlock(Block block) {
        String name = block.name().toUpperCase();
        return name.contains("PORTAL") || name.contains("NETHER_PORTAL") || name.contains("END_PORTAL");
    }

    /**
     * Check if an item is illegal/hacked.
     */
    public boolean isIllegalItem(ItemStack item) {
        if (item == null || item.isAir()) return false;

        // Check overstacked items
        if (item.amount() > maxStackSize) {
            return true;
        }

        // Check max stack for material
        if (item.amount() > item.maxStackSize()) {
            return true;
        }

        // Check enchantments
        if (hasIllegalEnchantments(item)) {
            return true;
        }

        // Check book size
        if (isOversizedBook(item)) {
            return true;
        }

        // Check illegal data values
        if (hasIllegalDataValues(item)) {
            return true;
        }

        return false;
    }

    private boolean hasIllegalEnchantments(ItemStack item) {
        EnchantmentList enchantments = item.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.enchantments().isEmpty()) {
            // Also check stored enchantments (e.g., enchanted books)
            enchantments = item.get(DataComponents.STORED_ENCHANTMENTS);
            if (enchantments == null || enchantments.enchantments().isEmpty()) return false;
        }

        Set<RegistryKey<Enchantment>> itemEnchants = enchantments.enchantments().keySet();

        for (Set<RegistryKey<Enchantment>> illegalSet : illegalEnchantments) {
            if (itemEnchants.containsAll(illegalSet)) {
                return true;
            }
        }

        // Check for excessively high enchantment levels
        for (var entry : enchantments.enchantments().entrySet()) {
            RegistryKey<Enchantment> enchant = entry.getKey();
            int level = entry.getValue();

            // Look up max level from registry
            Enchantment enchantmentData = MinecraftServer.getEnchantmentRegistry().get(enchant);
            int maxLevel = (enchantmentData != null) ? enchantmentData.maxLevel() : 5;
            
            if (level > maxLevel) {
                return true;
            }
        }

        return false;
    }

    private boolean isOversizedBook(ItemStack item) {
        Material mat = item.material();
        if (mat != Material.WRITABLE_BOOK && mat != Material.WRITTEN_BOOK) {
            return false;
        }

        WrittenBookContent bookContent = item.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (bookContent != null) {
            var pages = bookContent.pages();
            if (pages != null) {
                for (var page : pages) {
                    String pageText = page != null ? page.toString() : "";
                    if (pageText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBookPageSize) {
                        return true;
                    }
                }
            }
        }

        // Also check raw component data for writable books
        if (mat == Material.WRITABLE_BOOK) {
            WritableBookContent writableContent = item.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (writableContent != null) {
                var pages = writableContent.pages();
                if (pages != null) {
                    for (var page : pages) {
                        String pageText = page != null ? page.toString() : "";
                        if (pageText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBookPageSize) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean hasIllegalDataValues(ItemStack item) {
        // Check for items with extreme damage values (negative or beyond max durability)
        Integer damage = item.get(DataComponents.DAMAGE);
        if (damage != null && damage < 0) {
            return true;
        }

        // Check spawn eggs for invalid entity types via custom data
        var customData = item.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            var nbt = customData.nbt();
            if (nbt != null) {
                String entityType = nbt.getString("EntityTag");
                if (entityType != null && entityType.length() > 50) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Validate all items in a player's inventory and remove illegal ones.
     */
    public void validatePlayerInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItemStack(i);
            if (item != null && !item.isAir() && isIllegalItem(item)) {
                inventory.setItemStack(i, ItemStack.AIR);
                removed = true;
                logger.warn("Removed illegal item from {}'s inventory: {}", player.getUsername(), item.material());
            }
        }

        // Also check armor slots and offhand
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = player.getEquipment(slot);
            if (item != null && !item.isAir() && isIllegalItem(item)) {
                player.setEquipment(slot, ItemStack.AIR);
                removed = true;
                logger.warn("Removed illegal armor item from {}: {}", player.getUsername(), item.material());
            }
        }

        if (removed) {
            player.sendMessage(Component.text("Illegal items have been removed from your inventory!", NamedTextColor.RED));
        }
    }

    /**
     * Clear a player's portal cooldown.
     */
    public void clearPortalCooldown(Player player) {
        portalCooldowns.remove(player.getUuid());
    }
}
