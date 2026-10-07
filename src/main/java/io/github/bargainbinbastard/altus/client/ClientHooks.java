package io.github.bargainbinbastard.altus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only actions, kept in one class that the server never loads. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openTome(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TomeScreen(hand));
    }
}
