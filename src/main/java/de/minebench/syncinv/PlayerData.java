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

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData implements Serializable {
    @Serial
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

    public ItemStack[] getInventoryContents() {
        return deserializeItems(inventory);
    }

    public ItemStack[] getEnderchestContents() {
        return deserializeItems(enderchest);
    }

    /**
     * @return The timestamp, when this data was created
     */
    public long getTimeStamp() {
        return timeStamp;
    }

    /**
     * @return Data version of this data (server dependent)
     */
    public int getDataVersion() {
        return dataVersion;
    }

    /**
     * @return the UUID of the player
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @return Display name of the player
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * @return the players current GameMode
     */
    public GameMode getGamemode() {
        return gamemode;
    }

    /**
     * @return the players current experience points.
     * This refers to the total amount of experience the player has collected over time
     * and is not currently displayed to the client.
     */
    public int getTotalExperience() {
        return totalExperience;
    }

    /**
     * @return the players current experience level
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return the players current experience points towards the next level
     * This is a percentage value. 0 is "no progress" and 1 is "next level".
     */
    public float getExp() {
        return exp;
    }

    /**
     * @return the players current potion effects
     */
    public Collection<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    /**
     * @return map data to be rendered and send to the player
     */
    public Set<MapData> getMaps() {
        return maps;
    }

    /**
     * @return the players max health
     */
    public double getMaxHealth() {
        return maxHealth;
    }

    /**
     * @return the players current health
     */
    public double getHealth() {
        return health;
    }

    /**
     * @return if the client is displayed a 'scaled' health, that is, health on a scale from 0-getHealthScale()
     */
    public boolean isHealthScaled() {
        return isHealthScaled;
    }

    /**
     * @return the number to scale health to for the client
     * Displayed health follows a simple formula displayedHealth = getHealth() / getMaxHealth() * getHealthScale()
     */
    public double getHealthScale() {
        return healthScale;
    }

    /**
     * @return the players current food level
     */
    public int getFoodLevel() {
        return foodLevel;
    }

    /**
     * @return the players current saturation level.
     * Saturation is a buffer for food level. Your food level will not drop if you are saturated > 0.
     */
    public float getSaturation() {
        return saturation;
    }

    /**
     * @return the players current exhaustion level.
     * Exhaustion controls how fast the food level drops.
     * While you have a certain amount of exhaustion, your saturation will drop to zero,
     * and then your food will drop to zero.
     */
    public float getExhaustion() {
        return exhaustion;
    }

    /**
     * @return the maximum amount of air the player can have, in ticks.
     */
    public int getMaxAir() {
        return maxAir;
    }

    /**
     * @return the amount of air that the player has remaining, in ticks.
     */
    public int getRemainingAir() {
        return remainingAir;
    }

    /**
     * @return the amount of ticks before the player stops being on fire
     */
    public int getFireTicks() {
        return fireTicks;
    }

    /**
     * @return the maximum duration in which the player will not take damage.
     */
    public int getMaxNoDamageTicks() {
        return maxNoDamageTicks;
    }

    /**
     * @return the amount of no damage ticks
     */
    public int getNoDamageTicks() {
        return noDamageTicks;
    }

    /**
     * @return The fall distance of the player
     */
    public float getFallDistance() {
        return fallDistance;
    }

    /**
     * @return The velocity of the player
     */
    public Vector getVelocity() {
        return velocity;
    }

    /**
     * @return Set the slot number of the currently held item.
     * This validates whether the slot is between 0 and 8 inclusive.
     */
    public int getHeldItemSlot() {
        return heldItemSlot;
    }

    /**
     * @return the advancements of this player
     */
    public Map<String, Map<String, Long>> getAdvancementProgress() {
        return advancementProgress;
    }

    /**
     * @return the statistics of this player
     */
    public Table<Statistic, String, Integer> getStatistics() {
        return statistics;
    }

    /**
     * @return when the player was last seen
     */
    public long getLastSeen() {
        return lastSeen;
    }

    /**
     * @return the persistentData
     */
    public byte[] getPersistentData() {
        return persistentData;
    }

    /**
     * @param persistentData the persistentData to set
     */
    public void setPersistentData(byte[] persistentData) {
        this.persistentData = persistentData;
    }

    /**
     * @return a string representation of the object
     */
    public String toString() {
        return "PlayerData{" +
            "playerId=" + playerId +
            ", playerName='" + playerName + '\'' +
            ", dataVersion=" + dataVersion +
            ", timeStamp=" + timeStamp +
            '}';
    }
}
