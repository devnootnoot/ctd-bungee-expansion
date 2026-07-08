package com.extendedclip.papi.ctdexpansion;

import com.extendedclip.papi.ctdexpansion.queue.LegacyQueueProvider;
import com.extendedclip.papi.ctdexpansion.queue.Queue;
import com.extendedclip.papi.ctdexpansion.queue.QueueProvider;
import com.extendedclip.papi.ctdexpansion.queue.QueueStatesQueueProvider;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public final class CTDExpansion extends PlaceholderExpansion implements PluginMessageListener, Taskable, Configurable {

    private static final String MESSAGE_CHANNEL = "BungeeCord";
    private static final String CONFIG_INTERVAL = "check_interval";
    private static final String PING_CHANNEL = "Ping";

    /**
     * Queue placeholder names that accept an optional index/queue-name suffix.
     */
    private static final List<String> QUEUE_FIELDS =
            List.of("queued_server", "queued_position", "queued_max_position", "queued_paused");

    private final AtomicReference<BukkitTask> cached = new AtomicReference<>();
    private final Map<UUID, Integer> ping = new ConcurrentHashMap<>();

    private final QueueProvider queueStatesProvider = new QueueStatesQueueProvider();
    private final QueueProvider legacyProvider = new LegacyQueueProvider();

    @Override
    public @NotNull String getIdentifier() {
        return "ctd";
    }

    @Override
    public @NotNull String getAuthor() {
        return "clip, Wouter";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Collections.singletonMap(CONFIG_INTERVAL, 30);
    }

    /**
     * The modern provider once available, otherwise the legacy fallback.
     */
    private QueueProvider activeProvider() {
        return queueStatesProvider.isAvailable() ? queueStatesProvider : legacyProvider;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        UUID uuid = player.getUniqueId();
        String id = identifier.toLowerCase();

        if (id.equals("ping")) {
            return String.valueOf(ping.getOrDefault(uuid, 0));
        }

        List<Queue> queues = activeProvider().getQueues(uuid);

        if (id.equals("queued_count")) {
            return String.valueOf(queues.size());
        }

        // Optional suffix targets a queue by 1-based index or name; none means the first queue.
        for (String field : QUEUE_FIELDS) {
            String selector;
            if (id.equals(field)) {
                selector = "";
            } else if (id.startsWith(field + "_")) {
                selector = id.substring(field.length() + 1);
            } else {
                continue;
            }

            Queue queue = resolveQueue(queues, selector);
            return switch (field) {
                case "queued_server" -> queue != null ? queue.name() : "N/A";
                case "queued_position" -> String.valueOf(queue != null ? queue.position() : -1);
                case "queued_max_position" -> String.valueOf(queue != null ? queue.size() : -1);
                case "queued_paused" -> String.valueOf(queue != null && queue.paused());
                default -> null;
            };
        }

        return null;
    }

    /**
     * Resolves the targeted queue: empty selector = first, all digits = 1-based index, else queue
     * name (case-insensitive). Returns {@code null} if not enqueued there, so callers use defaults.
     */
    private static Queue resolveQueue(List<Queue> queues, String selector) {
        if (selector.isEmpty()) {
            return queues.isEmpty() ? null : queues.get(0);
        }

        if (selector.chars().allMatch(Character::isDigit)) {
            try {
                int index = Integer.parseInt(selector) - 1;
                return index >= 0 && index < queues.size() ? queues.get(index) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        for (Queue queue : queues) {
            if (queue.name().equalsIgnoreCase(selector)) {
                return queue;
            }
        }
        return null;
    }

    @Override
    public void start() {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(getPlaceholderAPI(), () -> {
            MessageSender sender = this::sendMessage;

            for (Player player : Bukkit.getOnlinePlayers()) {
                sendPingMessage(player);

                // Probe the modern provider; use legacy only until it proves available.
                queueStatesProvider.request(player, sender);
                if (!queueStatesProvider.isAvailable()) {
                    legacyProvider.request(player, sender);
                }
            }
        }, 20L * 2L, 20L * getLong(CONFIG_INTERVAL, 30));

        BukkitTask prev = cached.getAndSet(task);
        if (prev != null) {
            prev.cancel();
        } else {
            Bukkit.getMessenger().registerOutgoingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL, this);
        }
    }

    @Override
    public void stop() {
        BukkitTask prev = cached.getAndSet(null);
        if (prev == null) {
            return;
        }

        prev.cancel();

        Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!MESSAGE_CHANNEL.equals(channel)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        try {
            String subChannel = in.readUTF();
            if (PING_CHANNEL.equals(subChannel)) {
                UUID playerUuid = UUID.fromString(in.readUTF());
                ping.put(playerUuid, in.readInt());
                return;
            }

            // Sub-channels are disjoint, so at most one provider reads the stream.
            if (!queueStatesProvider.handleResponse(subChannel, in)) {
                legacyProvider.handleResponse(subChannel, in);
            }
        } catch (Exception e) {
            log(Level.WARNING, "Exception while processing plugin message", e);
        }
    }

    private void sendPingMessage(Player player) {
        sendMessage(PING_CHANNEL, out -> out.writeUTF(player.getUniqueId().toString()));
    }

    private void sendMessage(String channel, Consumer<ByteArrayDataOutput> consumer) {
        Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        if (player == null) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(channel);

        consumer.accept(out);

        player.sendPluginMessage(getPlaceholderAPI(), MESSAGE_CHANNEL, out.toByteArray());
    }
}
