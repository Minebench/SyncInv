package de.minebench.syncinv;

import java.util.Locale;

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

public enum SyncType {
    INVENTORY,
    ENDERCHEST,
    GAMEMODE,
    EXPERIENCE,
    HEALTH,
    HUNGER,
    SATURATION,
    EXHAUSTION,
    AIR,
    FIRE,
    NO_DAMAGE_TICKS,
    FALL_DISTANCE,
    VELOCITY,
    EFFECTS,
    MAPS,
    PERSISTENT_DATA,
    ADVANCEMENTS,
    GENERAL_STATISTICS,
    ENTITY_STATISTICS,
    ITEM_STATISTICS,
    BLOCK_STATISTICS;

    public String getKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
