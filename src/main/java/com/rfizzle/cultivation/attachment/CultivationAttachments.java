package com.rfizzle.cultivation.attachment;

import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * The mod's Fabric data attachments. {@link #SOIL} is chunk-scoped and has no
 * initializer on purpose: an absent store means pristine defaults, so a world
 * where nobody farms carries zero Cultivation data. Never auto-attach — every
 * write goes through {@link SoilStores}.
 */
public final class CultivationAttachments {
    public static final AttachmentType<SoilStore> SOIL =
            AttachmentRegistry.createPersistent(Cultivation.id("soil"), SoilStore.CODEC);

    private CultivationAttachments() {
    }

    /** Forces class load (and with it attachment registration) from mod init. */
    public static void init() {
    }
}
