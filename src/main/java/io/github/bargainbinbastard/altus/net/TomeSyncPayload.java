package io.github.bargainbinbastard.altus.net;

import java.util.List;

import io.github.bargainbinbastard.altus.AltusMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a Tome's pages after the server changed them, and the page to show. */
public record TomeSyncPayload(int slot, String title, List<String> pages, int page) implements CustomPacketPayload {
    public static final Type<TomeSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "tome_sync"));
    public static final StreamCodec<ByteBuf, TomeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TomeSyncPayload::slot,
            ByteBufCodecs.STRING_UTF8, TomeSyncPayload::title,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TomeSyncPayload::pages,
            ByteBufCodecs.VAR_INT, TomeSyncPayload::page,
            TomeSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
