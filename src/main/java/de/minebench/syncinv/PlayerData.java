package de.minebench.syncinv;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.UUID;

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

public class PlayerData {
    private final UUID playerId;
    private final int exp;
    private final ItemStack[] inventory;
    private final ItemStack[] enderchest;
    private final Collection<PotionEffect> potionEffects;

    public PlayerData(UUID playerId, int exp, ItemStack[] inventory, ItemStack[] enderchest, Collection<PotionEffect> potionEffects) {
        this.playerId = playerId;
        this.exp = exp;
        this.inventory = inventory;
        this.enderchest = enderchest;
        this.potionEffects = potionEffects;
    }

    public PlayerData(Player player) {
        this(
                player.getUniqueId(),
                getTotalExperience(player),
                player.getInventory().getContents(),
                player.getEnderChest().getContents(),
                player.getActivePotionEffects()
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getExp() {
        return exp;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    public ItemStack[] getEnderchest() {
        return enderchest;
    }

    public Collection<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    public String toString() {
        String r = "PlayerData{playerId=" + playerId + ",exp=" + exp + ",inventory={";
        for(ItemStack item : inventory) {
            r += item + ",";
        }
        r += "},enderchest=";
        for(ItemStack item : enderchest) {
            r += item + ",";
        }
        r += "},potionEffects=";
        for(PotionEffect effect : potionEffects) {
            r += effect + ",";
        }
        return r + "}}";
    }

    /**
     * Get the exp you need for a certain level. Uses the 1.8 exp math
     *
     * Method from Essentials
     *
     * @param level The level
     * @return The exp
     */
    public static int getExpAtLevel(final int level) {
        if (level <= 15) {
            return (2 * level) + 7;
        }
        if ((level >= 16) && (level <= 30)) {
            return (5 * level) - 38;
        }
        return (9 * level) - 158;

    }

    /**
     * Get the total exp a player has
     *
     * This method is required because the Bukkit Player.getTotalExperience() method shows exp that has been 'spent'.
     *
     * Without this people would be able to use exp and then still sell it.
     *
     * Method from Essentials
     *
     * @param player the player to get the total exp from
     * @return The total exp the player has
     */
    public static int getTotalExperience(final Player player) {
        int exp = Math.round(getExpAtLevel(player.getLevel()) * player.getExp());
        int currentLevel = player.getLevel();

        while (currentLevel > 0) {
            currentLevel--;
            exp += getExpAtLevel(currentLevel);
        }
        if (exp < 0) {
            exp = Integer.MAX_VALUE;
        }
        return exp;
    }
}
