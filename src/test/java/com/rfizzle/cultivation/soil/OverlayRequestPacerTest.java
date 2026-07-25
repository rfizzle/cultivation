package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayRequestPacerTest {
    /** Every chunk is loaded — the ordinary send path. */
    private static final java.util.function.LongPredicate ALL_LOADED = pos -> true;

    @Test
    void drainRespectsPerTickBudget() {
        OverlayRequestPacer pacer = new OverlayRequestPacer(8);
        for (int i = 0; i < 100; i++) {
            pacer.enqueue(i);
        }
        List<Long> sent = new ArrayList<>();
        int count = pacer.drain(ALL_LOADED, sent::add);
        assertEquals(8, count, "a single drain sends no more than the per-tick budget");
        assertEquals(8, sent.size());
        assertEquals(92, pacer.size(), "the rest waits for later ticks");
    }

    @Test
    void nothingIsDroppedAcrossDrains() {
        // The regression for #72: a large-view-distance join enqueues thousands of
        // chunks. The unpaced path overflowed the rate limiter and lost ~half; the
        // pacer sends every one, in the order they were queued.
        OverlayRequestPacer pacer = new OverlayRequestPacer(8);
        int total = 4225; // (2*32+1)^2 — a full render-distance-32 square
        for (int i = 0; i < total; i++) {
            pacer.enqueue(i);
        }
        List<Long> sent = new ArrayList<>();
        while (!pacer.isEmpty()) {
            int before = sent.size();
            int count = pacer.drain(ALL_LOADED, sent::add);
            assertTrue(count <= 8, "no drain exceeds the budget");
            assertEquals(count, sent.size() - before, "the reported count matches what was sent");
        }
        assertEquals(total, sent.size(), "every queued request is eventually sent — none dropped");
        for (int i = 0; i < total; i++) {
            assertEquals((long) i, sent.get(i), "requests are sent in FIFO (nearest-first) order");
        }
    }

    @Test
    void unloadedChunksAreSkippedButStillConsumeBudget() {
        OverlayRequestPacer pacer = new OverlayRequestPacer(4);
        for (long pos = 0; pos < 8; pos++) {
            pacer.enqueue(pos);
        }
        List<Long> sent = new ArrayList<>();
        // The first four dequeued chunks have unloaded while queued.
        int count = pacer.drain(pos -> pos >= 4, sent::add);
        assertEquals(0, count, "an unloaded chunk is not sent");
        assertTrue(sent.isEmpty());
        assertEquals(4, pacer.size(),
                "a skipped chunk still consumes a budget slot, so a stale front cannot spin a whole drain");
    }

    @Test
    void clearEmptiesTheQueue() {
        OverlayRequestPacer pacer = new OverlayRequestPacer(8);
        pacer.enqueue(1);
        pacer.enqueue(2);
        assertFalse(pacer.isEmpty());
        pacer.clear();
        assertTrue(pacer.isEmpty());
        assertEquals(0, pacer.size());
    }
}
