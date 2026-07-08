package com.extendedclip.papi.ctdexpansion.queue;

/**
 * A single queue a player is enqueued in. Position and size are {@code -1} when unknown.
 */
public record Queue(String name, int position, int size, boolean paused) {

}
