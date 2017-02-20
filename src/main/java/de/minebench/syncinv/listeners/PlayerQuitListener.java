package de.minebench.syncinv.listeners;

import de.minebench.syncinv.PlayerData;
import de.minebench.syncinv.SyncInv;
import de.minebench.syncinv.messenger.MessageType;
import de.minebench.syncinv.messenger.PlayerDataQuery;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;

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

public class PlayerQuitListener implements Listener {
    private final SyncInv plugin;

    public PlayerQuitListener(SyncInv plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerDataQuery query = plugin.getMessenger().removeQuery(event.getPlayer().getUniqueId());
        if (query != null) {
            // The player is gone although he had a query...
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
    }

}
