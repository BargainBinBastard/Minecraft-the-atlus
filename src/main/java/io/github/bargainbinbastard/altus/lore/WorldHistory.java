package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.HistorySimulator;
import net.minecraft.server.MinecraftServer;

/**
 * The history of the world currently being played. It is fully determined by the world seed,
 * so it is regenerated on demand rather than saved. Live changes during play will be layered
 * on top of this in a later version.
 */
public final class WorldHistory {
    private static History cached;
    private static long cachedSeed;

    private WorldHistory() {}

    public static synchronized History get(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        if (cached == null || cachedSeed != seed) {
            cached = HistorySimulator.simulate(Long.toString(seed));
            cachedSeed = seed;
        }
        return cached;
    }

    public static synchronized void clear() {
        cached = null;
    }
}
