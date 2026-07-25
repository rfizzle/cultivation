package com.rfizzle.cultivation.soil;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * A FIFO of pending soil-overlay chunk requests, drained at a fixed budget per
 * client tick so a join or teleport burst is spread under the server's request
 * rate limit instead of overflowing it ({@code design/SPEC.md} §1).
 *
 * <p>Both request sources feed one pacer: the per-chunk pull on chunk load and
 * the nearest-first re-pull when a rule behind the overlays moves mid-session.
 * Sharing a single queue is what keeps their combined send rate — not each
 * alone — under {@code SoilOverlayNetworking}'s 256/s token-bucket refill; an
 * unpaced source overflows the bucket and its requests are dropped silently,
 * leaving the client's overlay cache half-filled.
 *
 * <p>Pure by construction — packed chunk longs in, a send callback out, no
 * {@code net.minecraft.*} types — so the pacing and ordering guarantees test at
 * Tier 1. The owning client shell confines every call to the client main
 * thread, so this class adds no synchronization of its own.
 */
public final class OverlayRequestPacer {
    private final LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
    private final int requestsPerTick;

    /**
     * @param requestsPerTick the most chunks a single {@link #drain} dequeues;
     *                        keep the resulting per-second rate under the
     *                        server's bucket refill.
     */
    public OverlayRequestPacer(int requestsPerTick) {
        this.requestsPerTick = requestsPerTick;
    }

    /** Queues one chunk (packed via {@code ChunkPos.asLong}) for a paced send. */
    public void enqueue(long chunkPos) {
        pending.enqueue(chunkPos);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public int size() {
        return pending.size();
    }

    /** Drops every queued request — used on disconnect and on a dimension swap. */
    public void clear() {
        pending.clear();
    }

    /**
     * Dequeues up to the per-tick budget, sending each chunk that still passes
     * {@code stillLoaded}. A chunk that unloaded while queued is dropped without
     * a send — a later reload fires its own request — but still consumes a
     * budget slot, so a queue full of stale entries cannot spin a whole drain.
     *
     * @return the number of chunks actually sent this drain.
     */
    public int drain(LongPredicate stillLoaded, LongConsumer send) {
        int sent = 0;
        for (int i = 0; i < requestsPerTick && !pending.isEmpty(); i++) {
            long chunkPos = pending.dequeueLong();
            if (stillLoaded.test(chunkPos)) {
                send.accept(chunkPos);
                sent++;
            }
        }
        return sent;
    }
}
