package de.minebench.syncinv.listeners;

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

import de.minebench.syncinv.SyncInv;
import de.minebench.syncinv.messenger.MessageType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.MapInitializeEvent;

public class MapCreationListener implements Listener {
    private final SyncInv plugin;

    public MapCreationListener(SyncInv plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMapCreation(MapInitializeEvent event) {
        if (plugin.shouldSyncMaps())
        plugin.getMessenger().sendGroupMessage(MessageType.MAP_CREATED, event.getMap().getId());
    }
}
