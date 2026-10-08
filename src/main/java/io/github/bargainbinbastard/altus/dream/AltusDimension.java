package io.github.bargainbinbastard.altus.dream;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.history.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** The Altus dimension, defined in data/altus/dimension/altus.json. */
public final class AltusDimension {
    private AltusDimension() {}

    public static final ResourceKey<Level> KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "altus"));

    public static ServerLevel get(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean isAltus(Level level) {
        return level.dimension().equals(KEY);
    }

    /** A standing spot in the Clearing, in the Woods south of the Mountain. */
    public static BlockPos clearing(ServerLevel altus, RandomSource random) {
        int x = Terrain.CLEARING_X + random.nextInt(13) - 6;
        int z = Terrain.CLEARING_Z + random.nextInt(13) - 6;
        // Level#getHeight reports the world floor for chunks that aren't loaded, so load it first.
        altus.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int y = altus.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
