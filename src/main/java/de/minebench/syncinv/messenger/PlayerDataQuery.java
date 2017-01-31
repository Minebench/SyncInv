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

import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataQuery {
    private final long timestamp = System.currentTimeMillis();
    private final UUID playerId;
    private final long localLastSeen;
    private BukkitTask timeoutTask;

    private Map<String, Long> servers = new ConcurrentHashMap<>();

    public PlayerDataQuery(UUID playerId, long localLastSeen) {
        this.playerId = playerId;
        this.localLastSeen = localLastSeen;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getLocalLastSeen() {
        return localLastSeen;
    }

    public Map<String, Long> getServers() {
        return servers;
    }

    /**
     * Add a new response to this query
     * @param server The name of the server
     * @param lastSeen When the user was last seen on that server
     */
    public void addResponse(String server, long lastSeen) {
        servers.put(server, lastSeen);
    }

    /**
     * Get the youngest server recorded by this query
     * @return The name of the server or null if none was younger
     */
    public String getYoungestServer() {
        String server = null;
        long timestamp = localLastSeen;
        for (Map.Entry<String, Long> entry : servers.entrySet()) {
            if (entry.getValue() > timestamp) {
                timestamp = entry.getValue();
                server = entry.getKey();
            }
        }
        return server;
    }

    /**
     * Get the time this query was created
     * @return Timestamp in milliseconds since midnight, January 1, 1970 UTC.
     */
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimeoutTask(BukkitTask timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    public void stopTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }
}
