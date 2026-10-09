package io.github.bargainbinbastard.altus.net;

import io.github.bargainbinbastard.altus.AltusMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: perform the rite of one entry in the Tome in a slot. */
public record RitePerformPayload(int slot, String entryId) implements CustomPacketPayload {
    public static final Type<RitePerformPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "rite_perform"));
    public static final StreamCodec<ByteBuf, RitePerformPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RitePerformPayload::slot,
            ByteBufCodecs.STRING_UTF8, RitePerformPayload::entryId,
            RitePerformPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
