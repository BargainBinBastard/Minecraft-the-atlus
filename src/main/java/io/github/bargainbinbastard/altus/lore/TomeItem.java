package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.client.ClientHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A book for writing down memories before they fade. Its pages are also free for ordinary writing. */
public class TomeItem extends Item {
    public TomeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) ClientHooks.openTome(hand);
        else if (player instanceof ServerPlayer sp) Memories.sync(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
