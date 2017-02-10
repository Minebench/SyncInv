package de.minebench.syncinv;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.lishid.openinv.OpenInv;
import de.minebench.syncinv.listeners.PlayerFreezeListener;
import de.minebench.syncinv.listeners.PlayerJoinListener;
import de.minebench.syncinv.listeners.PlayerQuitListener;
import de.minebench.syncinv.messenger.RedisMessenger;
import de.minebench.syncinv.messenger.ServerMessenger;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
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
    private OpenInv openInv = null;

    /**
     * The messenger for communications between the servers
     */
    private ServerMessenger messenger;

    /**
     * The amount of seconds we should wait for a query to stopTimeout
     */
    private int queryTimeout;

    /**
     * Should we apply data of queries that weren't answered by every server
     */
    private boolean applyTimedOutQueries;

    /**
     * Should the plugin try to fix maps that were transferred over?
     */
    private boolean shouldFixMaps;

    /**
     * Whether or not the plugin is currently disabling
     */
    private boolean disabling = false;

    /**
     * Whether or not the plugin is in debugging mode
     */
    private boolean debug;

    @Override
    public void onEnable() {
        // Plugin startup logic
        loadConfig();

        messenger = new RedisMessenger(this);
        messenger.hello();

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerFreezeListener(this), this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("syncinv").setExecutor(this);
    }

    @Override
    public void onDisable() {
        disabling = true;
        if (getMessenger() != null) {
            getMessenger().goodbye();
        }
    }

    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();

        debug = getConfig().getBoolean("debug");

        queryInventories = getConfig().getBoolean("query-inventories");

        queryTimeout = getConfig().getInt("query-timeout");
        applyTimedOutQueries = getConfig().getBoolean("apply-timed-out-queries");

        shouldFixMaps = getConfig().getBoolean("fix-maps");

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
     * Get a language message from the config
     * @param key The key of the message (lang.<key>)
     * @return The message string with colorcodes replaced
     */
    public String getLang(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("lang." + key, getName() + ": &cMissing language key &6" + key));
    }

    /**
     * Get a language message from the config and replace variables in it
     * @param key The key of the message (lang.<key>)
     * @param replacements An array of variables to be replaced with certain strings in the format [var,repl,var,repl,...]
     * @return The message string with colorcodes and variables replaced
     */
    public String getLang(String key, String... replacements) {
        String msg = getLang(key);
        for (int i = 0; i + 1< replacements.length; i += 2) {
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
        return getMessenger().hasQuery(playerId);
    }

    /**
     * Get the date when a player last logged out
     * @param playerId The UUID of the player
     * @param online Whether or not it should return the current time if the player is online
     * @return The timestamp of his last known data on the server in milliseconds;
     * 0 if the file doesn't exist or an error occurs. (Take a look at {File#lastModified})
     */
    public long getLastSeen(UUID playerId, boolean online) {
        if (online) {
            Player player = getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                return System.currentTimeMillis();
            }
        }
        File playerDataFolder = new File(getServer().getWorlds().get(0).getWorldFolder(), "playerdata");
        File playerDat = new File(playerDataFolder, playerId + ".dat");
        return playerDat.lastModified();
    }

    /**
     * Get the messenger for communications between the servers
     * @return The messenger for communications between the servers
     */
    public ServerMessenger getMessenger() {
        return messenger;
    }

    /**
     * Whether or not we should query inventories from other servers
     */
    public boolean shouldQueryInventories() {
        return queryInventories;
    }

    /**
     * Get the amount of seconds we should wait for a query to stopTimeout
     */
    public int getQueryTimeout() {
        return queryTimeout;
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

    public OpenInv getOpenInv() {
        return openInv;
    }

    /**
     * Apply a PlayerData object to its player
     * @param data
     */
    public void applyData(PlayerData data) {
        if(data == null)
            return;

        runSync(() -> {
            Player player = getServer().getPlayer(data.getPlayerId());

            if (getOpenInv() != null && player == null || !player.isOnline()){
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(data.getPlayerId());
                if (offlinePlayer.hasPlayedBefore()) {
                    player = getOpenInv().loadPlayer(offlinePlayer);
                }
            }

            if(player != null) {
                player.setTotalExperience(0);
                player.setLevel(0);
                player.setExp(0);
                player.getInventory().clear();
                player.getEnderChest().clear();
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }

                player.giveExp(data.getExp());
                // players will associate the level up sound from the exp giving with the successful load of the inventory
                // --> play sound also if the player does not level up
                if (player.isOnline() && player.getLevel() < 1) {
                    playLoadSound(player);
                }
                // Try to fix the maps if we should do it
                if (shouldFixMaps) {
                    File mapDataDir = new File(getServer().getWorlds().get(0).getWorldFolder(), "data");
                    for (Map.Entry<Short, byte[]> map : data.getMapFiles().entrySet()) {
                        File mapFile = new File(mapDataDir, "map_" + map.getKey() + ".dat");
                        if (mapFile.canWrite()) {
                            checkMap(map.getKey());
                            try {
                                Files.write(mapFile.toPath(), map.getValue());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                player.getInventory().setContents(data.getInventory());
                player.getEnderChest().setContents(data.getEnderchest());
                player.addPotionEffects(data.getPotionEffects());
                if (player.isOnline()) {
                    player.updateInventory();
                }
            }
        });
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
     */
    private void checkMap(short id) {
        while (getServer().getMap(id) == null) {
            getServer().createMap(getServer().getWorlds().get(0));
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
}
