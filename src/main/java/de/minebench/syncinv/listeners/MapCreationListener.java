package de.minebench.syncinv.listeners;

import de.minebench.syncinv.SyncInv;
import de.minebench.syncinv.SyncType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.UUID;

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
        if (event.getInventory().getResult() != null && event.getInventory().getResult().getType() == Material.FILLED_MAP && event.getInventory().getResult().getAmount() != 2) {
            plugin.logDebug(event.getView().getPlayer() + " is trying to scale a map!");
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && item.getType() == Material.FILLED_MAP) {
                    MapMeta meta = (MapMeta) item.getItemMeta();
                    MapView map = meta.getMapView();
                    UUID mapId = plugin.getWorldId(map);
                    if (!event.getView().getPlayer().getWorld().getUID().equals(mapId)) {
                        plugin.logDebug(event.getView().getPlayer().getName() + " is not on the world that map " + map.getId() + " is from! (" + mapId + ")");
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
