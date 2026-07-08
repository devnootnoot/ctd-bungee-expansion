package com.extendedclip.papi.ctdexpansion.queue;

import com.extendedclip.papi.ctdexpansion.MessageSender;
import com.google.common.io.ByteArrayDataInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Modern provider backed by the {@code QueueStates} responder (PR #1003): one request yields every
 * queue a player is in. Reports available only after a response arrives, which older proxies never
 * send.
 */
public final class QueueStatesQueueProvider implements QueueProvider {

    static final String QUEUE_STATES = "QueueStates";

    private final Map<UUID, List<Queue>> queues = new ConcurrentHashMap<>();
    private volatile boolean available = false;

    @Override
    public void request(Player player, MessageSender sender) {
        String uuid = player.getUniqueId().toString();
        sender.send(QUEUE_STATES, out -> out.writeUTF(uuid));
    }

    @Override
    public boolean handleResponse(String subChannel, ByteArrayDataInput in) {
        if (!QUEUE_STATES.equals(subChannel)) {
            return false;
        }

        UUID playerUuid = UUID.fromString(in.readUTF());
        int amount = in.readInt();

        List<Queue> result = new ArrayList<>(Math.max(amount, 0));
        for (int i = 0; i < amount; i++) {
            String name = in.readUTF();
            int position = in.readInt();
            int size = in.readInt();
            boolean paused = in.readBoolean();
            result.add(new Queue(name, position, size, paused));
        }

        queues.put(playerUuid, List.copyOf(result));
        available = true;
        return true;
    }

    @Override
    public List<Queue> getQueues(UUID playerUuid) {
        return queues.getOrDefault(playerUuid, List.of());
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
