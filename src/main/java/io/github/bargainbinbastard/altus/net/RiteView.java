package io.github.bargainbinbastard.altus.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One rite a Tome entry can perform, as the Tome screen lists it. */
public record RiteView(String entryId, String title, String effect, String needs, boolean ready, String note) {
    public static final StreamCodec<ByteBuf, RiteView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RiteView::entryId,
            ByteBufCodecs.STRING_UTF8, RiteView::title,
            ByteBufCodecs.STRING_UTF8, RiteView::effect,
            ByteBufCodecs.STRING_UTF8, RiteView::needs,
            ByteBufCodecs.BOOL, RiteView::ready,
            ByteBufCodecs.STRING_UTF8, RiteView::note,
            RiteView::new);
}
