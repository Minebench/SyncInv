package de.minebench.syncinv;

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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ToString
@Getter
@Setter
public class PlayerData implements Serializable {
    private static final long serialVersionUID = -5703536933548893803L;
    private final long timeStamp = System.currentTimeMillis();
    private final int dataVersion;
    private final UUID playerId;
    private final String playerName;
    private final GameMode gamemode;
    private final int totalExperience;
    private final int level;
    private final float exp;
    private final byte[][] inventory;
    private final byte[][] enderchest;
    private final Collection<PotionEffect> potionEffects;
    private final Set<MapData> maps = new HashSet<>();
    private final double maxHealth;
    private final double health;
    private final boolean isHealthScaled;
    private final double healthScale;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int maxAir;
    private final int remainingAir;
    private final int fireTicks;
    private final int maxNoDamageTicks;
    private final int noDamageTicks;
    private final float fallDistance;
    private final Vector velocity;
    private final int heldItemSlot;
    private final Map<String, Map<String, Long>> advancementProgress = new HashMap<>();
    private final Table<Statistic, String, Integer> statistics = HashBasedTable.create();
    private final long lastSeen;
    private byte[] persistentData = null;

    PlayerData(Player player, long lastSeen) {
        this.dataVersion = player.getServer().getUnsafe().getDataVersion();
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.gamemode = player.getGameMode();
        this.totalExperience = player.getTotalExperience();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.inventory = serializeItems(player.getInventory().getContents());
        this.enderchest = serializeItems(player.getEnderChest().getContents());
        this.potionEffects = player.getActivePotionEffects();
        this.maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        this.health = player.getHealth();
        this.isHealthScaled = player.isHealthScaled();
        this.healthScale = player.getHealthScale();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exhaustion = player.getExhaustion();
        this.maxAir = player.getMaximumAir();
        this.remainingAir = player.getRemainingAir();
        this.fireTicks = player.getFireTicks();
        this.maxNoDamageTicks = player.getMaximumNoDamageTicks();
        this.noDamageTicks = player.getNoDamageTicks();
        this.velocity = player.getVelocity();
        this.fallDistance = player.getFallDistance();
        this.heldItemSlot = player.getInventory().getHeldItemSlot();
        this.lastSeen = lastSeen;
    }

    public ItemStack[] getInventoryContents() {
        return deserializeItems(inventory);
    }

    public ItemStack[] getEnderchestContents() {
        return deserializeItems(enderchest);
    }

    private static byte[][] serializeItems(ItemStack[] items) {
        byte[][] itemByteArray = new byte[items.length][];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            itemByteArray[i] = item != null ? item.serializeAsBytes() : null;
        }
        return itemByteArray;
    }

    private static ItemStack[] deserializeItems(byte[][] items) {
        ItemStack[] itemsArray = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            byte[] itemBytes = items[i];
            itemsArray[i] = itemBytes != null ? ItemStack.deserializeBytes(itemBytes) : null;
        }
        return itemsArray;
    }

    /**
     * Get a map with the IDS and MapViews of all maps in an array of items
     * @param items The items (e.g. from an inventory) to get the maps
     * @return A map of IDs to MapView
     */
    public static Map<? extends Integer, ? extends MapView> getMapIds(ItemStack[] items) {
        Map<Integer, MapView> maps = new HashMap<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.FILLED_MAP) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof MapMeta && ((MapMeta) meta).hasMapView()) {
                    MapView view = ((MapMeta) meta).getMapView();
                    if (view != null) {
                        maps.put(view.getId(), view);
                    }
                }
            }
        }
        return maps;
    }
}
