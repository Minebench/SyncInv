package de.minebench.syncinv.listeners;

import de.minebench.syncinv.PlayerData;
import de.minebench.syncinv.SyncInv;
import de.minebench.syncinv.messenger.MessageType;
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
		if (plugin.isLocked(event.getPlayer().getUniqueId())) { // well shit, the player is gone
			//TODO: what do we do now? Just applying the data to the offline player would be the best thing to do imo
		}

		if (plugin.shouldSyncWithGroupOnLogout()) {
			plugin.getMessenger().sendGroupMessage(MessageType.DATA, new PlayerData(event.getPlayer()));
		} else {
			Set<String> servers = plugin.getMessenger().getQueuedDataRequest(event.getPlayer().getUniqueId());
			if (servers != null && !servers.isEmpty()) {
				PlayerData data = new PlayerData(event.getPlayer());
				plugin.getMessenger().fulfillQueuedDataRequest(data);
			}
		}
	}

}
