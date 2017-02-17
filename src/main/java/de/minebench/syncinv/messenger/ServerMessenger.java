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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.minebench.syncinv.PlayerData;
import de.minebench.syncinv.SyncInv;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public abstract class ServerMessenger {
    protected final SyncInv plugin;

    /**
     * The group that this server is in
     */
    private String serverGroup;

    /**
     * The name of this server, should be the same as in the Bungee's config.yml
     */
    private String serverName;

    /**
     * Store a set of all known servers
     */
    private Set<String> servers = new HashSet<>();

    /**
     * Store the current queries for PlayerData
     */
    private Map<UUID, PlayerDataQuery> queries = new ConcurrentHashMap<>();

    /**
     * This holds queue requests that need to be executed when the player logs out
     */
    private Map<UUID, Set<String>> queuedDataRequests = new ConcurrentHashMap<>();

    public ServerMessenger(SyncInv plugin) {
        this.plugin = plugin;
        serverGroup = plugin.getConfig().getString("server-group");
        serverName = plugin.getConfig().getString("server-name");
    }

    /**
     * Be polite and introduce yourself!
     */
    public void hello() {
        sendGroupMessage(MessageType.HELLO);
    }

    /**
     * Be polite and say goodbye
     */
    public void goodbye() {
        sendMessage("group:" + getServerGroup(), new Message(getServerName(), MessageType.BYE), true);
    }

    /**
     * Query the data of a player
     * @param playerId The UUID of the player
     * @return The new PlayerDataQuery object or null if one was already started
     */
    public PlayerDataQuery queryData(UUID playerId) {
        if (servers.isEmpty()) {
            // We are all alone :'(
            return null;
        }

        if (queries.get(playerId) != null) {
            plugin.logDebug("Already querying data of " + playerId);
            return null;
        }

        long lastSeen = plugin.getLastSeen(playerId, false);
        PlayerDataQuery query = new PlayerDataQuery(playerId, lastSeen);
        query.setTimeoutTask(plugin.runLater(() -> completeQuery(query), 20 * plugin.getQueryTimeout()));
        queries.put(playerId, query);

        sendGroupMessage(MessageType.GET_LAST_SEEN, playerId);

        return query;
    }

    /**
     * Reaction on a message, this has to be called by the messenger implementation!
     * @param target    The server this message is targeted at
     * @param message   The message received
     */
    protected void onMessage(String target, Message message) {
        if (message.getSender().equals(getServerName()) // don't read messages from ourselves
                || target != null // target is null? Accept message anyways...
                && !"*".equals(target)
                && !getServerName().equals(target)
                && !("group:" + getServerGroup()).equalsIgnoreCase(target) ) {
            // This message is not for us
            return;
        }

        plugin.logDebug("Received " + message.getType() + " from " + message.getSender() + " targeted at " + target + " containing " + message.getData().size() + " objects");

        if (!servers.contains(message.getSender())) {
            servers.add(message.getSender());
        }

        UUID playerId;
        long lastSeen;
        Player player;
        PlayerDataQuery query;
        try {
            switch (message.getType()) {
                case GET_LAST_SEEN:
                    playerId = (UUID) message.read();
                    lastSeen = plugin.getLastSeen(playerId, true);
                    sendMessage(message.getSender(), MessageType.LAST_SEEN, playerId, lastSeen); // Send the last seen date to the server that requested it
                    break;

                case LAST_SEEN:
                    playerId = (UUID) message.read();
                    query = queries.get(playerId);
                    if (query != null) { // No query was started? Why are we getting this message?
                        lastSeen = (long) message.read();
                        query.addResponse(message.getSender(), lastSeen);

                        if (isCompleted(query)) { // All known servers responded
                            completeQuery(query);
                        }
                    }
                    break;

                case GET_DATA:
                    playerId = (UUID) message.read();
                    player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) { // Player is still online
                        if (!plugin.shouldSyncWithGroupOnLogout()) {
                            queueDataRequest(playerId, message.getSender());
                        }
                        sendMessage(message.getSender(), MessageType.IS_ONLINE, playerId); // Tell the sender
                    } else if (plugin.getOpenInv() != null){
                        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerId);
                        if (offlinePlayer.hasPlayedBefore()) {
                            Player p = plugin.getOpenInv().loadPlayer(offlinePlayer);
                            if (p != null) {
                                sendMessage(message.getSender(), MessageType.DATA, new PlayerData(p));
                            } else {
                                sendMessage(message.getSender(), MessageType.CANT_GET_DATA, playerId); // Tell the sender that we can't load the data
                            }
                        }
                    } else {
                        sendMessage(message.getSender(), MessageType.CANT_GET_DATA, playerId); // Tell the sender that we have no ability to load the data
                    }
                    break;

                case DATA:
                    PlayerData data = (PlayerData) message.read();
                    query = queries.get(data.getPlayerId());
                    if (query != null || plugin.shouldSyncWithGroupOnLogout() && plugin.getLastSeen(data.getPlayerId(), true) < data.getTimeStamp()) {
                        plugin.applyData(data);
                        if (query != null) {
                            query.stopTimeout();
                            queries.remove(data.getPlayerId());
                        }
                    }
                    break;

                case IS_ONLINE:
                    // Do we want to do something if the player is online on the other server?
                    // playerId = UUID.fromString(getString(args[1]));
                    break;

                case CANT_GET_DATA:
                    // Send the player to the server if we can't get the data and he has an open request
                    playerId = (UUID) message.read();
                    if (hasQuery(playerId)) {
                        plugin.connectToServer(playerId, message.getSender());
                    }
                    break;

                case HELLO:
                    servers.add(message.getSender());
                    if (!getServerName().equalsIgnoreCase(target)) {
                        // Only answer if we were targeted as a group, not if he replied to a single server
                        sendMessage(message.getSender(), MessageType.HELLO);
                    }
                    break;

                case BYE:
                    servers.remove(message.getSender());
                    break;

                default:
                    plugin.getLogger().log(Level.WARNING, "Received an unsupported " + message.getType() + " request!");
            }
        } catch (ClassCastException | NullPointerException e) {
            plugin.getLogger().log(Level.SEVERE, "Received an invalid " + message.getType() + " request!", e);
        }
    }

    private void completeQuery(PlayerDataQuery query) {
        query.stopTimeout();

        if (!query.isCompleted() && !plugin.applyTimedOutQueries() && !isCompleted(query)) {
            plugin.sendMessage(query.getPlayerId(), "cant-load-data");
            plugin.kick(query.getPlayerId(), "cant-load-data");
            return;
        }

        String youngestServer = query.getYoungestServer();
        if (youngestServer == null) { // This is the youngest server
            queries.remove(query.getPlayerId()); // Let the player play

            // players will associate the level up sound from the exp giving with the successful load of the inventory
            // --> play sound
            plugin.playLoadSound(query.getPlayerId());
        } else if (plugin.shouldQueryInventories()){
            sendMessage(youngestServer, MessageType.GET_DATA, query.getPlayerId()); // Query the player's data
            query.setTimeoutTask(plugin.runLater(() -> {
                plugin.sendMessage(query.getPlayerId(), "cant-load-data");
                plugin.kick(query.getPlayerId(), "cant-load-data");
                queries.remove(query.getPlayerId());
            }, 20 * plugin.getQueryTimeout()));
        } else {
            plugin.connectToServer(query.getPlayerId(), youngestServer); // Connect him to the server
        }
    }

    /**
     * Check if a query was answered by all known servers
     * @param query The query to check
     * @return      Whether or not all servers responded
     */
    private boolean isCompleted(PlayerDataQuery query) {
        if (query.getServers().size() != servers.size()) {
            return false;
        }

        for (String server : servers) {
            if (!query.getServers().containsKey(server)) {
                return false;
            }
        }

        query.complete();
        return true;
    }

    /**
     * Send a simple message with only a type to other servers
     * @param target    The name of the target server;
     *                  use "group:<group>" to only send to a specific group of servers;
     *                  use "*" to send it to everyone
     * @param type      The type of the message to send
     * @param objects   The data to send in the order the exact order
     */
    public void sendMessage(String target, MessageType type, Object... objects) {
        sendMessage(target, new Message(getServerName(), type, objects), false);
    }

    /**
     * Send a message to other servers
     * @param target    The name of the target server;
     *                  use "group:<group>" to only send to a specific group of servers;
     *                  use "*" to send it to everyone
     * @param message   The message to send
     * @param sync      Whether the message should be send sync or on its own thread
     */
    public void sendMessage(String target, Message message, boolean sync) {
        plugin.logDebug("Sending " + (sync ? "sync " : "") + message.getType() + " to " + target + " containing " + message.getData().size() + " objects.");
        sendMessageImplementation(target, message, sync);
    }

    /**
     * Send a simple message with only a type to other all servers of the group
     * @param type      The type of the message to send
     * @param objects   The data to send in the order the exact order
     */
    public void sendGroupMessage(MessageType type, Object... objects) {
        sendMessage("group:" + getServerGroup(), type, objects);
    }

    protected abstract void sendMessageImplementation(String target, Message message, boolean sync);

    /**
     * Check whether or not a player has an active query
     * @param playerId The UUID of the player
     */
    public boolean hasQuery(UUID playerId) {
        return queries.containsKey(playerId);
    }

    /**
     * Get the active query of a player
     * @param playerId The UUID of the player
     */
    public PlayerDataQuery getQuery(UUID playerId) {
        return queries.get(playerId);
    }

    /**
     * Remove an active query of a player
     * @param playerId  The UUID of the player
     * @return          The previous PlayerDataQuery if there was one
     */
    public PlayerDataQuery removeQuery(UUID playerId) {
        return queries.remove(playerId);
    }

    /**
     * Add a server to the data request queue
     * @param playerId The UUID of the player
     * @param server The name of the server
     */
    private void queueDataRequest(UUID playerId, String server) {
        queuedDataRequests.putIfAbsent(playerId, new LinkedHashSet<>());
        queuedDataRequests.get(playerId).add(server);
    }

    /**
     * Get the name of the server that wants the data
     * @param playerId The UUID of the player
     * @return A set with all servers that requested the data
     */
    public Set<String> getQueuedDataRequest(UUID playerId) {
        return queuedDataRequests.get(playerId);
    }

    /**
     * Get the group that this server is in
     */
    public String getServerGroup() {
        return serverGroup;
    }

    /**
     * Get the name of this server, should be the same as in the Bungee's config.yml
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Send the data to the server that requested it
     * @param data The player's data
     */
    public void fulfillQueuedDataRequest(PlayerData data) {
        Set<String> servers = queuedDataRequests.get(data.getPlayerId());
        if (servers != null) {
            queuedDataRequests.remove(data.getPlayerId());
            for (String server : servers) {
                sendMessage(server, MessageType.DATA, data);
            }
        }
    }
}
