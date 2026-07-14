package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.config.CultivationConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of the server's authoritative {@link CultivationConfig},
 * delivered by {@link com.rfizzle.cultivation.network.ConfigSyncS2CPayload} on
 * join and after a server-side reload. Held behind a single {@code volatile}
 * reference so a tooltip read on the render thread always sees a whole config,
 * never a torn one, and cleared on disconnect so one server's rules never bleed
 * into the next.
 *
 * <p>Read {@link #effective()} for any <em>server-authoritative</em> rule — it
 * returns the synced copy when connected to a server that sent one, and falls
 * back to the local file only when standalone (the offline/singleplayer case).
 * <em>Client-only</em> presentation keys ({@code showSoilOverlays},
 * {@code showFatigueTooltips}, {@code showNutritionTooltips}, the overlay render
 * distance) are always read from {@link CultivationConfig#get()} directly — a
 * server never dictates a purely visual client preference.
 */
public final class ClientCultivationConfig {
    @Nullable
    private static volatile CultivationConfig serverConfig;

    private ClientCultivationConfig() {
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
