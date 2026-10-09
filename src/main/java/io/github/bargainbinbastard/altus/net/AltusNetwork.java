package io.github.bargainbinbastard.altus.net;

import io.github.bargainbinbastard.altus.client.ClientLore;
import io.github.bargainbinbastard.altus.lore.TomeService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's network messages. */
public final class AltusNetwork {
    private AltusNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(HeldSyncPayload.TYPE, HeldSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientLore.acceptHeld(p.entries())));
        r.playToClient(TomeSyncPayload.TYPE, TomeSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientLore.acceptTome(p)));
        r.playToServer(TomeSavePayload.TYPE, TomeSavePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) TomeService.save(sp, p.slot(), p.title(), p.pages());
        }));
        r.playToServer(TomeWritePayload.TYPE, TomeWritePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) TomeService.write(sp, p.slot(), p.page(), p.testimonyId(), p.pages());
        }));
        r.playToClient(RiteListPayload.TYPE, RiteListPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientLore.acceptRites(p)));
        r.playToServer(RitePerformPayload.TYPE, RitePerformPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) io.github.bargainbinbastard.altus.lore.RiteService.perform(sp, p.slot(), p.entryId());
        }));
    }
}
