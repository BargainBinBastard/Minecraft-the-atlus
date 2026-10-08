package io.github.bargainbinbastard.altus.lore;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;

/** Calls up a god's guardians when a dreamer comes near. The calling is done by {@link Guardians}. */
public class WardenStoneBlock extends Block {
    public static final MapCodec<WardenStoneBlock> CODEC = simpleCodec(WardenStoneBlock::new);

    public WardenStoneBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
