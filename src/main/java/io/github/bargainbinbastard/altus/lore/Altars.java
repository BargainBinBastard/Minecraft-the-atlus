package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.dream.AltusAttachments;
import io.github.bargainbinbastard.altus.dream.AltusDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The altar's rite: leave one thing on it, and it goes with you into your next dream. */
public final class Altars {
    private Altars() {}

    public static final String CARRIED = "altus_carried";

    public static BoundItem bound(ServerPlayer sp) {
        return sp.getData(AltusAttachments.BOUND);
    }

    /** Leaves one of the held item on the altar. Returns true if something was bound. */
    public static boolean bind(ServerPlayer sp, ItemStack held) {
        if (AltusDimension.isAltus(sp.level())) {
            say(sp, "The altar is silent here.");
            return false;
        }
        BoundItem b = bound(sp);
        if (!b.stack.isEmpty()) {
            say(sp, "The altar already holds your " + b.stack.getHoverName().getString() + ". Take it back first, with an empty hand.");
            return false;
        }
        if (held.isEmpty()) return false;
        b.stack = held.copyWithCount(1);
        if (!sp.getAbilities().instabuild) held.shrink(1);
        sp.displayClientMessage(Component.literal("You leave the " + b.stack.getHoverName().getString()
                + " on the altar. If you dream tonight, it will be with you.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
        return true;
    }

    /** Takes back whatever the player left on the altar. */
    public static void reclaim(ServerPlayer sp) {
        BoundItem b = bound(sp);
        if (b.stack.isEmpty()) {
            say(sp, "Lay one thing on the altar, and it will go with you into your next dream.");
            return;
        }
        ItemStack s = b.stack;
        b.stack = ItemStack.EMPTY;
        if (!sp.getInventory().add(s)) sp.drop(s, false);
        say(sp, "You take back the " + s.getHoverName().getString() + ".");
    }

    public static boolean isCarried(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(CARRIED);
    }

    public static void mark(ItemStack s) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putBoolean(CARRIED, true);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
    }

    public static void unmark(ItemStack s) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.remove(CARRIED);
        CustomData.set(DataComponents.CUSTOM_DATA, s, t);
    }

    private static void say(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
