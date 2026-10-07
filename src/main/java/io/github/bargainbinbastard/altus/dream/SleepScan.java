package io.github.bargainbinbastard.altus.dream;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.history.Content;
import io.github.bargainbinbastard.altus.history.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Counts the god-likeable blocks around a bed. Each likeable block id maps to a block tag in
 * data/altus/tags/block/likeable/, so packs can widen or narrow what counts.
 */
public final class SleepScan {
    private SleepScan() {}

    public static final Map<String, TagKey<Block>> TAGS = new LinkedHashMap<>();
    static {
        for (Item b : Content.BLOCKS)
            TAGS.put(b.id, TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(AltusMod.MODID, "likeable/" + b.id)));
    }

    public static Map<String, Integer> count(Level level, BlockPos center, int radius) {
        Map<String, Integer> counts = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockState st = level.getBlockState(p);
            if (st.isAir()) continue;
            for (Map.Entry<String, TagKey<Block>> e : TAGS.entrySet())
                if (st.is(e.getValue())) counts.merge(e.getKey(), 1, Integer::sum);
        }
        return counts;
    }
}
