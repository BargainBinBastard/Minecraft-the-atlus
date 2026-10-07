package io.github.bargainbinbastard.altus.net;

import java.util.List;

import io.github.bargainbinbastard.altus.AltusMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the memories a player carries. */
public record HeldSyncPayload(List<HeldView> entries) implements CustomPacketPayload {
    public static final Type<HeldSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "held_sync"));
    public static final StreamCodec<ByteBuf, HeldSyncPayload> STREAM_CODEC =
            HeldView.STREAM_CODEC.apply(ByteBufCodecs.list()).map(HeldSyncPayload::new, HeldSyncPayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
