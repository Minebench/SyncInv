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

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.codec.ByteArrayCodec;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.codec.StringCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;
import com.lambdaworks.redis.pubsub.StatefulRedisPubSubConnection;
import com.lambdaworks.redis.pubsub.api.async.RedisPubSubAsyncCommands;
import de.minebench.syncinv.SyncInv;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.nio.ByteBuffer;
import java.util.logging.Level;

public class RedisMessenger extends ServerMessenger {
    private final RedisClient client;
    private StatefulRedisConnection<String, byte[]> connection;

    public RedisMessenger(SyncInv plugin) {
        super(plugin);
        RedisURI uri = new RedisURI();
        if (plugin.getConfig().isSet("redis.uri")) {
            uri = RedisURI.create(plugin.getConfig().getString("redis.uri"));
        }
        if (plugin.getConfig().isSet("redis.host")) {
            uri.setHost(plugin.getConfig().getString("redis.host"));
        }
        if (plugin.getConfig().isSet("redis.port")) {
            uri.setPort(plugin.getConfig().getInt("redis.port"));
        }
        if (plugin.getConfig().isSet("redis.password")) {
            uri.setPassword(plugin.getConfig().getString("redis.password"));
        }
        if (plugin.getConfig().isSet("redis.timeout")) {
            uri.setTimeout(plugin.getConfig().getInt("redis.timeout"));
        }
        client = RedisClient.create(uri);

        StatefulRedisPubSubConnection<String, byte[]> connection = client.connectPubSub(new StringByteArrayCodec());
        connection.addListener(new RedisPubSubListener<String, byte[]>() {
            @Override
            public void message(String channel, byte[] bytes) {
                if (bytes.length == 0) {
                    plugin.getLogger().log(Level.WARNING, "Received a message with 0 bytes on " + channel + " redis channel? ");
                    return;
                }
                try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                     ObjectInput in = new BukkitObjectInputStream(bis)){
                    onMessage(channel, (Message) in.readObject());
                } catch (IOException | ClassNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error while decoding message on " + channel + " redis channel! ", e);
                }
            }

            @Override
            public void message(String pattern, String channel, byte[] message) {}

            @Override
            public void subscribed(String channel, long count) {}

            @Override
            public void psubscribed(String pattern, long count) {}

            @Override
            public void unsubscribed(String channel, long count) {}

            @Override
            public void punsubscribed(String pattern, long count) {}
        });

        RedisPubSubAsyncCommands<String, byte[]> async = connection.async();
        async.subscribe("*");
        async.subscribe("group:" + getServerGroup());
        async.subscribe(getServerName());
    }

    @Override
    protected void sendMessageImplementation(String target, Message message, boolean sync) {
        if (connection == null || !connection.isOpen()) {
            connection = client.connect(new StringByteArrayCodec());
        }
        if (sync) {
            connection.sync().publish(target, message.toByteArray());
        } else {
            connection.async().publish(target, message.toByteArray());
        }
    }

    private class StringByteArrayCodec implements RedisCodec<String, byte[]> {

        private final StringCodec stringCodec = new StringCodec();
        private final ByteArrayCodec byteArrayCodec = new ByteArrayCodec();

        @Override
        public String decodeKey(ByteBuffer bytes) {
            return stringCodec.decodeKey(bytes);
        }

        @Override
        public byte[] decodeValue(ByteBuffer bytes) {
            return byteArrayCodec.decodeValue(bytes);
        }

        @Override
        public ByteBuffer encodeKey(String key) {
            return stringCodec.encodeKey(key);
        }

        @Override
        public ByteBuffer encodeValue(byte[] value) {
            return byteArrayCodec.encodeValue(value);
        }
    }
}
