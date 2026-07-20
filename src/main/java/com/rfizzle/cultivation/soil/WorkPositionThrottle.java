package com.rfizzle.cultivation.soil;

/**
 * A recheck throttle for work the farmland behavior would otherwise redo every
 * tick: it pins one work position and re-arms on a fixed tick cadence, so a gate
 * reads soil state at roughly the farmer's work rate instead of at 20 Hz.
 *
 * <p>Two paths on the farmland task need this, for the same underlying reason —
 * neither inherits the vanilla task's own ~1 Hz throttle. The fallow gate sits
 * inside the vanilla gate but denies the replant, which leaves the work block
 * air, so the branch that re-arms that throttle never runs and the task retries
 * the plant every tick. Fertilizer upkeep runs at the tail of the tick, outside
 * the vanilla gate entirely. Either way a farmer parked on one block carries the
 * cost for as long as the behavior runs — up to its {@code 60}-tick duration —
 * and the soil rules make both cases a routine steady state rather than a corner
 * case.
 *
 * <p>Callers come in two shapes. The fallow gate must answer every tick, so it
 * caches a verdict through {@link #record(long, long, boolean)} and replays it
 * from {@link #cachedVerdict()}. Fertilizer upkeep has nothing to replay — a
 * throttled tick simply skips the dose check — so it re-arms with the
 * verdict-free {@link #record(long, long)}.
 *
 * <p>Caching a verdict trades a bounded staleness window for that work. The
 * window matters for the fallow gate because fallow ground keeps recovering on
 * the live random-tick path ({@link SoilRecovery}) while the farmer stands on
 * it, so a verdict pinned to the work position alone would hold a stale denial
 * for a whole engagement. Re-arming on {@link #INTERVAL_TICKS} caps the delay at
 * one second, below what a player can perceive against fertility that moves in
 * fractions per random tick.
 *
 * <p>One instance belongs to one call site on one farmland behavior, and so to
 * one villager; it is read and written only on the server thread. The position is
 * a packed {@code long}, so the cadence rules are unit-testable without a level.
 */
public final class WorkPositionThrottle {
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
     * Whether the caller must redo its work for {@code packedPos} at
     * {@code gameTime}, rather than skip the tick (or reuse
     * {@link #cachedVerdict()}). True while nothing has been recorded yet, when
     * the farmer has moved to a different work position, or once the interval has
     * elapsed.
     */
    public boolean needsRecheck(long packedPos, long gameTime) {
        return !primed || packedPos != cachedPos || gameTime >= nextRecheckTick;
    }

    /** Stores a freshly computed {@code verdict} and re-arms the interval from {@code gameTime}. */
    public void record(long packedPos, long gameTime, boolean verdict) {
        this.cachedVerdict = verdict;
        record(packedPos, gameTime);
    }

    /**
     * Re-arms the interval from {@code gameTime} without recording a verdict, for
     * callers that skip a throttled tick outright instead of replaying an answer.
     * Leaves {@link #cachedVerdict()} untouched.
     */
    public void record(long packedPos, long gameTime) {
        this.cachedPos = packedPos;
        this.nextRecheckTick = gameTime + INTERVAL_TICKS;
        this.primed = true;
    }

    /**
     * The last recorded verdict. Meaningful only when {@link #needsRecheck} is
     * false and the caller records verdicts at all; it is {@code false} on an
     * unprimed throttle.
     */
    public boolean cachedVerdict() {
        return cachedVerdict;
    }
}
