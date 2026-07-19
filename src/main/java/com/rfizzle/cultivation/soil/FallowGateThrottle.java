package com.rfizzle.cultivation.soil;

/**
 * The villager fallow gate's recheck throttle: remembers the last replant verdict
 * for one work position and re-arms it on a fixed tick cadence, so the gate reads
 * soil state at roughly the farmer's replant rate instead of once per tick.
 *
 * <p>The vanilla farmland task only re-arms its own ~1 Hz throttle when the farmer
 * advances to a young crop. A denied replant leaves the work block air, so that
 * branch never runs and the task retries the plant every tick for the rest of the
 * engagement — up to {@code 200} ticks per seed-carrying farmer parked on resting
 * ground, which the fallow rules make a routine steady state rather than a corner
 * case.
 *
 * <p>Caching a verdict trades a bounded staleness window for that work. The window
 * matters because fallow ground keeps recovering on the live random-tick path
 * ({@link SoilRecovery}) while the farmer stands on it, so a verdict pinned to the
 * work position alone would hold a stale denial for a whole engagement. Re-arming
 * on {@link #INTERVAL_TICKS} instead caps the delay at one second, below what a
 * player can perceive against fertility that moves in fractions per random tick.
 *
 * <p>One instance belongs to one farmland behavior, and so to one villager; it is
 * read and written only on the server thread. The position is a packed {@code long},
 * so the cadence rules are unit-testable without a level.
 */
public final class FallowGateThrottle {
    /**
     * Ticks between rechecks of the same work position, matching the cadence
     * vanilla's farmland task re-arms itself with when it advances to a new target.
     */
    public static final long INTERVAL_TICKS = 20L;

    private long cachedPos;
    private long nextRecheckTick;
    private boolean cachedVerdict;
    private boolean primed;

    /**
     * Whether the gate must recompute the verdict for {@code packedPos} at
     * {@code gameTime}, rather than reuse {@link #cachedVerdict()}. True while no
     * verdict has been recorded yet, when the farmer has moved to a different work
     * position, or once the interval has elapsed.
     */
    public boolean needsRecheck(long packedPos, long gameTime) {
        return !primed || packedPos != cachedPos || gameTime >= nextRecheckTick;
    }

    /** Stores a freshly computed {@code verdict} and re-arms the interval from {@code gameTime}. */
    public void record(long packedPos, long gameTime, boolean verdict) {
        this.cachedPos = packedPos;
        this.nextRecheckTick = gameTime + INTERVAL_TICKS;
        this.cachedVerdict = verdict;
        this.primed = true;
    }

    /**
     * The last recorded verdict. Meaningful only when {@link #needsRecheck} is
     * false; it is {@code false} on an unprimed throttle.
     */
    public boolean cachedVerdict() {
        return cachedVerdict;
    }
}
