package io.github.bargainbinbastard.altus.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A held memory as the client sees it. */
public record HeldView(String id, String title, int seconds, boolean pending) {
    public static final StreamCodec<ByteBuf, HeldView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HeldView::id,
            ByteBufCodecs.STRING_UTF8, HeldView::title,
            ByteBufCodecs.VAR_INT, HeldView::seconds,
            ByteBufCodecs.BOOL, HeldView::pending,
            HeldView::new);
}
