package de.minebench.syncinv;

import lombok.Getter;
import lombok.ToString;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@ToString
@Getter
public class PlayerData implements Serializable {
    private final UUID playerId;
    private final int exp;
    private final ItemStack[] inventory;
    private final ItemStack[] enderchest;
    private final Collection<PotionEffect> potionEffects;
    private final Map<Short, byte[]> mapFiles = new HashMap<>();
    private final double maxHealth;
    private final double health;
    private final boolean isHealthScaled;
    private final double healthScale;
    private final int foodLevel;
    private final float exhaustion;
    private final int maxAir;
    private final int remainingAir;
    private final int fireTicks;
    private final int maxNoDamageTicks;
    private final int noDamageTicks;
    private final Vector velocity;
    private final int heldItemSlot;

    public PlayerData(Player player) {
        this.playerId = player.getUniqueId();
        this.exp = getTotalExperience(player); // No way to properly get the total exp in Bukkit
        this.inventory = player.getInventory().getContents();
        this.enderchest = player.getEnderChest().getContents();
        this.potionEffects = player.getActivePotionEffects();
        this.maxHealth = player.getMaxHealth();
        this.health = player.getHealth();
        this.isHealthScaled = player.isHealthScaled();
        this.healthScale = player.getHealthScale();
        this.foodLevel = player.getFoodLevel();
        this.exhaustion = player.getExhaustion();
        this.maxAir = player.getMaximumAir();
        this.remainingAir = player.getRemainingAir();
        this.fireTicks = player.getFireTicks();
        this.maxNoDamageTicks = player.getMaximumNoDamageTicks();
        this.noDamageTicks = player.getNoDamageTicks();
        this.velocity = player.getVelocity();
        this.heldItemSlot = player.getInventory().getHeldItemSlot();

        // Load maps that are in the inventory/enderchest
        Set<Short> mapIdSet = new HashSet<>(); // Use set to only add each id once
        mapIdSet.addAll(getMapIds(inventory));
        mapIdSet.addAll(getMapIds(enderchest));

        // Load the map file data contents
        File mapDataDir = new File(player.getServer().getWorlds().get(0).getWorldFolder(), "data");
        for (Short mapId : mapIdSet) {
            File mapFile = new File(mapDataDir, "map_" + mapId + ".dat");
            if (mapFile.exists() && mapFile.isFile() && mapFile.canRead()) {
                try {
                    mapFiles.put(mapId, Files.readAllBytes(mapFile.toPath()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
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

    /**
     * Get a list with the ids of all maps in an array of items
     * @param items The items (e.g. from an inventory) to get the map ids from
     * @return A list of map ids (shorts)
     */
    public static List<Short> getMapIds(ItemStack[] items) {
        List<Short> mapIds = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.MAP) {
                mapIds.add(item.getDurability());
            }
        }
        return mapIds;
    }
}
