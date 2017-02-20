package de.minebench.syncinv.messenger;

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

public enum MessageType {
    /**
     * Make the presence of this server known. If this is targeting everyone
     * or a group it will get a targeted response of the same kind
     */
    HELLO,

    /**
     * Be polite and say bye so that others don't have to wait on you.
     */
    BYE,

    /**
     * Get the time a player was last seen. <br />
     * 1. arg - the player's uuid
     * returns LAST_SEEN
     */
    GET_LAST_SEEN(1),

    /**
     * Answers a GET_LAST_SEEN request with the time a player was last seen. <br />
     * 1. arg - the player's uuid as a string <br />
     * 2. arg - the timestamp as a long
     */
    LAST_SEEN(2),

    /**
     * Get the data of a player <br />
     * 1. arg - the player's uuid
     * returns DATA
     */
    GET_DATA(1),

    /**
     * Answers a GET_DATA request with the player's PlayerData object. <br />
     * 1. arg - the PlayerData object
     */
    DATA(1),

    /**
     * Tells us that a player is online. <br />
     * 1. arg - the player's uuid
     */
    IS_ONLINE(1),

    /**
     * The server failed to load the data of a player. <br />
     * 1. arg - the player's uuid
     */
    CANT_GET_DATA(1),

    /**
     * Send whenever a MapInitializeEvent is called to keep the latest id in sync. <br />
     * 1. arg - the id of the map created as a short
     */
    MAP_CREATED(1);

    private final int argCount;

    MessageType() {
        this(0);
    }

    MessageType(int argCount) {
        this.argCount = argCount;
    }


    public int getArgCount() {
        return argCount;
    }
}
