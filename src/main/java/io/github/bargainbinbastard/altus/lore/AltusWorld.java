package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.dream.AltusDimension;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Builds the fixed features of the Altus the first time a world runs with the mod. */
public final class AltusWorld {
    private AltusWorld() {}

    public static final int GROUND_Y = 64;
    /** Where the four inscriptions stand, by facet. */
    public static final BlockPos[] INSCRIPTIONS = {
            new BlockPos(5, GROUND_Y, 0), new BlockPos(0, GROUND_Y, 5), new BlockPos(-5, GROUND_Y, 0), new BlockPos(0, GROUND_Y, -5)};
    public static final int CLEARING_RADIUS = 9;

    public static void prepare(MinecraftServer server) {
        ServerLevel altus = AltusDimension.get(server);
        if (altus == null) return;
        if (altus.getBlockState(INSCRIPTIONS[0]).is(AltusRegistry.INSCRIPTION.get())) return;
        int r = CLEARING_RADIUS;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                for (int y = GROUND_Y; y < GROUND_Y + 32; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!altus.getBlockState(p).isAir()) altus.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
        for (int i = 0; i < INSCRIPTIONS.length; i++)
            altus.setBlock(INSCRIPTIONS[i], AltusRegistry.INSCRIPTION.get().defaultBlockState().setValue(InscriptionBlock.FACET, i),
                    Block.UPDATE_ALL);
        AltusMod.LOGGER.info("The Altus: cleared the Clearing and carved its inscriptions.");
    }
}
