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
import de.minebench.syncinv.SyncType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.UUID;

public class MapCreationListener implements Listener {
    private final SyncInv plugin;

    public MapCreationListener(SyncInv plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMapCreation(MapInitializeEvent event) {
        if (plugin.shouldSync(SyncType.MAPS)) {
            plugin.setNewestMap(event.getMap().getId());
        }
    }

    @EventHandler
    public void onMapScale(PrepareItemCraftEvent event) {
        if (event.getInventory().getResult() != null && event.getInventory().getResult().getType() == Material.MAP && event.getInventory().getResult().getAmount() != 2) {
            plugin.logDebug(event.getView().getPlayer() + " is trying to scale a map!");
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item.getType() == Material.MAP) {
                    MapView map = plugin.getServer().getMap(item.getDurability());
                    UUID mapId = plugin.getWorldId(map);
                    World world = plugin.getServer().getWorld(mapId);
                    if (event.getView().getPlayer().getWorld() != world) {
                        plugin.logDebug(event.getView().getPlayer().getName() + " is not on the world that map " + map.getId() + " is off! (" + (world == null ? "null" : world.getName() + ")"));
                        event.getInventory().setResult(null);
                        for (HumanEntity viewer : event.getViewers()) {
                            viewer.sendMessage(ChatColor.RED + "Please switch to the world where this map was created to scale it!");
                        }
                        break;
                    }
                }
            }
        }
    }
}
