package io.github.bargainbinbastard.altus.lore;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A carved stone in the Woods. Its facet decides what it tells of the dream's focus god:
 * 0 what it loves, 1 what it hates, 2 its nature, 3 its origin.
 */
public class InscriptionBlock extends Block {
    public static final MapCodec<InscriptionBlock> CODEC = simpleCodec(InscriptionBlock::new);
    public static final IntegerProperty FACET = IntegerProperty.create("facet", 0, 3);

    public InscriptionBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACET, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACET);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            String mural = AltusWorld.muralAt(pos);
            if (mural != null) Memories.take(sp, mural, "mural");
            else Memories.readInscription(sp, state.getValue(FACET));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
