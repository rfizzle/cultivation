package com.rfizzle.cultivation.attachment;

import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * The mod's Fabric data attachments. {@link #SOIL} is chunk-scoped and {@link
 * #DIET} is player-scoped; both have no initializer on purpose: an absent value
 * means pristine defaults, so a world where nobody farms or eats varied food
 * carries zero Cultivation data. Never auto-attach — every write goes through
 * {@link SoilStores} / {@link DietStore}.
 */
public final class CultivationAttachments {
    public static final AttachmentType<SoilStore> SOIL =
            AttachmentRegistry.createPersistent(Cultivation.id("soil"), SoilStore.CODEC);

    /**
     * Per-player dietary fatigue (SPEC §3). Persistent but <b>not</b>
     * {@code copyOnDeath}: Fabric's attachment transfer copies it on a non-death
     * respawn (returning from the End) and drops it on death, which is exactly
     * "death clears the data; relog and restart persist it" — no respawn handler
     * of our own is needed.
     */
    public static final AttachmentType<DietData> DIET =
            AttachmentRegistry.createPersistent(Cultivation.id("diet"), DietData.CODEC);

    private CultivationAttachments() {
    }

    /** Forces class load (and with it attachment registration) from mod init. */
    public static void init() {
    }
}
