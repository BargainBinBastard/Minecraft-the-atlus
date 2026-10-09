package io.github.bargainbinbastard.altus.dream;

import java.util.function.Supplier;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.lore.BoundItem;
import io.github.bargainbinbastard.altus.lore.Devotion;
import io.github.bargainbinbastard.altus.lore.HeldMemories;
import io.github.bargainbinbastard.altus.lore.Knowledge;
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

    public static final Supplier<AttachmentType<HeldMemories>> HELD =
            TYPES.register("held", () -> AttachmentType.serializable(HeldMemories::new).copyOnDeath().build());

    public static final Supplier<AttachmentType<BoundItem>> BOUND =
            TYPES.register("bound", () -> AttachmentType.serializable(BoundItem::new).copyOnDeath().build());

    public static final Supplier<AttachmentType<Devotion>> DEVOTION =
            TYPES.register("devotion", () -> AttachmentType.serializable(Devotion::new).copyOnDeath().build());

    public static final Supplier<AttachmentType<Knowledge>> KNOWLEDGE =
            TYPES.register("knowledge", () -> AttachmentType.serializable(Knowledge::new).copyOnDeath().build());
}
