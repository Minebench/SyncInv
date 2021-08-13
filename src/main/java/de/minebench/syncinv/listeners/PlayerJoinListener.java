package de.minebench.syncinv.listeners;

import de.minebench.syncinv.PlayerData;
import de.minebench.syncinv.SyncInv;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

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

public class PlayerJoinListener implements Listener {
    private final SyncInv plugin;

    public PlayerJoinListener(SyncInv plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            if (plugin.getMessenger() == null) {
                e.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                e.setKickMessage(ChatColor.RED + plugin.getName() + " is not enabled! Please contact an administrator!");
                return;
            }
            if (plugin.getMessenger().queryData(e.getUniqueId()) == null && !plugin.getMessenger().isAlone()) {
                e.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                e.setKickMessage(ChatColor.RED + "Unable to query player data!");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoined(PlayerJoinEvent e) {
        Map.Entry<PlayerData, Runnable> cached = plugin.getCachedData(e.getPlayer());
        if (cached != null) {
            plugin.applyData(cached.getKey(), cached.getValue());
        }
    }
}
