package com.rfizzle.cultivation.config;

import com.rfizzle.cultivation.Cultivation;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of the server's authoritative {@link CultivationConfig},
 * delivered by {@link com.rfizzle.cultivation.network.ConfigSyncPayload} on
 * join and after a server-side reload. Held behind a single {@code volatile}
 * reference so a read on the render thread always sees a whole config, never a
 * torn one, and cleared on disconnect so one server's rules never bleed into
 * the next.
 *
 * <p>Read {@link #effective()} for any <em>server-authoritative</em> rule from
 * code that can run on the client — it returns the synced copy when connected
 * to a server that sent one, and falls back to the local file only when
 * standalone. It lives in the common source set because common code runs on
 * both sides: {@code FertilizerItem#useOn} predicts its client-side swing from
 * here, and a client-only holder would be unreachable from it.
 *
 * <p>The standalone fallback expects {@link CultivationConfig#get()} to be
 * already warm — {@code Cultivation#onInitialize} loads it on both physical
 * sides, so a fallback read is a volatile read rather than a disk load. Callers
 * here sit on latency-sensitive paths (item use, tooltip build), so that
 * warm-up must stay ahead of them.
 *
 * <p>Server-side gameplay reads {@link CultivationConfig#get()} directly — the
 * reference here is only ever written by the client's payload receiver, so on a
 * dedicated server it stays null and {@code effective()} resolves to the
 * server's own live config.
 *
 * <p><em>Client-only</em> presentation keys ({@code showSoilOverlays},
 * {@code showFatigueTooltips}, {@code showNutritionTooltips}, the overlay render
 * distance) are always read from {@link CultivationConfig#get()} directly — a
 * server never dictates a purely visual client preference.
 */
public final class SyncedConfig {
    @Nullable
    private static volatile CultivationConfig serverConfig;

    private SyncedConfig() {
    }

    public static void accept(CultivationConfig config) {
        serverConfig = config;
    }

    /**
     * Parses, clamps and publishes a config-sync blob. <strong>Call this on the
     * client thread</strong> — from inside {@code client.execute(...)}, never from
     * {@code decode()} or the netty callback. The clamp emits one synchronous log
     * line per correction, and on this path a remote peer chooses both how many
     * corrections a payload contains and how often it resends: a config packed
     * with out-of-range values, looped, is a write primitive against the client's
     * log file and its event loop.
     *
     * <p>An unreadable blob is <em>not</em> healed to defaults. Defaults would read
     * as a successful sync while seating a config the server never sent — and every
     * default here is the permissive one, so the client would enable features the
     * server had disabled, which is the exact direction this whole mechanism exists
     * to prevent. The previous synced copy is kept instead (or none, on a failed
     * first sync, which falls back to the local file as when standalone), and the
     * failure is logged at error rather than passed off as a warning.
     */
    public static void acceptJson(String json) {
        CultivationConfig parsed = CultivationConfig.fromSyncJson(json);
        if (parsed == null) {
            Cultivation.LOGGER.error(
                    "Ignoring an unreadable config sync from the server; keeping the {} config."
                            + " Server-authoritative rules may not match this session.",
                    serverConfig != null ? "previously synced" : "local");
            return;
        }
        serverConfig = parsed;
    }

    public static void clear() {
        serverConfig = null;
    }

    /** The synced server config, or {@code null} when standalone. */
    @Nullable
    public static CultivationConfig serverConfig() {
        return serverConfig;
    }

    /**
     * The config to read a server-authoritative rule from: the synced copy when
     * connected, else the local file.
     */
    public static CultivationConfig effective() {
        CultivationConfig synced = serverConfig;
        return synced != null ? synced : CultivationConfig.get();
    }
}
