package com.rfizzle.cultivation.compat.modmenu;

import com.rfizzle.cultivation.config.ConfigFields;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.network.ConfigNetworking;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Builds the Cloth Config screen from {@link ConfigFields#ALL} — one tab per
 * section, one entry per field, every label and tooltip a
 * {@code config.cultivation.*} translation key (mc-config).
 *
 * <p>The screen edits a JSON-round-tripped <em>working copy</em>, never the live
 * singleton: {@link CultivationConfig} forbids in-place mutation of the published
 * instance, so on save the working copy is clamped, written to disk, and the
 * singleton rebuilt via {@link CultivationConfig#reload()} — the same path
 * {@code /cultivation reload} takes. Connected clients pick the change up on the
 * next server-side reload broadcast.
 */
public final class CultivationConfigScreen {
    private CultivationConfigScreen() {
    }

    public static Screen create(Screen parent) {
        // A deep copy through the sync JSON, so field edits never touch the live config.
        CultivationConfig working = CultivationConfig.fromSyncJson(CultivationConfig.get().toSyncJson());
        CultivationConfig defaults = new CultivationConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.cultivation.title"))
                .setSavingRunnable(() -> {
                    working.clamp();
                    working.save();
                    CultivationConfig.reload();
                    // On an integrated server (singleplayer or LAN host), push the reloaded
                    // rules to connected clients — this host included — so config-derived
                    // surfaces refresh without a rejoin. Null when connected to a remote
                    // server, where the local edit is not authoritative anyway.
                    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> ConfigNetworking.syncAll(server));
                    }
                });

        ConfigEntryBuilder entries = builder.entryBuilder();
        for (String category : ConfigFields.CATEGORIES) {
            ConfigCategory tab = builder.getOrCreateCategory(
                    Component.translatable("config.cultivation.category." + category));
            for (ConfigFields.Spec spec : ConfigFields.ALL) {
                if (spec.category().equals(category)) {
                    tab.addEntry(entry(entries, spec, working, defaults));
                }
            }
        }
        return builder.build();
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> entry(
            ConfigEntryBuilder entries, ConfigFields.Spec spec,
            CultivationConfig working, CultivationConfig defaults) {
        Component label = Component.translatable(spec.labelKey());
        Component tooltip = Component.translatable(spec.tooltipKey());
        return switch (spec.kind()) {
            case BOOL -> entries.startBooleanToggle(label, (Boolean) spec.getter().apply(working))
                    .setDefaultValue((Boolean) spec.getter().apply(defaults))
                    .setTooltip(tooltip)
                    .setSaveConsumer(v -> spec.setter().accept(working, v))
                    .build();
            case INT -> entries.startIntField(label, (Integer) spec.getter().apply(working))
                    .setMin((int) spec.min()).setMax((int) spec.max())
                    .setDefaultValue((Integer) spec.getter().apply(defaults))
                    .setTooltip(tooltip)
                    .setSaveConsumer(v -> spec.setter().accept(working, v))
                    .build();
            case DOUBLE -> entries.startDoubleField(label, (Double) spec.getter().apply(working))
                    .setMin(spec.min()).setMax(spec.max())
                    .setDefaultValue((Double) spec.getter().apply(defaults))
                    .setTooltip(tooltip)
                    .setSaveConsumer(v -> spec.setter().accept(working, v))
                    .build();
        };
    }
}
