package io.github.bargainbinbastard.altus.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.bargainbinbastard.altus.net.HeldView;
import io.github.bargainbinbastard.altus.net.TomeSyncPayload;

/**
 * What the client knows about its own memories and Tome. Plain Java with no client-only classes,
 * so the network handlers that fill it are safe to register on a dedicated server.
 */
public final class ClientLore {
    private ClientLore() {}

    public static volatile List<HeldView> held = List.of();
    public static volatile long heldReceivedAtMillis;
    public static final AtomicInteger heldVersion = new AtomicInteger();

    public static volatile TomeSyncPayload lastTome;
    public static final AtomicInteger tomeVersion = new AtomicInteger();

    public static void acceptHeld(List<HeldView> entries) {
        held = List.copyOf(entries);
        heldReceivedAtMillis = System.currentTimeMillis();
        heldVersion.incrementAndGet();
    }

    public static void acceptTome(TomeSyncPayload payload) {
        lastTome = payload;
        tomeVersion.incrementAndGet();
    }

    /** Seconds left on a memory, counting down from when the server last reported it. */
    public static int secondsLeft(HeldView v) {
        long elapsed = (System.currentTimeMillis() - heldReceivedAtMillis) / 1000L;
        return (int) Math.max(0, v.seconds() - elapsed);
    }
}
