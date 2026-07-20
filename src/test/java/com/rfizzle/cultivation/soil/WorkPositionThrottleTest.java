package com.rfizzle.cultivation.soil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The farmland work throttle's recheck cadence: position changes and the interval boundary. */
class WorkPositionThrottleTest {
    private static final long POS = 12345L;
    private static final long OTHER_POS = 67890L;

    @Test
    void anUnprimedThrottleAlwaysCallsForARecheck() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();

        assertTrue(throttle.needsRecheck(POS, 0L), "a throttle with no recorded verdict must recheck");
        assertFalse(throttle.cachedVerdict(), "an unprimed throttle reports a denial rather than a stale allow");
    }

    @Test
    void aRecordedVerdictIsReusedWithinTheInterval() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L, false);

        assertFalse(throttle.needsRecheck(POS, 100L), "the recording tick itself is covered");
        assertFalse(throttle.needsRecheck(POS, 119L), "the last tick before the interval elapses is covered");
        assertFalse(throttle.cachedVerdict(), "the recorded denial is what the gate replays");
    }

    @Test
    void theIntervalBoundaryForcesARecheck() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L, false);

        assertTrue(throttle.needsRecheck(POS, 100L + WorkPositionThrottle.INTERVAL_TICKS),
                "the verdict expires exactly one interval after it was recorded");
        assertTrue(throttle.needsRecheck(POS, 500L), "a verdict long past its interval must not be reused");
    }

    @Test
    void movingToADifferentWorkPositionForcesARecheck() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L, false);

        assertTrue(throttle.needsRecheck(OTHER_POS, 101L),
                "a verdict for one block must never answer for another, however recent");
    }

    @Test
    void anAllowVerdictRoundTripsJustLikeADenial() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L, true);

        assertFalse(throttle.needsRecheck(POS, 110L));
        assertTrue(throttle.cachedVerdict(), "a recorded allow must replay as an allow");
    }

    @Test
    void recordingAgainReArmsTheIntervalFromTheNewTick() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L, false);
        throttle.record(POS, 118L, true);

        assertFalse(throttle.needsRecheck(POS, 120L),
                "the interval runs from the latest recording, not the first");
        assertTrue(throttle.cachedVerdict(), "the latest verdict wins");
        assertTrue(throttle.needsRecheck(POS, 138L), "and it expires one interval after that recording");
    }

    @Test
    void theVerdictFreeRecordArmsTheIntervalWithoutAVerdict() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L);

        assertFalse(throttle.needsRecheck(POS, 119L), "a verdict-free recording throttles just the same");
        assertTrue(throttle.needsRecheck(POS, 100L + WorkPositionThrottle.INTERVAL_TICKS),
                "and expires on the same boundary");
        assertTrue(throttle.needsRecheck(OTHER_POS, 101L), "and is still pinned to its work position");
    }

    @Test
    void theVerdictFreeRecordLeavesAnExistingVerdictAlone() {
        WorkPositionThrottle throttle = new WorkPositionThrottle();
        throttle.record(POS, 100L, true);
        throttle.record(POS, 110L);

        assertTrue(throttle.cachedVerdict(), "re-arming without a verdict must not clear the recorded one");
    }
}
