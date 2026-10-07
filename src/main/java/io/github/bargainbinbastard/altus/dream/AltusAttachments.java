package io.github.bargainbinbastard.altus.dream;

import java.util.function.Supplier;

import io.github.bargainbinbastard.altus.AltusMod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attached to players. */
public final class AltusAttachments {
    private AltusAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AltusMod.MODID);

    public static final Supplier<AttachmentType<AltusSession>> SESSION =
            TYPES.register("session", () -> AttachmentType.serializable(AltusSession::new).copyOnDeath().build());
}
