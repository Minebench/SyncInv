package de.minebench.syncinv;

import lombok.Data;
import org.bukkit.map.MapView;

import java.io.Serializable;
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
@Data
public class MapData implements Serializable {
    private static final long serialVersionUID = 74390249021942L;
    private final int id;
    private final UUID worldId;
    private final int centerX;
    private final int centerZ;
    private final MapView.Scale scale;
    private final byte[] colors;
}
