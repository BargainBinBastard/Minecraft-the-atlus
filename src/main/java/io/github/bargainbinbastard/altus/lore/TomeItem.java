package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.client.ClientHooks;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.network.chat.Component;
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

    /** A titled Tome shows its title as its name. An anvil rename still takes precedence. */
    @Override
    public Component getName(ItemStack stack) {
        TomeContents c = stack.get(AltusRegistry.TOME_CONTENTS.get());
        if (c != null && !c.title().isBlank()) return Component.literal(c.title());
        return super.getName(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) ClientHooks.openTome(hand);
        else if (player instanceof ServerPlayer sp) Memories.sync(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
