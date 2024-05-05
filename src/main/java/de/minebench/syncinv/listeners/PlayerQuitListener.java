package de.minebench.syncinv.listeners;

import de.minebench.syncinv.PlayerData;
import de.minebench.syncinv.SyncInv;
import de.minebench.syncinv.messenger.MessageType;
import de.minebench.syncinv.messenger.PlayerDataQuery;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;

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

public class PlayerQuitListener implements Listener {
    private final SyncInv plugin;

    public PlayerQuitListener(SyncInv plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getMessenger() == null) {
            return;
        }
        PlayerDataQuery query = plugin.getMessenger().removeQuery(event.getPlayer().getUniqueId());
        if (query != null) {
            // The player is gone, although he had a query...
            // We have to make sure now that the time of the data file matches the old one
            // and not send our data to all the other servers as it might be outdated
            plugin.runLater(() -> {
                if (plugin.getLastSeen(query.getPlayerId(), false) > query.getLocalLastSeen()) {
                    plugin.setLastSeen(query.getPlayerId(), query.getLocalLastSeen());
                }
            }, 1);
            return;
        }


        if (plugin.shouldSyncWithGroupOnLogout()) {
            plugin.getMessenger().sendGroupMessage(MessageType.DATA, plugin.getData(event.getPlayer()));
        } else {
            Set<String> servers = plugin.getMessenger().getQueuedDataRequest(event.getPlayer().getUniqueId());
            if (servers != null && !servers.isEmpty()) {
                PlayerData data = plugin.getData(event.getPlayer());
                plugin.getMessenger().fulfillQueuedDataRequest(data);
            }
        }
        // Update last seen
        plugin.runLater(() -> plugin.setLastSeen(event.getPlayer().getUniqueId(), System.currentTimeMillis()), 1);
    }
}
