# CTD-Expansion
An expansion for Velocity-CTD's placeholders.

Available Placeholders:
- `%ctd_ping%`: Returns the player's ping.
- `%ctd_queued_server%`: Returns players currently queued server or "N/A".
- `%ctd_queued_position%`: Returns the player's position in queue or -1.
- `%ctd_queued_max_position%`: Returns the number of players in the queue.
- `%ctd_queued_paused%`: Returns whether the queue is paused or not (True/False).
- `%ctd_queued_count%`: Returns how many queues the player is currently in.

On proxies running Velocity-CTD with the `QueueStates` responder
([PR #1003](https://github.com/GemstoneGG/Velocity-CTD/pull/1003)), a player can be in more than
one queue. Each queue placeholder accepts an optional suffix to target a specific queue; without a
suffix it refers to the first queue (alphabetical by name). The suffix may be either a 1-based index
or a queue name (case-insensitive):
- By index: `%ctd_queued_server_2%`, `%ctd_queued_position_2%`, `%ctd_queued_max_position_2%`, `%ctd_queued_paused_2%`, ...
- By name: `%ctd_queued_server_lobby%`, `%ctd_queued_position_lobby%`, `%ctd_queued_paused_lobby%`, ...

When the player isn't enqueued in the targeted queue, defaults are returned (N/A, -1, False).

The expansion auto-detects support: if the proxy responds to `QueueStates` it is used exclusively,
otherwise it falls back to the original per-attribute channels (which only ever expose one queue).
