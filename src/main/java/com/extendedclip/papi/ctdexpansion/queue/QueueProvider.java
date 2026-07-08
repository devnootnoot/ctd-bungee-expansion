package com.extendedclip.papi.ctdexpansion.queue;

import com.extendedclip.papi.ctdexpansion.MessageSender;
import com.google.common.io.ByteArrayDataInput;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Provides the queues a player is currently enqueued in. Implemented by {@link LegacyQueueProvider}
 * (single queue, legacy responders) and {@link QueueStatesQueueProvider} (all queues, PR #1003).
 */
public interface QueueProvider {

    /**
     * Sends the request(s) for a player; responses arrive via {@link #handleResponse}.
     */
    void request(Player player, MessageSender sender);

    /**
     * Consumes a response if it belongs to this provider, returning whether it did.
     */
    boolean handleResponse(String subChannel, ByteArrayDataInput in);

    /**
     * The player's queues, most-relevant first; empty if not queued or no data yet.
     */
    List<Queue> getQueues(UUID playerUuid);

    /**
     * Whether the proxy has confirmed support for this provider.
     */
    boolean isAvailable();
}
