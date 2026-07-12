package com.rfizzle.cultivation.attachment;

import net.minecraft.server.level.ServerPlayer;

/**
 * The single mediator for a player's {@link DietData} ({@code AGENTS.md}, SPEC
 * §3). Reads return pristine {@link DietData#EMPTY} when nothing is tracked, and
 * writes evict an all-default record so an untouched player carries zero
 * Cultivation data. A player entity serializes with its save every write, so —
 * unlike a block-entity attachment — no explicit dirtying is needed; the ban on
 * bare in-place mutation still holds, so route every write through here.
 */
public final class DietStore {
    private DietStore() {
    }

    public static DietData get(ServerPlayer player) {
        DietData data = player.getAttached(CultivationAttachments.DIET);
        return data == null ? DietData.EMPTY : data;
    }

    public static void set(ServerPlayer player, DietData data) {
        if (data.isDefault()) {
            player.removeAttached(CultivationAttachments.DIET);
        } else {
            player.setAttached(CultivationAttachments.DIET, data);
        }
    }
}
