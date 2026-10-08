package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.HistorySimulator;
import io.github.bargainbinbastard.altus.history.Sites;
import net.minecraft.server.MinecraftServer;

/**
 * The history of the world currently being played, and who holds which door. Both are fully
 * determined by the world seed, so they are regenerated on demand rather than saved.
 */
public final class WorldHistory {
    private static History cached;
    private static Sites cachedSites;
    private static long cachedSeed;

    private WorldHistory() {}

    public static synchronized History get(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        if (cached == null || cachedSeed != seed) {
            cached = HistorySimulator.simulate(Long.toString(seed));
            cachedSites = Sites.assign(cached);
            cachedSeed = seed;
        }
        return cached;
    }

    public static synchronized Sites sites(MinecraftServer server) {
        get(server);
        return cachedSites;
    }

    public static synchronized void clear() {
        cached = null;
        cachedSites = null;
    }
}
