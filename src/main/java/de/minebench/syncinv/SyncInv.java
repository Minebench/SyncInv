package de.minebench.syncinv;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.lishid.openinv.OpenInv;
import com.lishid.openinv.commands.OpenInvCommand;
import com.mojang.authlib.GameProfile;
import de.minebench.syncinv.listeners.MapCreationListener;
import de.minebench.syncinv.listeners.PlayerFreezeListener;
import de.minebench.syncinv.listeners.PlayerJoinListener;
import de.minebench.syncinv.listeners.PlayerQuitListener;
import de.minebench.syncinv.messenger.Message;
import de.minebench.syncinv.messenger.MessageType;
import de.minebench.syncinv.messenger.PlayerDataQuery;
import de.minebench.syncinv.messenger.RedisMessenger;
import de.minebench.syncinv.messenger.ServerMessenger;
import de.themoep.minedown.adventure.MineDown;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/*
 * SyncInv
 * Copyright (c) 2021 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

public final class SyncInv extends JavaPlugin {

    /**
     * Whether or not we should query the inventories from other servers
     * or just move players to the server that has the latest data
     */
    private boolean queryInventories;

    /**
     * Reference to the OpenInv plugin to load data for the query option
     */
    @Getter
    private OpenInv openInv = null;

    /**
     * The messenger for communications between the servers
     */
    @Getter
    private ServerMessenger messenger;

    /**
     * The cache for player data which should only get applied when the player is online
     */
    private Cache<UUID, Map.Entry<PlayerData, Runnable>> playerDataCache;

    /**
     * Sync data with all servers in a group when a player logs out
     */
    private boolean syncWithGroupOnLogout;
    
    /**
     * Store player data even if the player never joined the server
     */
    private boolean storeUnknownPlayers;

    /**
     * The amount of seconds we should wait for a query to stopTimeout
     */
    @Getter
    private int queryTimeout;

    /**
     * Should we apply data of queries that weren't answered by every server
     */
    private boolean applyTimedOutQueries;

    /**
     * What to sync
     */
    private EnumSet<SyncType> enabledSyncTypes;

    /**
     * Whether or not the plugin is currently disabling
     */
    @Getter
    private boolean disabling = false;

    /**
     * Whether or not the plugin is in debugging mode
     */
    @Getter
    private boolean debug;

    /**
     * The id of the newest map that was seen on this server
     */
    @Getter
    private int newestMap = 0;
    
    // Unknown player storing
    private Method methodGetOfflinePlayer = null;
    private Method methodGetHandle = null;
    private Method methodSetPositionRaw;
    private Field fieldYaw = null;
    private Field fieldPitch = null;
    private Method methodSetYaw = null;
    private Method methodSetPitch = null;

    // Offline player health setting
    private Method methodSetHealth;

    // Persistent data syncing
    private Method methodDeserializeCompound = null;
    private Method methodPdcSerialize = null;
    private Method methodGetRaw = null;
    private Method methodPutAll = null;

    // Map syncing
    private Field fieldWorldMap;
    private Field fieldMapColor;
    private Field fieldMapWorldId;

    private File playerDataFolder;

    @Override
    public void onEnable() {
        // Plugin startup logic
        loadConfig();
        
        playerDataFolder = new File(getServer().getWorlds().get(0).getWorldFolder(), "playerdata");
        try {
            methodGetOfflinePlayer = getServer().getClass().getMethod("getOfflinePlayer", GameProfile.class);
        } catch (NoSuchMethodException e) {
            if (storeUnknownPlayers) {
                getLogger().log(Level.WARNING, "Could not load method required to store unknown players. Disabling it!", e);
                storeUnknownPlayers = false;
            }
        }
        playerDataCache = CacheBuilder.newBuilder().expireAfterWrite(queryTimeout, TimeUnit.SECONDS).build();
        try {
            messenger = new RedisMessenger(this);
            messenger.hello();
        } catch (Exception e) {
            messenger = null;
        }

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerFreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new MapCreationListener(this), this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("syncinv").setExecutor(this);
        if (openInv != null) {
            OpenInvCommand openInvCommand = new OpenInvCommand(openInv);
            CommandExecutor forwarding = (sender, command, label, args) -> {
                if (sender instanceof Player && args.length > 0) {
                    if ("?".equalsIgnoreCase(args[0])) {
                        return openInvCommand.onCommand(sender, command, label, args);
                    }
                    Player player = getServer().getPlayer(args[0]);
                    if (player == null || !player.isOnline()) {
                        getServer().getScheduler().runTaskAsynchronously(this, () -> {
                            OfflinePlayer offlinePlayer = openInv.matchPlayer(args[0]);
                            if (offlinePlayer != null && (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline())) {
                                PlayerDataQuery q = getMessenger().queryData(offlinePlayer.getUniqueId(), (query) -> {
                                    if (getServer().getPlayer(query.getPlayerId()) != null) {
                                        openInvCommand.onCommand(sender, command, label, args);
                                        return;
                                    }
                                    getMessenger().removeQuery(query.getPlayerId());
                                    if (!((Player) sender).isOnline()) {
                                        return;
                                    }
                                    if (query.getYoungestServer() == null) {
                                        openInvCommand.onCommand(sender, command, label, args);
                                    } else {
                                        sender.sendMessage(getLang("stale-player-data", "server", query.getYoungestServer()));
                                        connectToServer(((Player) sender).getUniqueId(), query.getYoungestServer());
                                    }
                                });
                                if (q == null) {
                                    sender.sendMessage(getLang("query-error"));
                                }
                            } else {
                                sender.sendMessage(getLang("player-not-found"));
                            }
                        });
                        return true;
                    }
                }
                return openInvCommand.onCommand(sender, command, label, args);
            };
            getCommand("openinv").setExecutor(forwarding);
            getCommand("openender").setExecutor(forwarding);
        }
    }

    /**
     * Check if the plugin should sync a certain type
     * @param syncType The type to check
     * @return Whether or not it should be synced
     */
    public boolean shouldSync(SyncType syncType) {
        return enabledSyncTypes.contains(syncType);
    }

    /**
     * Check if the plugin should sync any of the provided types
     * @param syncTypes The types to check
     * @return Whether or not it should be synced
     */
    public boolean shouldSyncAny(SyncType... syncTypes) {
        for (SyncType syncType : syncTypes) {
            if (shouldSync(syncType)) {
                return true;
            }
        }
        return false;
    }

    private boolean disableSync(SyncType syncType) {
        return enabledSyncTypes.remove(syncType);
    }

    @Override
    public void onDisable() {
        disabling = true;
        if (getMessenger() != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                getMessenger().sendGroupMessage(new Message(getMessenger().getServerName(), MessageType.DATA, getData(player)), true);
            }
            getMessenger().goodbye();
        }
    }

    public void loadConfig() {
        reloadConfig(); // load working config

        // save newly added config options to disk
        getConfig().options().copyDefaults(true);
        saveConfig();

        debug = getConfig().getBoolean("debug");

        queryInventories = getConfig().getBoolean("query-inventories");

        syncWithGroupOnLogout = getConfig().getBoolean("sync-with-group-on-logout");

        storeUnknownPlayers = getConfig().getBoolean("store-unknown-players");
        
        queryTimeout = getConfig().getInt("query-timeout");
        applyTimedOutQueries = getConfig().getBoolean("apply-timed-out-queries");

        enabledSyncTypes = EnumSet.noneOf(SyncType.class);
        for (SyncType syncType : SyncType.values()) {
            String key = "sync." + syncType.getKey();
            if (!getConfig().contains(key, true)
                    && getConfig().contains("sync-" + syncType.getKey(), true)) {
                key = "sync-" + syncType.getKey();
            }
            if (getConfig().getBoolean(key)) {
                enabledSyncTypes.add(syncType);
            }
        }

        if (getServer().getPluginManager().isPluginEnabled("OpenInv")) {
            openInv = (OpenInv) getServer().getPluginManager().getPlugin("OpenInv");
            getLogger().log(Level.INFO, "Hooked into " + openInv.getName() + " " + openInv.getDescription().getVersion());
        }

        if (shouldSync(SyncType.PERSISTENT_DATA)) {
            try {
                String basePackage = getServer().getClass().getPackage().getName();
                Class<?> c = Class.forName(basePackage + ".util.CraftNBTTagConfigSerializer");
                methodDeserializeCompound = c.getMethod("deserialize", Object.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                if (shouldSync(SyncType.PERSISTENT_DATA)) {
                    getLogger().log(Level.WARNING, "Could not load static method required for persistent data syncing. Disabling it!", e);
                    disableSync(SyncType.PERSISTENT_DATA);
                }
            }
        }

        if (getServer().getMap((short) 0) == null) {
            getServer().createMap(getServer().getWorlds().get(0));
        }
        try {
            MapView map = null;
            for (short i = 0; i < Short.MAX_VALUE && map == null; i++) {
                try {
                    map = getServer().getMap(i);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (map != null) {
                fieldWorldMap = map.getClass().getDeclaredField("worldMap");
                fieldWorldMap.setAccessible(true);
                Object worldMap = fieldWorldMap.get(map);
                try {
                    fieldMapColor = worldMap.getClass().getField("g");
                } catch (NoSuchFieldException e) {
                    try {
                        fieldMapColor = worldMap.getClass().getField("colors");
                    } catch (NoSuchFieldException e1) {
                        for (Field field : worldMap.getClass().getFields()) {
                            if (field.getType() == byte[].class) {
                                fieldMapColor = field;
                            }
                        }
                    }
                }
                fieldMapWorldId = worldMap.getClass().getDeclaredField("uniqueId");
                fieldMapWorldId.setAccessible(true);
            } else if (shouldSync(SyncType.MAPS)) {
                getLogger().log(Level.WARNING, "Could not get a map to load the field required for map syncing. Disabling it!");
                disableSync(SyncType.MAPS);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            if (shouldSync(SyncType.MAPS)) {
                getLogger().log(Level.WARNING, "Could not load field required for map syncing. Disabling it!", e);
                disableSync(SyncType.MAPS);
            }
        }

        // Make sure the world "world" exists so that we can store unknown players without issues
        if (storeUnknownPlayers && getServer().getWorld("world") == null && getConfig().getBoolean("create-world")) {
            getLogger().log(Level.INFO, "No world with the name 'world' exists while 'store-unknown-players' is enabled. This world is needed for that functionality to work correctly, creating it... (can be disabled with 'create-world' in the config)");
            World world = getServer().createWorld(new WorldCreator("world")
                    .type(WorldType.FLAT)
                    .generateStructures(false));
            world.setAutoSave(false);
            world.setViewDistance(2);
            world.setKeepSpawnInMemory(false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DISABLE_RAIDS, true);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String [] args) {
        if (args.length > 0) {
            if ("reload".equalsIgnoreCase(args[0]) && sender.hasPermission("syncing.command.reload")) {
                loadConfig();
                sender.sendMessage(getLang("config-reloaded"));
                return true;
            }
        }
        return false;
    }

    /**
     * Get a language message from the config and replace variables in it
     * @param key          The key of the message (lang.<key>)
     * @param replacements An array of String to replace placeholders (uses the % character as placeholder indicators)
     *                     in format of [placeholder, repl, placeholder,repl,...]
     * @return The message string with colorcodes and variables replaced
     */
    public Component getLang(String key, String... replacements) {
        String rawMsg = getConfig().getString("lang." + key); // use default defined by config (values from the config in jar)

        if (rawMsg == null) { // key is missing
            return MineDown.parse(getName() + ": &cMissing language key &6" + key);
        } else {
            return MineDown.parse(rawMsg, replacements);
        }
    }

    /**
     * Check whether or not the inventory etc. of a player is locked
     * @param playerId The UUID of the player
     * @return true if it is locked; false if not
     */
    public boolean isLocked(UUID playerId) {
        return getMessenger() == null || getMessenger().hasQuery(playerId);
    }

    /**
     * Get the date when a player last logged out
     * @param playerId  The UUID of the player
     * @param online    Whether or not it should return the current time if the player is online
     * @return          The timestamp of his last known data on the server in milliseconds;
     *                  0 if the file doesn't exist or an error occurs. (Take a look at {File#lastModified})
     */
    public long getLastSeen(UUID playerId, boolean online) {
        if (online) {
            Player player = getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                return System.currentTimeMillis();
            }
        }
        // Check if lastseen file exists, if so use it
        File lastSeen = getPlayerLastSeenFile(playerId);
        if (lastSeen.exists()) {
            try {
                String lastSeenString = Files.readString(lastSeen.toPath());
                logDebug("Lastseen file existed for " + playerId + "! (" + lastSeenString + ")");
                return Long.parseLong(lastSeenString);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Error while reading last seen file for " + playerId + "!", e);
                return 0;
            }
        }
        File playerDat = getPlayerDataFile(playerId);
        return playerDat.lastModified();
    }

    /**
     * Set the date when a player last logged out (by setting the file modify time)
     * @param playerId  The UUID of the player
     * @param timeStamp The timestamp to set as the last modify time of the file in
     *                  milliseconds.
     * @return          true if the time was successfully set
     */
    public boolean setLastSeen(UUID playerId, long timeStamp) {
        File playerDat = getPlayerDataFile(playerId);
        if (playerDat.exists()) {
            File lastSeen = getPlayerLastSeenFile(playerId);
            if (playerDat.setLastModified(timeStamp)) {
                if (playerDat.lastModified() == timeStamp) {
                    // Delete old last seen file if it existed
                    if (!lastSeen.exists() || lastSeen.delete()) {
                        return true;
                    }
                    logDebug("Unable to remove old last seen file for " + playerId + "?");
                }
                logDebug("Set last seen of " + playerId + " to " + timeStamp + " but it didn't work? Using workaround...");
            } else {
                logDebug("Unable to set last seen of " + playerId + " to " + timeStamp + "! Using workaround...");
            }
            // Workaround for systems that don't allow modifying the dat directly
            try {
                Files.write(lastSeen.toPath(), String.valueOf(timeStamp).getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Unable to store lastseen file for " + playerId, e);
            }
        } else {
            logDebug("Tried to set last seen of " + playerId + " to " + timeStamp + " but they had no player file stored?");
        }
        return false;
    }

    /**
     * Whether or not we should query inventories from other servers
     */
    public boolean shouldQueryInventories() {
        return queryInventories;
    }

    /**
     * Sync data with all servers in a group when a player logs out
     */
    public boolean shouldSyncWithGroupOnLogout() {
        return syncWithGroupOnLogout;
    }

    /**
     * Whether or not we should apply data of queries that weren't answered by every server
     */
    public boolean applyTimedOutQueries() {
        return applyTimedOutQueries;
    }

    /**
     * Connect a player to a bungee server
     * @param playerId The UUID of the player
     * @param server The name of the server
     */
    public void connectToServer(UUID playerId, String server) {
        Player player = getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        }
    }

    /**
     * Apply a PlayerData object to its player
     * @param data  The data to apply
     */
    public void applyData(PlayerData data, Runnable finished) {
        if (data == null)
            return;

        if (data.getDataVersion() != getServer().getUnsafe().getDataVersion()) {
            getLogger().log(Level.WARNING, "Received data with "
                    + (data.getDataVersion() < getServer().getUnsafe().getDataVersion() ? "older" : "newer")
                    + " Minecraft data version (" + data.getDataVersion() + ") than this server (" + getServer().getUnsafe().getDataVersion() + "). Trying to apply anyways but there will most likely be errors! Please try running the same Server version on all synced servers.");
        }

        runSync(() -> {
            Player player = getServer().getPlayer(data.getPlayerId());
            boolean createdNewFile = false;
            if ((player == null || !player.isOnline()) && getMessenger().hasQuery(data.getPlayerId())) {
                cacheData(data, finished);
                logDebug("Player " + data.getPlayerId() + " has query but was not fully online yet! Caching data...");
                return;
            }
            if (getOpenInv() != null && player == null) {
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(data.getPlayerId());
                if (storeUnknownPlayers && !offlinePlayer.hasPlayedBefore()) {
                    if (offlinePlayer.getName() == null) {
                        try {
                            offlinePlayer = (OfflinePlayer) methodGetOfflinePlayer.invoke(getServer(), new GameProfile(data.getPlayerId(), data.getPlayerName()));
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            logDebug("Could not create offline player for " + data.getPlayerId() + "! " + e.getMessage());
                        }
                    }
                    createdNewFile = createNewEmptyData(offlinePlayer.getUniqueId());
                }
                player = getOpenInv().loadPlayer(offlinePlayer);
                if (player == null) {
                    logDebug("Unable to load player " + offlinePlayer.getName() + "/" + offlinePlayer.getUniqueId() + " data with OpenInv");
                } else if (createdNewFile) {
                    try {
                        if (methodGetHandle == null) {
                            methodGetHandle = player.getClass().getMethod("getHandle");
                        }
                        Object entity = methodGetHandle.invoke(player);
                        if (methodSetPositionRaw == null || (fieldYaw == null && methodSetYaw == null) || (fieldPitch == null || methodSetPitch == null)) {
                            try {
                                methodSetPositionRaw = entity.getClass().getMethod("setPositionRaw", double.class, double.class, double.class);
                            } catch (NoSuchMethodException e) {
                                // TODO: Better obfuscation support
                                // 1.18-1.18.2
                                methodSetPositionRaw = entity.getClass().getMethod("e", double.class, double.class, double.class);
                            }
                            try {
                                fieldYaw = entity.getClass().getField("yaw");
                                fieldPitch = entity.getClass().getField("pitch");
                            } catch (NoSuchFieldException e) {
                                try {
                                    methodSetYaw = entity.getClass().getMethod("setYRot", float.class);
                                    methodSetPitch = entity.getClass().getMethod("setYRot", float.class);
                                } catch (NoSuchMethodException ignored) {}
                            }
                        }
                        Location spawn = getServer().getWorlds().get(0).getSpawnLocation();
                        methodSetPositionRaw.invoke(entity, spawn.getX(), spawn.getY(), spawn.getZ());
                        if (fieldYaw != null) {
                            fieldYaw.set(entity, spawn.getYaw());
                        } else if (methodSetYaw != null) {
                            methodSetYaw.invoke(entity, spawn.getYaw());
                        }
                        if (fieldPitch != null) {
                            fieldPitch.set(entity, spawn.getPitch());
                        } else if (methodSetPitch != null) {
                            methodSetPitch.invoke(entity, spawn.getPitch());
                        }
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        getLogger().log(Level.WARNING, "Error while trying to set location of an unknown player. Disabling unknown player storage it!", e);
                        storeUnknownPlayers = false;
                        player = null;
                        getOpenInv().unload(offlinePlayer);
                    }
                }
            }
            if (player == null) {
                logDebug("Could not apply data for player " + data.getPlayerId() + " as he isn't online and "
                        + (getOpenInv() == null ? "this server doesn't have OpenInv installed!" : "never was online on this server before!"));
                if (createdNewFile) {
                    getPlayerDataFile(data.getPlayerId()).delete();
                }
                return;
            }
            if (getOpenInv() != null && !player.isOnline()) {
                getOpenInv().retainPlayer(player, this);
            }
            try {
                if (shouldSync(SyncType.EXPERIENCE)) {
                    player.setTotalExperience(0);
                    player.setLevel(0);
                    player.setExp(0);
                }
                if (shouldSync(SyncType.INVENTORY))
                    player.getInventory().clear();
                if (shouldSync(SyncType.ENDERCHEST))
                    player.getEnderChest().clear();
                if (player.isOnline() && shouldSync(SyncType.EFFECTS)) {
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }
                }
                if (shouldSync(SyncType.HEALTH))
                    player.resetMaxHealth();

                if (shouldSync(SyncType.EXPERIENCE)) {
                    player.setTotalExperience(data.getTotalExperience());
                    player.setLevel(data.getLevel());
                    player.setExp(data.getExp());
                }
                // Try to fix the maps if we should do it
                if (shouldSync(SyncType.MAPS)) {
                    for (MapData mapData : data.getMaps()) {
                        logDebug("Found map " + mapData.getId() + " in inventory");
                        checkMap(mapData.getId());
                        try {
                            logDebug("Writing data of map " + mapData.getId());
                            MapView map = getServer().getMap(mapData.getId());
                            if (map != null) {
                                Object worldMap = fieldWorldMap.get(map);
                                map.setCenterX(mapData.getCenterX());
                                map.setCenterZ(mapData.getCenterZ());
                                map.setScale(mapData.getScale());
                                fieldMapColor.set(worldMap, mapData.getColors());
                                try {
                                    // Newer map info
                                    map.setLocked(mapData.isLocked());
                                    map.setTrackingPosition(mapData.isTrackingPosition());
                                    map.setUnlimitedTracking(mapData.isUnlimitedTracking());
                                } catch (NoSuchMethodError ignored) {}

                                World world = getServer().getWorld(mapData.getWorldId());
                                if (world != null) {
                                    map.setWorld(world);
                                }
                                fieldMapWorldId.set(worldMap, mapData.getWorldId()); // plugin API doesn't change UUID on world set so set it always
                                // Workaround for map not showing directly after creating it
                                forceRender(map);
                                player.sendMap(map);
                            }
                        } catch (IllegalAccessException e) {
                            getLogger().log(Level.SEVERE, "Could not access field in WorldMap class for " + mapData.getId() + "! ", e);
                        } catch (Exception e) {
                            getLogger().log(Level.SEVERE, "Error while trying to store map " + mapData.getId() + "! ", e);
                        }
                    }
                }

                if (shouldSync(SyncType.INVENTORY))
                    player.getInventory().setContents(data.getInventoryContents());
                if (shouldSync(SyncType.ENDERCHEST))
                    player.getEnderChest().setContents(data.getEnderchestContents());
                if (shouldSync(SyncType.GAMEMODE)) {
                    if (data.getGamemode() != null) {
                        player.setGameMode(data.getGamemode());
                    } else {
                        getLogger().log(Level.WARNING, "Data of " + player.getName() + " did not contain gamemode! Setting it to server default " + getServer().getDefaultGameMode());
                        player.setGameMode(getServer().getDefaultGameMode());
                    }
                }
                if (shouldSync(SyncType.HEALTH)) {
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(data.getMaxHealth());
                }
                if (shouldSync(SyncType.HUNGER))
                    player.setFoodLevel(data.getFoodLevel());
                if (shouldSync(SyncType.SATURATION))
                    player.setSaturation(data.getSaturation());
                if (shouldSync(SyncType.EXHAUSTION))
                    player.setExhaustion(data.getExhaustion());
                if (shouldSync(SyncType.AIR)) {
                    player.setMaximumAir(data.getMaxAir());
                    player.setRemainingAir(data.getRemainingAir());
                }
                if (shouldSync(SyncType.FIRE))
                    player.setFireTicks(data.getFireTicks());
                if (shouldSync(SyncType.NO_DAMAGE_TICKS)) {
                    player.setMaximumNoDamageTicks(data.getMaxNoDamageTicks());
                    player.setNoDamageTicks(data.getNoDamageTicks());
                }
                if (shouldSync(SyncType.VELOCITY))
                    player.setVelocity(data.getVelocity());
                if (shouldSync(SyncType.FALL_DISTANCE))
                    player.setFallDistance(data.getFallDistance());
                if (shouldSync(SyncType.PERSISTENT_DATA) && data.getPersistentData() != null) {
                    try {
                        PersistentDataContainer pdc = player.getPersistentDataContainer();
                        pdc.readFromBytes(data.getPersistentData(), true);
                    } catch (IOException e) {
                        getLogger().log(Level.WARNING, "Error while trying to write PersistentDataContainer data. Disabling persistent data syncing!", e);
                        disableSync(SyncType.PERSISTENT_DATA);
                    }
                }
                if (shouldSync(SyncType.ADVANCEMENTS)) {
                    Boolean oldGamerule = null;
                    try {
                        oldGamerule = player.getWorld().getGameRuleValue(GameRule.ANNOUNCE_ADVANCEMENTS);
                        if ((oldGamerule != null && oldGamerule) || (oldGamerule == null && player.getWorld().getGameRuleDefault(GameRule.ANNOUNCE_ADVANCEMENTS))) {
                            player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                        }
                    } catch (NullPointerException ignored) {
                        // world is not known
                    }
                    for (Iterator<Advancement> it = getServer().advancementIterator(); it.hasNext();) {
                        Advancement advancement = it.next();
                        Map<String, Long> awarded = data.getAdvancementProgress().get(advancement.getKey().toString());
                        if (awarded != null) {
                            AdvancementProgress progress = player.getAdvancementProgress(advancement);
                            for (String criterion : progress.getAwardedCriteria()) {
                                if (!awarded.containsKey(criterion)) {
                                    progress.revokeCriteria(criterion);
                                }
                            }
                            for (Map.Entry<String, Long> entry : awarded.entrySet()) {
                                Date date = progress.getDateAwarded(entry.getKey());
                                if (date == null && progress.awardCriteria(entry.getKey())) {
                                    date = progress.getDateAwarded(entry.getKey());
                                }
                                if (date != null && date.getTime() != entry.getValue()) {
                                    date.setTime(entry.getValue());
                                }
                            }
                        }
                    }
                    if (oldGamerule == null || oldGamerule) {
                        try {
                            player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
                        } catch (NullPointerException ignored) {
                            // world is not known
                        }
                    }
                }
                if (shouldSyncAny(SyncType.GENERAL_STATISTICS, SyncType.ENTITY_STATISTICS, SyncType.ITEM_STATISTICS, SyncType.BLOCK_STATISTICS)) {
                    for (Statistic statistic : Statistic.values()) {
                        switch (statistic.getType()) {
                            case UNTYPED:
                                if (shouldSync(SyncType.GENERAL_STATISTICS)) {
                                    Integer value = data.getStatistics().get(statistic, "");
                                    if (value != null && value >= 0) {
                                        player.setStatistic(statistic, value);
                                    }
                                }
                                break;
                            case ENTITY:
                                if (shouldSync(SyncType.ENTITY_STATISTICS)) {
                                    for (EntityType entityType : EntityType.values()) {
                                        Integer value = data.getStatistics().get(statistic, entityType.name());
                                        if (value != null && value > 0) {
                                            player.setStatistic(statistic, entityType, value);
                                        }
                                    }
                                }
                                break;
                            case BLOCK:
                                if (shouldSync(SyncType.BLOCK_STATISTICS)) {
                                    for (Material blockType : Material.values()) {
                                        if (blockType.isBlock()) {
                                            Integer value = data.getStatistics().get(statistic, blockType.name());
                                            if (value != null && value > 0) {
                                                player.setStatistic(statistic, blockType, value);
                                            }
                                        }
                                    }
                                }
                                break;
                            case ITEM:
                                if (shouldSync(SyncType.ITEM_STATISTICS)) {
                                    for (Material itemType : Material.values()) {
                                        if (itemType.isItem()) {
                                            Integer value = data.getStatistics().get(statistic, itemType.name());
                                            if (value != null && value > 0) {
                                                player.setStatistic(statistic, itemType, value);
                                            }
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }
                if (player.isOnline()) {
                    if (shouldSync(SyncType.EFFECTS)) {
                        player.addPotionEffects(data.getPotionEffects());
                    }
                    if (shouldSync(SyncType.HEALTH)) {
                        player.setHealthScale(data.getHealthScale());
                        player.setHealthScaled(data.isHealthScaled());
                        player.setHealth(Math.min(data.getHealth(), player.getMaxHealth()));
                    }
                    if (shouldSync(SyncType.INVENTORY)) {
                        player.getInventory().setHeldItemSlot(data.getHeldItemSlot());
                        player.updateInventory();
                    }
                } else {
                    if (shouldSync(SyncType.HEALTH)) {
                        double health = Math.min(data.getHealth(), player.getMaxHealth());
                        try {
                            if (methodGetHandle == null) {
                                methodGetHandle = player.getClass().getMethod("getHandle");
                            }
                            Object handle = methodGetHandle.invoke(player);
                            if (handle != null) {
                                if (methodSetHealth == null) {
                                    methodSetHealth = handle.getClass().getMethod("setHealth", float.class);
                                }
                                methodSetHealth.invoke(handle, (float) health);
                            }
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            player.setHealth(health > 0 ? health : 1);
                        }
                    }
                }
                finished.run();
                if (getOpenInv() != null && !player.isOnline()) {
                    File playerDat = getPlayerDataFile(data.getPlayerId());
                    // Store original player file modification date to compare after save to catch error while saving as that's not thrown
                    long lastModification = playerDat.lastModified();

                    // Try to save data
                    player.saveData();

                    // Check for temporary file
                    if (new File(playerDataFolder, data.getPlayerId() + "-.dat").exists()) {
                        throw new RuntimeException("Error while trying to save new player data file after creating temp file!");
                    }


                    // If the file was not modified while saving then an error occurred which didn't throw an uncaught exception
                    if (playerDat.lastModified() == lastModification) {
                        throw new RuntimeException("Internal error while trying to save new player data file!");
                    }
                }
                setLastSeen(data.getPlayerId(), data.getLastSeen());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error while applying player data of " + player.getName() + "!", e);
                File playerDat = getPlayerDataFile(data.getPlayerId());
                if (playerDat.exists()) {
                    if (createdNewFile) {
                        playerDat.delete();
                    } else if (playerDat.lastModified() >= data.getLastSeen()) {
                        // Failed to apply data, make sure our locally stored data is older than the newest
                        setLastSeen(data.getPlayerId(), data.getLastSeen() - 1);
                    }
                }
            } finally {
                if (getOpenInv() != null) {
                    getOpenInv().releasePlayer(player, this);
                    getOpenInv().unload(player);
                }
            }
        });
    }

    /**
     * Force a rerender of the map. This is done by adding an empty custom renderer above the vanilla one.
     * @param map The MapView
     */
    private void forceRender(MapView map) {
        map.addRenderer(new EmptyRenderer());
    }

    private static class EmptyRenderer extends MapRenderer {
        @Override
        public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {

        }
    }

    private void cacheData(PlayerData data, Runnable finished) {
        playerDataCache.put(data.getPlayerId(), new AbstractMap.SimpleEntry<>(data, finished));
    }

    /**
     * Get data that was cached which should be applied on a player's login
     * @param player    The player to get the data for
     * @return A cache entry containing the PlayerData and the notification Runnable when applied successfully
     */
    public Map.Entry<PlayerData, Runnable> getCachedData(Player player) {
        return playerDataCache.getIfPresent(player.getUniqueId());
    }

    private File getPlayerDataFile(UUID playerId) {
        return new File(playerDataFolder, playerId + ".dat");
    }

    private File getPlayerLastSeenFile(UUID playerId) {
        return new File(playerDataFolder, playerId + ".lastseen");
    }

    private boolean createNewEmptyData(UUID playerId) {
        File playerDat = getPlayerDataFile(playerId);
        if (playerDat.exists()) {
            return false;
        }
        
        try {
            playerDat.getParentFile().mkdirs();
            Files.copy(getResource("empty.dat"), playerDat.toPath());
            return true;
        } catch (IOException e) {
            logDebug("Error while trying to create file for unknown player " + playerId + ": " + e.getMessage());
        }
        return false;
    }
    
    public PlayerData getData(Player player) {
        PlayerData data = new PlayerData(player, getLastSeen(player.getUniqueId(), player.isOnline()));

        if (shouldSync(SyncType.PERSISTENT_DATA)) {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            try {
                data.setPersistentData(pdc.serializeToBytes());
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Error while trying to access PersistentDataContainer data (" + pdc + "). Disabling persistent data syncing!", e);
                disableSync(SyncType.PERSISTENT_DATA);
            }
        }

        if (shouldSync(SyncType.ADVANCEMENTS)) {
            for (Iterator<Advancement> it = getServer().advancementIterator(); it.hasNext();) {
                Advancement advancement = it.next();
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                Map<String, Long> awarded = new HashMap<>();
                for (String criterion : progress.getAwardedCriteria()) {
                    Date date = progress.getDateAwarded(criterion);
                    if (date != null) {
                        awarded.put(criterion, date.getTime());
                    }
                }
                data.getAdvancementProgress().put(advancement.getKey().toString(), awarded);
            }
        }

        if (shouldSyncAny(SyncType.GENERAL_STATISTICS, SyncType.ENTITY_STATISTICS, SyncType.ITEM_STATISTICS, SyncType.BLOCK_STATISTICS)) {
            for (Statistic statistic : Statistic.values()) {
                switch (statistic.getType()) {
                    case UNTYPED:
                        if (shouldSync(SyncType.GENERAL_STATISTICS)) {
                            int value = player.getStatistic(statistic);
                            if (value > 0) {
                                data.getStatistics().put(statistic, "", value);
                            }
                        }
                        break;
                    case ENTITY:
                        if (shouldSync(SyncType.ENTITY_STATISTICS)) {
                            for (EntityType entityType : EntityType.values()) {
                                try {
                                    int value = player.getStatistic(statistic, entityType);
                                    if (value > 0) {
                                        data.getStatistics().put(statistic, entityType.name(), value);
                                    }
                                } catch (IllegalArgumentException ignored) {} // This statistic doesn't exist
                            }
                        }
                        break;
                    case BLOCK:
                        if (shouldSync(SyncType.BLOCK_STATISTICS)) {
                            for (Material blockType : Material.values()) {
                                if (blockType.isBlock()) {
                                    try {
                                        int value = player.getStatistic(statistic, blockType);
                                        if (value > 0) {
                                            data.getStatistics().put(statistic, blockType.name(), value);
                                        }
                                    } catch (IllegalArgumentException ignored) {} // This statistic doesn't exist
                                }
                            }
                        }
                        break;
                    case ITEM:
                        if (shouldSync(SyncType.ITEM_STATISTICS)) {
                            for (Material itemType : Material.values()) {
                                if (itemType.isItem()) {
                                    try {
                                        int value = player.getStatistic(statistic, itemType);
                                        if (value > 0) {
                                            data.getStatistics().put(statistic, itemType.name(), value);
                                        }
                                    } catch (IllegalArgumentException ignored) {} // This statistic doesn't exist
                                }
                            }
                        }
                        break;
                }
            }
        }

        if (shouldSync(SyncType.MAPS)) {
            // Load maps that are in the inventory/enderchest
            Map<Integer, MapView> maps = new HashMap<>(); // Use set to only add each id once
            maps.putAll(PlayerData.getMapIds(player.getInventory().getContents()));
            maps.putAll(PlayerData.getMapIds(player.getEnderChest().getContents()));
            // Load the map data contents
            for (MapView map : maps.values()) {
                try {
                    Object worldMap = fieldWorldMap.get(map);
                    byte[] colors = (byte[]) fieldMapColor.get(worldMap);

                    UUID worldId = getWorldId(map);
                    if (worldId == null) {
                        getLogger().log(Level.SEVERE, "Could not get world id for map " + map.getId() + "!");
                        continue;
                    }

                    MapData mapData = new MapData(
                            map.getId(),
                            worldId,
                            map.getCenterX(),
                            map.getCenterZ(),
                            map.getScale(),
                            colors
                    );
                    try {
                        // Newer map info
                        mapData.setLocked(map.isLocked());
                        mapData.setTrackingPosition(map.isTrackingPosition());
                        mapData.setUnlimitedTracking(map.isUnlimitedTracking());
                    } catch (NoSuchMethodError ignored) {}
                    data.getMaps().add(mapData);
                } catch (IllegalAccessException e) {
                    getLogger().log(Level.SEVERE, "Could not access field in WorldMap class for " + map.getId() + "! ", e);
                }
            }
        }

        return data;
    }

    /**
     * The sound to play when a player gets unlocked, should match the vanilla levelup
     * @param playerId  The uuid of the Player to play the sound to
     */
    public void playLoadSound(UUID playerId) {
        Player player = getServer().getPlayer(playerId);
        if (player != null) {
            playLoadSound(player);
        }
    }

    /**
     * The sound to play when a player gets unlocked, should match the vanilla levelup
     * @param player    The Player to play the sound to
     */
    public void playLoadSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1);
    }

    /**
     * Make sure that we have maps with that id
     * @param id The map's numeric id
     */
    public void checkMap(int id) {
        setNewestMap(id);
        logDebug("Checking map " + id);
        try {
            while (getServer().getMap(id) == null) {
                MapView map = getServer().createMap(getServer().getWorlds().get(0));
                logDebug("Created map " + map.getId());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error while trying to check map " + id + ". It might be corrupted!", e);
        }
    }

    /**
     * Make sure that a task runs on the primary thread
     */
    public void runSync(Runnable run) {
        if(getServer().isPrimaryThread() || disabling) {
            run.run();
        } else {
            getServer().getScheduler().runTask(this, run);
        }
    }

    /**
     * Make sure that a task does not run on the primary thread
     */
    public void runAsync(Runnable run) {
        if(!getServer().isPrimaryThread() && !disabling) {
            getServer().getScheduler().runTaskAsynchronously(this, run);
        } else {
            run.run();
        }
    }

    public BukkitTask runLater(Runnable runnable, int delay) {
        return getServer().getScheduler().runTaskLater(this, runnable, delay);
    }

    public void sendMessage(UUID playerId, String key) {
        runSync(() -> {
            Player player = getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(getLang(key));
            }
        });
    }

    public void kick(UUID playerId, String key) {
        runSync(() -> {
            Player player = getServer().getPlayer(playerId);
            if (player != null) {
                player.kick(getLang(key));
            }
        });
    }

    public void logDebug(String message) {
        if (debug) {
            getLogger().log(Level.INFO, "Debug: " + message);
        }
    }

    public void setNewestMap(int newestMap) {
        if (getNewestMap() < newestMap) {
            getMessenger().sendGroupMessage(MessageType.MAP_CREATED, newestMap);
            this.newestMap = newestMap;
        }
    }

    public UUID getWorldId(MapView map) {
        if (map == null) {
            return null;
        }
        if (map.getWorld() == null) {
            try {
                return (UUID) fieldMapWorldId.get(fieldWorldMap.get(map));
            } catch (IllegalAccessException e) {
                getLogger().log(Level.SEVERE, "Could not access field in WorldMap class for " + map.getId() + "! ", e);
            }
        } else {
            return map.getWorld().getUID();
        }
        return null;
    }
}
