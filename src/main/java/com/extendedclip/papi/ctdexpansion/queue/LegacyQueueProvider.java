package com.extendedclip.papi.ctdexpansion.queue;

import com.extendedclip.papi.ctdexpansion.MessageSender;
import com.google.common.io.ByteArrayDataInput;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Legacy provider using the original per-attribute responders. Only ever exposes one queue, so
 * {@link #getQueues(UUID)} returns at most one {@link Queue}. Kept as a fallback for older proxies.
 */
public final class LegacyQueueProvider implements QueueProvider {

    static final String QUEUED_SERVER = "QueuedServer";
    static final String QUEUED_POSITION = "QueuedPosition";
    static final String QUEUED_MAX_POSITION = "MaxQueuedPosition";
    static final String QUEUED_PAUSED = "QueuedPausedChannel";

    /**
     * Server value the proxy sends when the player is not queued.
     */
    private static final String NOT_QUEUED = "N/A";

    private final Map<UUID, String> servers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> positions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> maxPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> paused = new ConcurrentHashMap<>();

    @Override
    public void request(Player player, MessageSender sender) {
        String uuid = player.getUniqueId().toString();
        sender.send(QUEUED_SERVER, out -> out.writeUTF(uuid));
        sender.send(QUEUED_POSITION, out -> out.writeUTF(uuid));
        sender.send(QUEUED_MAX_POSITION, out -> out.writeUTF(uuid));
        sender.send(QUEUED_PAUSED, out -> out.writeUTF(uuid));
    }

    @Override
    public boolean handleResponse(String subChannel, ByteArrayDataInput in) {
        switch (subChannel) {
            case QUEUED_SERVER -> servers.put(UUID.fromString(in.readUTF()), in.readUTF());
            case QUEUED_POSITION -> positions.put(UUID.fromString(in.readUTF()), in.readInt());
            case QUEUED_MAX_POSITION -> maxPositions.put(UUID.fromString(in.readUTF()), in.readInt());
            case QUEUED_PAUSED -> paused.put(UUID.fromString(in.readUTF()), in.readBoolean());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Queue> getQueues(UUID playerUuid) {
        String server = servers.get(playerUuid);
        if (server == null || server.equals(NOT_QUEUED)) {
            return List.of();
        }
        return List.of(new Queue(
                server,
                positions.getOrDefault(playerUuid, -1),
                maxPositions.getOrDefault(playerUuid, -1),
                paused.getOrDefault(playerUuid, false)
        ));
    }

    @Override
    public boolean isAvailable() {
        // Baseline fallback: always usable.
        return true;
    }
}
