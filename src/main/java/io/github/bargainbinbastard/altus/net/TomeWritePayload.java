package io.github.bargainbinbastard.altus.net;

import java.util.List;

import io.github.bargainbinbastard.altus.AltusMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: write a held memory into a Tome, with the current pages so edits aren't lost. */
public record TomeWritePayload(int slot, int page, String testimonyId, List<String> pages) implements CustomPacketPayload {
    public static final Type<TomeWritePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "tome_write"));
    public static final StreamCodec<ByteBuf, TomeWritePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TomeWritePayload::slot,
            ByteBufCodecs.VAR_INT, TomeWritePayload::page,
            ByteBufCodecs.STRING_UTF8, TomeWritePayload::testimonyId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TomeWritePayload::pages,
            TomeWritePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
