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
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/*
 * Copyright 2017 Phoenix616 All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation,  version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
     * Should the plugin try to sync player effects?
     */
    private boolean shouldSyncEffects;

    /**
     * Should the plugin try to fix maps that were transferred over?
     */
    private boolean shouldSyncMaps;

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

    // Map syncing
    private Field fieldWorldMap;
    
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
                                    getServer().getScheduler().runTask(this, () -> {
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
                                            sender.sendMessage(ChatColor.RED + "Current server does not have newest player data! "
                                                    + ChatColor.GRAY + "Connecting to server " + query.getYoungestServer() + " which has the newest data...");
                                            connectToServer(((Player) sender).getUniqueId(), query.getYoungestServer());
                                        }
                                    });
                                });
                                if (q == null) {
                                    sender.sendMessage(ChatColor.RED + "Could not query information from other servers! Take a look at the log for more details.");
                                }
                            } else {
                                sender.sendMessage(ChatColor.RED + "Player not found!");
                            }
                        });
                        return true;
                    }
                    return openInvCommand.onCommand(sender, command, label, args);
                }
                return openInvCommand.onCommand(sender, command, label, new String[]{sender.getName()});
            };
            getCommand("openinv").setExecutor(forwarding);
            getCommand("openender").setExecutor(forwarding);
        }

        if (getServer().getMap((short) 0) == null) {
            getServer().createMap(getServer().getWorlds().get(0));
        }
        try {
            MapView map = null;
            for (short i = 0; i < Short.MAX_VALUE && map == null; i++) {
                try {
                    map = getServer().getMap(i);
                } catch (IllegalArgumentException ignored) {}
            }
            if (map != null) {
                fieldWorldMap = map.getClass().getDeclaredField("worldMap");
                fieldWorldMap.setAccessible(true);
            } else if (shouldSyncMaps) {
                getLogger().log(Level.WARNING, "Could not get a map to laod the field required for map syncing. Disabling it!");
                shouldSyncMaps = false;
            }
        } catch (NoSuchFieldException e) {
            if (shouldSyncMaps) {
                getLogger().log(Level.WARNING, "Could not load field required for map syncing. Disabling it!", e);
                shouldSyncMaps = false;
            }
        }
    }

    @Override
    public void onDisable() {
        disabling = true;
        for (Player player : getServer().getOnlinePlayers()) {
            getMessenger().sendGroupMessage(new Message(getMessenger().getServerName(), MessageType.DATA, getData(player)), true);
        }
        if (getMessenger() != null) {
            getMessenger().goodbye();
        }
    }

    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();

        debug = getConfig().getBoolean("debug");

        queryInventories = getConfig().getBoolean("query-inventories");

        syncWithGroupOnLogout = getConfig().getBoolean("sync-with-group-on-logout");

        storeUnknownPlayers = getConfig().getBoolean("store-unknown-players");
        
        queryTimeout = getConfig().getInt("query-timeout");
        applyTimedOutQueries = getConfig().getBoolean("apply-timed-out-queries");

        shouldSyncEffects = getConfig().getBoolean("sync-effects");

        shouldSyncMaps = getConfig().getBoolean("sync-maps");

        if (getServer().getPluginManager().isPluginEnabled("OpenInv")) {
            openInv = (OpenInv) getServer().getPluginManager().getPlugin("OpenInv");
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            if ("reload".equalsIgnoreCase(args[0]) && sender.hasPermission("syncing.command.reload")) {
                loadConfig();
                sender.sendMessage(ChatColor.YELLOW + "Config reloaded!");
                return true;
            }
        }
        return false;
    }

    /**
     * Get a language message from the config and replace variables in it
     * @param key The key of the message (lang.<key>)
     * @param replacements An array of variables to be replaced with certain strings in the format [var,repl,var,repl,...]
     * @return The message string with colorcodes and variables replaced
     */
    public String getLang(String key, String... replacements) {
        String msg = ChatColor.translateAlternateColorCodes('&', getConfig().getString("lang." + key, getName() + ": &cMissing language key &6" + key));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("%" + replacements[i] + "%", replacements[i+1]);
        }
        return msg;
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
        File playerDat = new File(playerDataFolder, playerId + ".dat");
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
        File playerDataFolder = new File(getServer().getWorlds().get(0).getWorldFolder(), "playerdata");
        File playerDat = new File(playerDataFolder, playerId + ".dat");
        return playerDat.setLastModified(timeStamp);
    }

    /**
     * Should the plugin try to keep maps in sync?
     */
    public boolean shouldSyncMaps() {
        return shouldSyncMaps;
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
                if (createdNewFile) {
                    try {
                        if (methodGetHandle == null) {
                            methodGetHandle = player.getClass().getMethod("getHandle");
                        }
                        Object entity = methodGetHandle.invoke(player);
                        if (methodSetPositionRaw == null || fieldYaw == null || fieldPitch == null) {
                            methodSetPositionRaw = entity.getClass().getMethod("setPositionRaw", double.class, double.class, double.class);
                            fieldYaw = entity.getClass().getField("yaw");
                            fieldPitch = entity.getClass().getField("pitch");
                        }
                        Location spawn = getServer().getWorlds().get(0).getSpawnLocation();
                        methodSetPositionRaw.invoke(entity, spawn.getX(), spawn.getY(), spawn.getZ());
                        fieldYaw.set(entity, spawn.getYaw());
                        fieldPitch.set(entity, spawn.getPitch());
                    } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
                        getLogger().log(Level.WARNING, "Error while trying to set location of an unknown player. Disabling unknown player storage it!", e);
                        storeUnknownPlayers = false;
                        player = null;
                        new File(playerDataFolder, data.getPlayerId() + ".dat").delete();
                        getOpenInv().unload(offlinePlayer);
                    }
                }
            }
            if (player == null) {
                logDebug("Could not apply data for player " + data.getPlayerId() + " as he isn't online and "
                        + (getOpenInv() == null ? "this server doesn't have OpenInv installed!" : "never was online on this server before!"));
                return;
            }
            if (getOpenInv() != null && !player.isOnline()) {
                getOpenInv().retainPlayer(player, this);
            }
            try {
                player.setTotalExperience(0);
                player.setLevel(0);
                player.setExp(0);
                player.getInventory().clear();
                player.getEnderChest().clear();
                if (player.isOnline()) {
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }
                }
                player.resetMaxHealth();

                player.setTotalExperience(data.getTotalExperience());
                player.setLevel(data.getLevel());
                player.setExp(data.getExp());
                // Try to fix the maps if we should do it
                if (shouldSyncMaps) {
                    for (MapData mapData : data.getMaps()) {
                        logDebug("Found map " + mapData.getId() + " in inventory");
                        checkMap(mapData.getId());
                        try {
                            logDebug("Writing data of map " + mapData.getId());
                            MapView map = getServer().getMap(mapData.getId());
                            Object worldMap = fieldWorldMap.get(map);
                            map.setCenterX(mapData.getCenterX());
                            map.setCenterZ(mapData.getCenterZ());
                            map.setScale(mapData.getScale());
                            Field colorField = worldMap.getClass().getField("colors");
                            colorField.set(worldMap, mapData.getColors());

                            if (getServer().getWorld(mapData.getWorldId()) != null) {
                                map.setWorld(getServer().getWorld(mapData.getWorldId()));
                            } else {
                                Field dimensionField = worldMap.getClass().getField("map");
                                dimensionField.set(worldMap, (byte) 127);
                                Field worldIdField = worldMap.getClass().getDeclaredField("uniqueId");
                                worldIdField.setAccessible(true);
                                worldIdField.set(worldMap, mapData.getWorldId());
                            }
                            player.sendMap(map);
                        } catch (NoSuchFieldException e) {
                            getLogger().log(Level.SEVERE, "Could not get field from map " + mapData.getId() + "! ", e);
                        } catch (IllegalAccessException e) {
                            getLogger().log(Level.SEVERE, "Could not access field in WorldMap class for " + mapData.getId() + "! ", e);
                        }
                    }
                }

                player.getInventory().setContents(data.getInventory());
                player.getEnderChest().setContents(data.getEnderchest());
                player.setMaxHealth(data.getMaxHealth());
                player.setHealth(data.getHealth());
                player.setFoodLevel(data.getFoodLevel());
                player.setSaturation(data.getSaturation());
                player.setExhaustion(data.getExhaustion());
                player.setMaximumAir(data.getMaxAir());
                player.setRemainingAir(data.getRemainingAir());
                player.setFireTicks(data.getFireTicks());
                player.setMaximumNoDamageTicks(data.getMaxNoDamageTicks());
                player.setNoDamageTicks(data.getNoDamageTicks());
                player.setVelocity(data.getVelocity());
                if (player.isOnline()) {
                    if (shouldSyncEffects) {
                        player.addPotionEffects(data.getPotionEffects());
                    }
                    player.setHealthScaled(data.isHealthScaled());
                    player.setHealthScale(data.getHealthScale());
                    player.getInventory().setHeldItemSlot(data.getHeldItemSlot());
                    player.updateInventory();
                }
                finished.run();
                if (getOpenInv() != null && !player.isOnline()) {
                    player.saveData();
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error while applying player data of " + player.getName() + "!", e);
                if (createdNewFile) {
                    new File(playerDataFolder, data.getPlayerId() + ".dat").delete();
                }
            } finally {
                if (getOpenInv() != null) {
                    getOpenInv().releasePlayer(player, this);
                    getOpenInv().unload(player);
                }
                setLastSeen(data.getPlayerId(), data.getLastSeen());
            }
        });
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

    private boolean createNewEmptyData(UUID playerId) {
        File playerDat = new File(playerDataFolder, playerId + ".dat");
        if (playerDat.exists()) {
            return false;
        }
        File emptyFile = new File(getDataFolder(), "empty.dat");
        if (!emptyFile.exists()) {
            saveResource(emptyFile.getName(), false);
        }
        
        try {
            Files.copy(emptyFile.toPath(), playerDat.toPath());
            return true;
        } catch (IOException e) {
            logDebug("Error while trying to create file for unknown player " + playerId + ": " + e.getMessage());
        }
        return false;
    }
    
    public PlayerData getData(Player player) {
        PlayerData data = new PlayerData(player, getLastSeen(player.getUniqueId(), player.isOnline()));

        if (shouldSyncMaps()) {
            // Load maps that are in the inventory/enderchest
            Map<Integer, MapView> maps = new HashMap<>(); // Use set to only add each id once
            maps.putAll(PlayerData.getMapIds(data.getInventory()));
            maps.putAll(PlayerData.getMapIds(data.getEnderchest()));
            // Load the map data contents
            for (MapView map : maps.values()) {
                try {
                    Object worldMap = fieldWorldMap.get(map);
                    Field colorField = worldMap.getClass().getField("colors");
                    byte[] colors = (byte[]) colorField.get(worldMap);

                    UUID worldId = getWorldId(map);
                    if (worldId == null) {
                        getLogger().log(Level.SEVERE, "Could not get world id for map " + map.getId() + "!");
                        continue;
                    }

                    data.getMaps().add(new MapData(
                            map.getId(),
                            worldId,
                            map.getCenterX(),
                            map.getCenterZ(),
                            map.getScale(),
                            colors
                    ));
                } catch (NoSuchFieldException e) {
                    getLogger().log(Level.SEVERE, "Could not get field from map " + map.getId() + "! ", e);
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
     * @param id
     */
    public void checkMap(int id) {
        setNewestMap(id);
        logDebug("Checking map " + id);
        while (getServer().getMap(id) == null) {
            MapView map = getServer().createMap(getServer().getWorlds().get(0));
            logDebug("Created map " + map.getId());
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
                player.kickPlayer(getLang(key));
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
        try {
            Object worldMap = fieldWorldMap.get(map);
            UUID worldId;
            if (map.getWorld() == null) {
                Field worldIdField = worldMap.getClass().getDeclaredField("uniqueId");
                worldIdField.setAccessible(true);
                worldId = (UUID) worldIdField.get(worldMap);
            } else {
                worldId = map.getWorld().getUID();
            }
            return worldId;
        } catch (NoSuchFieldException e) {
            getLogger().log(Level.SEVERE, "Could not get field from map " + map.getId() + "! ", e);
        } catch (IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Could not access field in WorldMap class for " + map.getId() + "! ", e);
        }
        return null;
    }
}
