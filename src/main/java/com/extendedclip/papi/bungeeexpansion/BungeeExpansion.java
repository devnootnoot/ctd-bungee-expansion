package com.extendedclip.papi.bungeeexpansion;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class BungeeExpansion extends PlaceholderExpansion implements PluginMessageListener, Taskable, Configurable {

    private static final String MESSAGE_CHANNEL = "BungeeCord";
    private static final String SERVERS_CHANNEL = "GetServers";
    private static final String CONFIG_INTERVAL = "check_interval";
    private static final String PING_CHANNEL = "Ping";
    private static final String QUEUED_SERVER_CHANNEL = "QueuedServer";
    private static final String QUEUED_POSITION_CHANNEL = "QueuedPosition";
    private static final String QUEUED_MAX_POSITION_CHANNEL = "MaxQueuedPosition";
    private static final String QUEUED_PAUSED_CHANNEL = "QueuedPausedChannel";

    private final AtomicReference<BukkitTask> cached = new AtomicReference<>();
    private final Map<UUID, Integer> ping = new HashMap<>();
    private final Map<UUID, String> queuedServers = new HashMap<>();
    private final Map<UUID, Integer> queuedPosition = new HashMap<>();
    private final Map<UUID, Integer> queuedMaxPosition = new HashMap<>();
    private final Map<UUID, Boolean> queuedPause = new HashMap<>();

    private static Field inputField;

    static {
        try {
            inputField = Class.forName("com.google.common.io.ByteStreams$ByteArrayDataInputStream").getDeclaredField("input");
            inputField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getIdentifier() {
        return "bungeenoot";
    }

    @Override
    public String getAuthor() {
        return "clip";
    }

    @Override
    public String getVersion() {
        return "2.3";
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Collections.singletonMap(CONFIG_INTERVAL, 30);
    }


    @Override
    public String onRequest(final OfflinePlayer player, String identifier) {
        switch (identifier.toLowerCase()) {
            case "ping":
                return String.valueOf(ping.getOrDefault(player.getUniqueId(), 0));
            case "queued_server":
                return String.valueOf(queuedServers.getOrDefault(player.getUniqueId(), "N/A"));
            case "queued_position":
                return String.valueOf(queuedPosition.getOrDefault(player.getUniqueId(), -1));
            case "queued_max_position":
                return String.valueOf(queuedMaxPosition.getOrDefault(player.getUniqueId(), -1));
            case "queued_paused":
                return String.valueOf(queuedPause.getOrDefault(player.getUniqueId(), false));
        }

        return null;
    }

    @Override
    public void start() {
        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(getPlaceholderAPI(), () -> {

            for (Player player : Bukkit.getOnlinePlayers()){
                sendPingMessage(player);
                sendQueuedServerMessage(player);
                sendQueuedPositionMessage(player);
                sendQueuedMaxPositionMessage(player);
                sendQueuedPausedMessage(player);
            }


        }, 20L * 2L, 20L);


        final BukkitTask prev = cached.getAndSet(task);
        if (prev != null) {
            prev.cancel();
        } else {
            Bukkit.getMessenger().registerOutgoingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL, this);
        }
    }



    @Override
    public void stop() {
        final BukkitTask prev = cached.getAndSet(null);
        if (prev == null) {
            return;
        }

        prev.cancel();

        Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL, this);
    }


    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!MESSAGE_CHANNEL.equals(channel)) {
            return;
        }

        //noinspection UnstableApiUsage
        final ByteArrayDataInput in = ByteStreams.newDataInput(message);
        try {
            DataInputStream stream = (DataInputStream) inputField.get(in);
            switch (in.readUTF()) {
                case PING_CHANNEL -> {
                    final UUID playerUuid = UUID.fromString(in.readUTF());
                    final int foundPing = in.readInt();

                    ping.put(playerUuid, foundPing);
                }
                case QUEUED_SERVER_CHANNEL -> {
                    final UUID playerUuid = UUID.fromString(in.readUTF());
                    final String queuedServer = in.readUTF();

                    queuedServers.put(playerUuid, queuedServer);
                }
                case QUEUED_POSITION_CHANNEL -> {
                    final UUID playerUuid = UUID.fromString(in.readUTF());
                    final int position = in.readInt();

                    queuedPosition.put(playerUuid, position);
                }
                case QUEUED_MAX_POSITION_CHANNEL -> {
                    final UUID playerUuid = UUID.fromString(in.readUTF());
                    final int position = in.readInt();

                    queuedMaxPosition.put(playerUuid, position);
                }
                case QUEUED_PAUSED_CHANNEL -> {
                    final UUID playerUuid = UUID.fromString(in.readUTF());
                    final boolean paused = in.readBoolean();

                    queuedPause.put(playerUuid, paused);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void sendQueuedPausedMessage(Player player) {
        sendMessage(QUEUED_PAUSED_CHANNEL, out -> out.writeUTF(player.getUniqueId().toString()));
    }

    private void sendQueuedMaxPositionMessage(Player player) {
        sendMessage(QUEUED_MAX_POSITION_CHANNEL, out -> out.writeUTF(player.getUniqueId().toString()));
    }

    private void sendQueuedPositionMessage(Player player) {
        sendMessage(QUEUED_POSITION_CHANNEL, out -> out.writeUTF(player.getUniqueId().toString()));
    }

    private void sendPingMessage(Player player) {
        sendMessage(PING_CHANNEL, out -> out.writeUTF(player.getUniqueId().toString()));
    }

    private void sendQueuedServerMessage(Player player) {
        sendMessage(QUEUED_SERVER_CHANNEL, out -> out.writeUTF(player.getUniqueId().toString()));
    }

    private void sendMessage(final String channel, final Consumer<ByteArrayDataOutput> consumer) {
        final Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        if (player == null) {
            return;
        }

        //noinspection UnstableApiUsage
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(channel);

        consumer.accept(out);

        player.sendPluginMessage(getPlaceholderAPI(), MESSAGE_CHANNEL, out.toByteArray());
    }

}