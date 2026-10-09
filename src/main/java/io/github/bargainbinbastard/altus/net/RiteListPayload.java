package io.github.bargainbinbastard.altus.net;

import java.util.List;

import io.github.bargainbinbastard.altus.AltusMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the rites the Tome in a slot can perform. */
public record RiteListPayload(int slot, List<RiteView> rites) implements CustomPacketPayload {
    public static final Type<RiteListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "rite_list"));
    public static final StreamCodec<ByteBuf, RiteListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RiteListPayload::slot,
            RiteView.STREAM_CODEC.apply(ByteBufCodecs.list()), RiteListPayload::rites,
            RiteListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
