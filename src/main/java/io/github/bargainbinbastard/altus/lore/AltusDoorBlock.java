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

/** A god's door, or a way out of a sanctum. Where it leads is decided by its position. */
public class AltusDoorBlock extends Block {
    public static final MapCodec<AltusDoorBlock> CODEC = simpleCodec(AltusDoorBlock::new);

    public AltusDoorBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) AltusWorld.useDoor(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
