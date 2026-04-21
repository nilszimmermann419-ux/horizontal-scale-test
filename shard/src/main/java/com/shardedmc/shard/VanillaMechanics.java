package com.shardedmc.shard;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.timer.SchedulerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class VanillaMechanics {
    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaMechanics.class);
    private static final long DAY_LENGTH = 24000; // 20 minutes in ticks
    private static final long TICK_RATE = 50; // 50ms per tick (20 TPS)
    
    // Weather constants
    private static final long WEATHER_CHANGE_INTERVAL = 600000; // 10 minutes
    private static final double RAIN_CHANCE = 0.3;
    private static final double THUNDER_CHANCE = 0.1;

    private final Instance instance;
    private final SchedulerManager scheduler;
    private final Random random;
    
    private long time = 0;
    private Weather currentWeather = Weather.CLEAR;
    private long weatherDuration = 0;
    private boolean doDaylightCycle = true;
    private boolean doWeatherCycle = true;
    private Difficulty difficulty = Difficulty.NORMAL;
    private GameMode defaultGameMode = GameMode.SURVIVAL;
    
    private long lastWeatherChange = 0;

    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }

    public enum Difficulty {
        PEACEFUL,
        EASY,
        NORMAL,
        HARD
    }

    public VanillaMechanics(Instance instance) {
        this.instance = instance;
        this.scheduler = MinecraftServer.getSchedulerManager();
        this.random = new Random();
        
        registerEventHandlers();
        startTickLoop();
        
        LOGGER.info("Initialized vanilla mechanics");
    }

    private void registerEventHandlers() {
        GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();
        
        // Block break - drop items
        eventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            Block block = event.getBlock();
            Pos pos = event.getBlockPosition().asVec().asPosition();
            
            // Don't drop blocks in creative mode
            if (player.getGameMode() == GameMode.CREATIVE) {
                return;
            }
            
            ItemStack drop = getBlockDrop(block);
            if (drop != null) {
                dropItem(drop, pos.add(0.5, 0.5, 0.5));
            }
        });
        
        // Item drop event
        eventHandler.addListener(ItemDropEvent.class, event -> {
            Player player = event.getPlayer();
            ItemStack itemStack = event.getItemStack();
            Pos pos = player.getPosition();
            
            ItemEntity itemEntity = new ItemEntity(itemStack);
            itemEntity.setInstance(instance, pos.add(0, 1, 0));
            itemEntity.setVelocity(pos.direction().mul(6));
        });
        
        // Gravity / fall damage
        eventHandler.addListener(PlayerMoveEvent.class, this::handlePlayerMovement);
        
        // Entity attack
        eventHandler.addListener(EntityAttackEvent.class, event -> {
            if (event.getTarget() instanceof Player target) {
                double damage = calculateDamage(event.getEntity().getEntityType());
                target.damage(net.minestom.server.event.EventDispatcher.call(
                    new net.minestom.server.event.entity.EntityDamageEvent(
                        target, 
                        net.minestom.server.event.entity.EntityDamageEvent.DamageType.ENTITY_ATTACK, 
                        (float) damage
                    )
                ).getDamage());
            }
        });
    }

    private void startTickLoop() {
        scheduler.buildTask(this::tick)
                .repeat(java.time.Duration.ofMillis(TICK_RATE))
                .schedule();
    }

    public void tick() {
        // Day/night cycle
        if (doDaylightCycle) {
            time = (time + 1) % DAY_LENGTH;
            instance.setTime(time);
        }
        
        // Weather cycle
        if (doWeatherCycle) {
            updateWeather();
        }
        
        // Entity spawning
        if (time % 400 == 0) { // Every 20 seconds
            spawnEntities();
        }
    }

    private void updateWeather() {
        if (currentWeather == Weather.CLEAR) {
            if (System.currentTimeMillis() - lastWeatherChange > WEATHER_CHANGE_INTERVAL) {
                double roll = random.nextDouble();
                if (roll < THUNDER_CHANCE) {
                    setWeather(Weather.THUNDER);
                } else if (roll < RAIN_CHANCE + THUNDER_CHANCE) {
                    setWeather(Weather.RAIN);
                }
                lastWeatherChange = System.currentTimeMillis();
            }
        } else {
            // Weather is active, check if it should end
            weatherDuration--;
            if (weatherDuration <= 0) {
                setWeather(Weather.CLEAR);
                lastWeatherChange = System.currentTimeMillis();
            }
        }
    }

    private void handlePlayerMovement(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Pos newPos = event.getNewPosition();
        Pos oldPos = event.getPlayer().getPosition();
        
        // Check for fall damage
        double fallDistance = oldPos.y() - newPos.y();
        if (fallDistance > 3.0) {
            // Simple fall damage: 1 heart per block over 3
            int damage = (int) (fallDistance - 3.0);
            if (damage > 0 && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.damage(net.minestom.server.event.EventDispatcher.call(
                    new net.minestom.server.event.entity.EntityDamageEvent(
                        player,
                        net.minestom.server.event.entity.EntityDamageEvent.DamageType.FALL,
                        (float) damage
                    )
                ).getDamage());
            }
        }
    }

    private void spawnEntities() {
        if (instance.getPlayers().isEmpty()) return;
        
        // Spawn mobs around players
        for (Player player : instance.getPlayers()) {
            if (random.nextDouble() > 0.1) continue; // 10% chance per player
            
            Pos playerPos = player.getPosition();
            int spawnX = (int) (playerPos.x() + random.nextInt(48) - 24);
            int spawnZ = (int) (playerPos.z() + random.nextInt(48) - 24);
            
            // Simple mob spawning based on light level and time
            boolean isNight = time > 12000;
            
            if (isNight && difficulty != Difficulty.PEACEFUL) {
                // Spawn hostile mobs
                spawnMob(spawnX, spawnZ, getRandomHostileMob());
            } else {
                // Spawn passive mobs during day
                if (random.nextBoolean()) {
                    spawnMob(spawnX, spawnZ, getRandomPassiveMob());
                }
            }
        }
    }

    private void spawnMob(int x, int z, EntityType type) {
        try {
            var entity = new net.minestom.server.entity.Entity(type);
            Pos pos = new Pos(x, 100, z); // Will fall to ground
            entity.setInstance(instance, pos);
        } catch (Exception e) {
            LOGGER.debug("Failed to spawn mob {} at {}, {}", type, x, z);
        }
    }

    private EntityType getRandomHostileMob() {
        EntityType[] hostile = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.ENDERMAN
        };
        return hostile[random.nextInt(hostile.length)];
    }

    private EntityType getRandomPassiveMob() {
        EntityType[] passive = {
            EntityType.SHEEP, EntityType.COW, EntityType.PIG,
            EntityType.CHICKEN, EntityType.RABBIT
        };
        return passive[random.nextInt(passive.length)];
    }

    private ItemStack getBlockDrop(Block block) {
        return switch (block) {
            case Block.GRASS_BLOCK -> ItemStack.of(net.minestom.server.item.Material.DIRT);
            case Block.DIRT -> ItemStack.of(net.minestom.server.item.Material.DIRT);
            case Block.STONE -> ItemStack.of(net.minestom.server.item.Material.COBBLESTONE);
            case Block.COBBLESTONE -> ItemStack.of(net.minestom.server.item.Material.COBBLESTONE);
            case Block.OAK_LOG -> ItemStack.of(net.minestom.server.item.Material.OAK_LOG);
            case Block.OAK_LEAVES -> random.nextInt(10) == 0 ? 
                ItemStack.of(net.minestom.server.item.Material.OAK_SAPLING) : null;
            case Block.DIAMOND_ORE -> ItemStack.of(net.minestom.server.item.Material.DIAMOND);
            case Block.IRON_ORE -> ItemStack.of(net.minestom.server.item.Material.RAW_IRON);
            case Block.GOLD_ORE -> ItemStack.of(net.minestom.server.item.Material.RAW_GOLD);
            case Block.COAL_ORE -> ItemStack.of(net.minestom.server.item.Material.COAL);
            case Block.SAND -> ItemStack.of(net.minestom.server.item.Material.SAND);
            case Block.GRAVEL -> ItemStack.of(net.minestom.server.item.Material.GRAVEL);
            default -> null;
        };
    }

    private void dropItem(ItemStack item, Pos pos) {
        ItemEntity itemEntity = new ItemEntity(item);
        itemEntity.setInstance(instance, pos);
        itemEntity.setVelocity(new net.minestom.server.coordinate.Vec(
            ThreadLocalRandom.current().nextDouble() - 0.5,
            0.3,
            ThreadLocalRandom.current().nextDouble() - 0.5
        ));
    }

    private double calculateDamage(EntityType entityType) {
        return switch (entityType) {
            case EntityType.ZOMBIE -> 3.0;
            case EntityType.SKELETON -> 2.0;
            case EntityType.CREEPER -> 6.0;
            case EntityType.SPIDER -> 2.0;
            case EntityType.ENDERMAN -> 5.0;
            default -> 1.0;
        };
    }

    public void setTime(long time) {
        this.time = time % DAY_LENGTH;
        instance.setTime(this.time);
    }

    public long getTime() {
        return time;
    }

    public void setWeather(Weather weather) {
        this.currentWeather = weather;
        this.weatherDuration = weather == Weather.CLEAR ? 0 : 6000 + random.nextInt(12000); // 5-15 minutes
        
        // Apply weather effects to all players
        switch (weather) {
            case CLEAR -> {
                instance.setRain(false);
                instance.setThunder(false);
            }
            case RAIN -> {
                instance.setRain(true);
                instance.setThunder(false);
            }
            case THUNDER -> {
                instance.setRain(true);
                instance.setThunder(true);
            }
        }
        
        LOGGER.info("Weather changed to {}", weather);
    }

    public Weather getWeather() {
        return currentWeather;
    }

    public void setDoDaylightCycle(boolean doDaylightCycle) {
        this.doDaylightCycle = doDaylightCycle;
    }

    public void setDoWeatherCycle(boolean doWeatherCycle) {
        this.doWeatherCycle = doWeatherCycle;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDefaultGameMode(GameMode gameMode) {
        this.defaultGameMode = gameMode;
    }

    public GameMode getDefaultGameMode() {
        return defaultGameMode;
    }
}
