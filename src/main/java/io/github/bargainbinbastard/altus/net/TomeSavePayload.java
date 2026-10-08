package io.github.bargainbinbastard.altus.net;

import java.util.List;

import io.github.bargainbinbastard.altus.AltusMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the player's edits to a Tome's title and pages. */
public record TomeSavePayload(int slot, String title, List<String> pages) implements CustomPacketPayload {
    public static final Type<TomeSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "tome_save"));
    public static final StreamCodec<ByteBuf, TomeSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TomeSavePayload::slot,
            ByteBufCodecs.STRING_UTF8, TomeSavePayload::title,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TomeSavePayload::pages,
            TomeSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
