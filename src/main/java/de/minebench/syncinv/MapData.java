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

import org.bukkit.map.MapView;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class MapData implements Serializable {
    @Serial
    private static final long serialVersionUID = 4376356835175363489L;
    private final int id;
    private final UUID worldId;
    private final int centerX;
    private final int centerZ;
    private final MapView.Scale scale;
    private final byte[] colors;
    private boolean locked;
    private boolean trackingPosition;
    private boolean unlimitedTracking;

    public MapData(int id, UUID worldId, int centerX, int centerZ, MapView.Scale scale, byte[] colors) {
        this.id = id;
        this.worldId = worldId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = scale;
        this.colors = colors;
    }

    /**
     * @return The id of the map
     */
    public int getId() {
        return id;
    }

    /**
     * @return the unique id of the world the map is in
     */
    public UUID getWorldId() {
        return worldId;
    }

    /**
     * @return the x coordinate of the center of the map
     */
    public int getCenterX() {
        return centerX;
    }

    /**
     * @return the z coordinate of the center of the map
     */
    public int getCenterZ() {
        return centerZ;
    }

    /**
     * @return the scale of the map
     */
    public MapView.Scale getScale() {
        return scale;
    }

    /**
     * @return the colors of the map
     */
    public byte[] getColors() {
        return colors;
    }

    /**
     * @return if the map is locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * @param locked if the map should be locked
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * @return if the map should track position
     */
    public boolean isTrackingPosition() {
        return trackingPosition;
    }

    /**
     * @param trackingPosition if the map should track position
     */
    public void setTrackingPosition(boolean trackingPosition) {
        this.trackingPosition = trackingPosition;
    }

    /**
     * @return Whether the map will show a smaller position cursor (true), or no position cursor (false) when cursor is outside of map's range.
     */
    public boolean isUnlimitedTracking() {
        return unlimitedTracking;
    }

    /**
     * @param unlimitedTracking Whether the map will show a smaller position cursor (true), or no position cursor (false) when cursor is outside of map's range.
     */
    public void setUnlimitedTracking(boolean unlimitedTracking) {
        this.unlimitedTracking = unlimitedTracking;
    }
}
