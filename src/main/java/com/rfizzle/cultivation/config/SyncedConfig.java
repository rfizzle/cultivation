package com.rfizzle.cultivation.config;

import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of the server's authoritative {@link CultivationConfig},
 * delivered by {@link com.rfizzle.cultivation.network.ConfigSyncS2CPayload} on
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
