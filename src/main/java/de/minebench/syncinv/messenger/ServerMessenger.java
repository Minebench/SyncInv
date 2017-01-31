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
        sendMessage("group:" + plugin.getServerGroup(), MessageType.HELLO);
    }

    public void queryData(UUID playerId) {
        long lastSeen = plugin.getLastSeen(playerId, false);

        queries.put(playerId, new PlayerDataQuery(playerId, lastSeen));

        sendMessage("group:" + plugin.getServerGroup(), MessageType.GET_LAST_SEEN);
    }

    /**
     * Reaction on a message, this has to be called by the messenger implementation!
     * @param sender The server that send the message
     * @param target The server this message is targeted at
     * @param type The type of request
     * @param args The arguments
     */
    private void onMessage(String sender, String target, MessageType type, byte[]... args) {
        if (sender.equals(plugin.getServerName()) // don't read messages from ourselves
                || target != null // target is null? Accept message anyways...
                && !"*".equals(target)
                && !plugin.getServerName().equals(target)
                && !("group:" + plugin.getServerGroup()).equalsIgnoreCase(target) ) {
            // This message is not for us
            return;
        }

        if (!servers.contains(sender)) {
            servers.add(sender);
        }

        if (args.length < type.getArgCount()) {
            plugin.getLogger().log(Level.SEVERE, "Received an invalid " + type + " request! It needs " + type.getArgCount() + " but only has " + (args.length - 1));
            return;
        }

        UUID playerId;
        long lastSeen;
        Player player;
        try {
            switch (type) {
                case GET_LAST_SEEN:
                    playerId = UUID.fromString(getString(args[0]));
                    lastSeen = plugin.getLastSeen(playerId, true);
                    sendMessage(sender, MessageType.LAST_SEEN, toByteArray(playerId.toString()), toByteArray(lastSeen)); // Send the last seen date to the server that requested it
                    break;

                case LAST_SEEN:
                    playerId = UUID.fromString(getString(args[0]));
                    PlayerDataQuery query = queries.get(playerId);
                    if (query != null) { // No query was started? Why are we getting this message?
                        lastSeen = getLong(args[1]);
                        query.addResponse(sender, lastSeen);

                        if (query.getServers().size() == servers.size() // All known servers responded
                                || query.getTimestamp() + plugin.getQueryTimeout() * 1000 < System.currentTimeMillis()) { // Query timed out, just use the known servers
                            String youngestServer = query.getYoungestServer();
                            if (youngestServer == null) { // This is the youngest server
                                queries.remove(playerId); // Let the player play
                            } else if (plugin.shouldQueryInventories()){
                                sendMessage(youngestServer, MessageType.GET_DATA, toByteArray(playerId.toString())); // Query the player's data
                            } else {
                                plugin.connectToServer(playerId, youngestServer); // Connect him to the server
                                queries.remove(playerId);
                            }
                        }
                    }
                    break;

                case GET_DATA:
                    playerId = UUID.fromString(getString(args[0]));
                    player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) { // Player is still online
                        queueDataRequest(playerId, sender);
                        sendMessage(sender, MessageType.IS_ONLINE, toByteArray(playerId.toString())); // Tell the sender
                        break;
                    } else if (plugin.getOpenInv() != null){
                        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerId);
                        if (offlinePlayer.hasPlayedBefore()) {
                            player = plugin.getOpenInv().loadPlayer(offlinePlayer);
                            if (player != null) {
                                sendMessage(sender, MessageType.DATA, objectToByteArray(new PlayerData(player)));
                                break;
                            }
                        }
                    } else {
                        sendMessage(sender, MessageType.CANT_GET_DATA, toByteArray(playerId.toString())); // Tell the sender that we have no ability to load the data
                    }
                    break;

                case DATA:
                    playerId = UUID.fromString(getString(args[0]));
                    player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        PlayerData data = (PlayerData) getObject(args[1]);
                        plugin.applyData(data);
                    }
                    break;

                case IS_ONLINE:
                    // Do we want to do something if the player is online on the other server?
                    // playerId = UUID.fromString(getString(args[1]));
                    break;

                case CANT_GET_DATA:
                    // Send the player to the server if we can't get the data and he has an open request
                    playerId = UUID.fromString(getString(args[1]));
                    if (hasQuery(playerId)) {
                        queries.remove(playerId);
                        plugin.connectToServer(playerId, sender);
                    }
                    break;

                case HELLO:
                    servers.add(sender);
                    if (!plugin.getServerName().equalsIgnoreCase(target)) {
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
            e.printStackTrace();
        }
    }

    /**
     * Send a message to other servers
     * @param target The name of the target server;
     *               use "group:<group>" to only send to a specific group of servers;
     *               use "*" to send it to everyone
     * @param type The type of the message
     * @param data The data bytes of this message
     */
    public abstract void sendMessage(String target, MessageType type, byte[]... data);

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
     * Send the data to the server that requested it
     * @param data The player's data
     */
    public void fulfillQueuedDataRequest(PlayerData data) {
        Set<String> servers = queuedDataRequests.get(data.getPlayerId());
        if (servers != null) {
            queuedDataRequests.remove(data.getPlayerId());
            for (String server : servers) {
                sendMessage(server, MessageType.DATA, objectToByteArray(data));
            }
        }
    }

    private byte[] toByteArray(long l) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutput out = new BukkitObjectOutputStream(bos);
            out.writeLong(l);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bos.toByteArray();
    }

    private byte[] toByteArray(String s) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutput out = new BukkitObjectOutputStream(bos);
            out.writeChars(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bos.toByteArray();
    }

    private byte[] objectToByteArray(Object o) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutput out = new BukkitObjectOutputStream(bos);
            out.writeObject(o);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bos.toByteArray();
    }

    private Long getLong(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new BukkitObjectInputStream(bis)){
            return in.readLong();
        }
    }

    private String getString(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new BukkitObjectInputStream(bis)){
            return in.readUTF();
        }
    }

    private Object getObject(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new BukkitObjectInputStream(bis)){
            return in.readObject();
        }
    }
}
