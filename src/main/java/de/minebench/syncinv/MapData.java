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

import lombok.Data;
import org.bukkit.map.MapView;

import java.io.Serializable;
import java.util.UUID;

@Data
public class MapData implements Serializable {
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
}
