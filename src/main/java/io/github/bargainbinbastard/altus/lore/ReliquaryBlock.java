package io.github.bargainbinbastard.altus.lore;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A sealed casket in a sanctum's archive. It opens only for those who know its keeper well. */
public class ReliquaryBlock extends Block {
    public static final MapCodec<ReliquaryBlock> CODEC = simpleCodec(ReliquaryBlock::new);

    public ReliquaryBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) AltusWorld.useReliquary(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
