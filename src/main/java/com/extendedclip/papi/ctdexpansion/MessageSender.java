package com.extendedclip.papi.ctdexpansion;

import com.google.common.io.ByteArrayDataOutput;
import java.util.function.Consumer;

/**
 * Sends a plugin message so providers don't depend on Bukkit's messenger directly.
 */
@FunctionalInterface
public interface MessageSender {

    /**
     * Sends a message on {@code subChannel} (written first), then whatever {@code writer} adds.
     */
    void send(String subChannel, Consumer<ByteArrayDataOutput> writer);
}
