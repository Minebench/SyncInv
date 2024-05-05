package de.minebench.syncinv.messenger;

import de.minebench.syncinv.SyncInv;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.logging.Level;

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

public class RedisMessenger extends ServerMessenger {
    private static final String CHANNEL_PREFIX = "syncinv:";
    private static final String VERSION_PREFIX = Message.VERSION + ":";
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
            uri.setTimeout(Duration.ofSeconds(plugin.getConfig().getLong("redis.timeout")));
        }
        client = RedisClient.create(uri);

        StatefulRedisPubSubConnection<String, byte[]> connection = client.connectPubSub(new StringByteArrayCodec());
        connection.addListener(new RedisPubSubListener<>() {
            @Override
            public void message(String channel, byte[] bytes) {
                if (!channel.startsWith(CHANNEL_PREFIX)) {
                    plugin.getLogger().log(Level.WARNING, "Received a message on " + channel + " even 'though it doesn't belong to our plugin? ");
                    return;
                }
                if (!channel.startsWith(CHANNEL_PREFIX + VERSION_PREFIX)) {
                    plugin.getLogger().log(Level.WARNING, "Received a message on " + channel + " that doesn't match the accepted version " + Message.VERSION + "! ");
                    return;
                }
                if (bytes.length == 0) {
                    plugin.getLogger().log(Level.WARNING, "Received a message with 0 bytes on " + channel + " redis channel? ");
                    return;
                }
                try {
                    Message message = Message.fromByteArray(bytes);
                    plugin.runSync(() -> onMessage(channel.substring(CHANNEL_PREFIX.length() + VERSION_PREFIX.length()), message));
                } catch (IOException | ClassNotFoundException | IllegalArgumentException | InvalidConfigurationException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error while decoding message on " + channel + " redis channel! ", e);
                } catch (VersionMismatchException e) {
                    plugin.getLogger().log(Level.WARNING, e.getMessage() + ". Ignoring message!");
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
        for (String channel : getChannels()) {
            async.subscribe(CHANNEL_PREFIX + VERSION_PREFIX + channel);
        }
    }

    @Override
    protected void close() {
        client.shutdown();
    }

    @Override
    protected void sendMessageImplementation(String target, Message message, boolean sync) {
        if (connection == null || !connection.isOpen()) {
            connection = client.connect(new StringByteArrayCodec());
        }
        if (sync) {
            connection.sync().publish(CHANNEL_PREFIX + VERSION_PREFIX + target, message.toByteArray());
        } else {
            connection.async().publish(CHANNEL_PREFIX + VERSION_PREFIX + target, message.toByteArray());
        }
    }

    private static class StringByteArrayCodec implements RedisCodec<String, byte[]> {
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
