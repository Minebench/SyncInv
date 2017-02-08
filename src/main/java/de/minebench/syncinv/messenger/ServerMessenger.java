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

import de.minebench.syncinv.PlayerData;
import de.minebench.syncinv.SyncInv;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class ServerMessenger {
    private final SyncInv plugin;

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
        sendMessage("group:" + getServerGroup(), MessageType.HELLO);
    }

    /**
     * Be polite and say goodbye
     */
    public void goodbye() {
        sendMessage("group:" + getServerGroup(), MessageType.BYE);
    }

    /**
     * Query the data of a player
     * @param playerId The UUID of the player
     * @return The new PlayerDataQuery object or null if one was already started
     */
    public PlayerDataQuery queryData(UUID playerId) {
        if (queries.get(playerId) != null) {
            // already querying data
            return null;
        }

        long lastSeen = plugin.getLastSeen(playerId, false);
        PlayerDataQuery query = new PlayerDataQuery(playerId, lastSeen);
        query.setTimeoutTask(plugin.runLater(() -> finishQuery(query), 20 * plugin.getQueryTimeout()));
        queries.put(playerId, query);

        sendMessage("group:" + getServerGroup(), MessageType.GET_LAST_SEEN);

        return query;
    }

    /**
     * Reaction on a message, this has to be called by the messenger implementation!
     * @param sender    The server that send the message
     * @param target    The server this message is targeted at
     * @param type      The type of request
     * @param in        The input data
     */
    protected void onMessage(String sender, String target, MessageType type, ObjectInput in) {
        if (sender.equals(getServerName()) // don't read messages from ourselves
                || target != null // target is null? Accept message anyways...
                && !"*".equals(target)
                && !getServerName().equals(target)
                && !("group:" + getServerGroup()).equalsIgnoreCase(target) ) {
            // This message is not for us
            return;
        }

        if (!servers.contains(sender)) {
            servers.add(sender);
        }

        UUID playerId;
        long lastSeen;
        Player player;
        PlayerDataQuery query;
        try {
            switch (type) {
                case GET_LAST_SEEN:
                    playerId = UUID.fromString(in.readUTF());
                    lastSeen = plugin.getLastSeen(playerId, true);
                    sendMessage(sender, MessageType.LAST_SEEN, playerId, lastSeen); // Send the last seen date to the server that requested it
                    break;

                case LAST_SEEN:
                    playerId = UUID.fromString(in.readUTF());
                    query = queries.get(playerId);
                    if (query != null) { // No query was started? Why are we getting this message?
                        lastSeen = in.readLong();
                        query.addResponse(sender, lastSeen);

                        if (query.getServers().size() == servers.size()) { // All known servers responded
                            finishQuery(query);
                        }
                    }
                    break;

                case GET_DATA:
                    playerId = UUID.fromString(in.readUTF());
                    player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) { // Player is still online
                        queueDataRequest(playerId, sender);
                        sendMessage(sender, MessageType.IS_ONLINE, playerId); // Tell the sender
                        break;
                    } else if (plugin.getOpenInv() != null){
                        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerId);
                        if (offlinePlayer.hasPlayedBefore()) {
                            player = plugin.getOpenInv().loadPlayer(offlinePlayer);
                            if (player != null) {
                                sendMessage(sender, MessageType.DATA, new PlayerData(player));
                                break;
                            }
                        }
                    } else {
                        sendMessage(sender, MessageType.CANT_GET_DATA, playerId); // Tell the sender that we have no ability to load the data
                    }
                    break;

                case DATA:
                    playerId = UUID.fromString(in.readUTF());
                    query = queries.get(playerId);
                    if (query != null) {
                        query.stopTimeout();
                        queries.remove(playerId);
                        PlayerData data = (PlayerData) in.readObject();
                        plugin.applyData(data);
                    }
                    break;

                case IS_ONLINE:
                    // Do we want to do something if the player is online on the other server?
                    // playerId = UUID.fromString(getString(args[1]));
                    break;

                case CANT_GET_DATA:
                    // Send the player to the server if we can't get the data and he has an open request
                    playerId = UUID.fromString(in.readUTF());
                    if (hasQuery(playerId)) {
                        queries.remove(playerId);
                        plugin.connectToServer(playerId, sender);
                    }
                    break;

                case HELLO:
                    servers.add(sender);
                    if (!getServerName().equalsIgnoreCase(target)) {
                        // Only answer if we were targeted as a group, not if he replied to a single server
                        sendMessage(sender, MessageType.HELLO);
                    }
                    break;

                case BYE:
                    servers.remove(sender);
                    break;

                default:
                    plugin.getLogger().log(Level.WARNING, "Received an unsupported " + type + " request!");
            }
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Received an invalid " + type + " request!", e);
        }
    }

    private void finishQuery(PlayerDataQuery query) {
        query.stopTimeout();

        String youngestServer = query.getYoungestServer();
        if (youngestServer == null) { // This is the youngest server
            queries.remove(query.getPlayerId()); // Let the player play
        } else if (plugin.shouldQueryInventories()){
            sendMessage(youngestServer, new Message(MessageType.DATA, query.getPlayerId())); // Query the player's data
            query.setTimeoutTask(plugin.runLater(() -> queries.remove(query.getPlayerId()), 20 * plugin.getQueryTimeout()));
        } else {
            plugin.connectToServer(query.getPlayerId(), youngestServer); // Connect him to the server
            queries.remove(query.getPlayerId());
        }
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
        sendMessage(target, new Message(type, objects));
    }

    /**
     * Send a message to other servers
     * @param target    The name of the target server;
     *                  use "group:<group>" to only send to a specific group of servers;
     *                  use "*" to send it to everyone
     * @param message   The message to send
     */
    public abstract void sendMessage(String target, Message message);

    /**
     * Check whether or not a player has an active query
     * @param playerId The UUID of the player
     */
    public boolean hasQuery(UUID playerId) {
        return queries.containsKey(playerId);
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
                sendMessage(server, new Message(MessageType.DATA, data));
            }
        }
    }
}
