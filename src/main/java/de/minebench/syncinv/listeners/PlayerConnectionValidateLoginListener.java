package de.minebench.syncinv.listeners;

import de.minebench.syncinv.SyncInv;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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

public class PlayerConnectionValidateLoginListener implements Listener {
    private final SyncInv plugin;

    public PlayerConnectionValidateLoginListener(SyncInv plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerConnectionValidateLoginEvent e) {
        // Event will have pre-cancelled result set for whitelist/banlist
        if (e.isAllowed() && e.getConnection() instanceof PlayerLoginConnection connection && connection.getUnsafeProfile() != null) {
            // Sync login listener for sanity checking as we don't want to allow the player to exist twice
            Entity entity = plugin.getServer().getEntity(connection.getUnsafeProfile().getUniqueId());
            if (entity instanceof Player) {
                e.kickMessage(Component.text("A player with your UUID already exists!").color(NamedTextColor.RED));
                plugin.logDebug("A player object with the same UUID " + connection.getUnsafeProfile().getUniqueId() + " already exists on the server.");
                // Kick player. This should do nothing if it's not a real one (e.g. one loaded by OpenInv)
                // Removal of such players is up to OpenInv itself
                ((Player) entity).kickPlayer("Login from different location.");
            } else if (entity != null) {
                // Well... this is weird. An entity with the same UUID as the player's exists?!? Removing it just to be sure...
                plugin.getLogger().info("A " + entity + " with the same UUID " + connection.getUnsafeProfile().getUniqueId()
                        + " as the player login in existed on the server at " + entity.getLocation() + "... removing it!");
                entity.remove();
            }
        }
    }
}
