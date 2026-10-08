package io.github.bargainbinbastard.altus.lore;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** The one thing a player has left on an altar, to carry into their next dream. */
public final class BoundItem implements INBTSerializable<CompoundTag> {
    public ItemStack stack = ItemStack.EMPTY;

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        Tag item = stack.saveOptional(provider);
        t.put("item", item);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        stack = ItemStack.parseOptional(provider, nbt.getCompound("item"));
    }
}
