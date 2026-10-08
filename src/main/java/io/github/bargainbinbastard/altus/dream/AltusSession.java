package io.github.bargainbinbastard.altus.dream;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * A player's dream state. Saved with the player, so a dream survives logging out and a crash
 * can never lose the inventory stashed at the start of a dream.
 */
public final class AltusSession implements INBTSerializable<CompoundTag> {
    public boolean active;
    /** Where to wake up. */
    public String returnDim = "";
    public double returnX, returnY, returnZ;
    public int ticksLeft;
    /** The god this dream leans toward, or -1. */
    public int focusGod = -1;
    /** The inventory held at the moment of falling asleep. */
    public ListTag stash = new ListTag();
    /** Whether this dream began with a thing carried in from an altar. */
    public boolean carrying;
    /** Overworld day number of the last dream, so a player dreams at most once a night. */
    public long lastDreamDay = -1;

    /** Not saved: whether this stretch of sleep has already been checked for a dream. */
    public transient boolean sleepChecked;

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("active", active);
        t.putString("returnDim", returnDim);
        t.putDouble("returnX", returnX);
        t.putDouble("returnY", returnY);
        t.putDouble("returnZ", returnZ);
        t.putInt("ticksLeft", ticksLeft);
        t.putInt("focusGod", focusGod);
        t.put("stash", stash);
        t.putLong("lastDreamDay", lastDreamDay);
        t.putBoolean("carrying", carrying);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        active = t.getBoolean("active");
        returnDim = t.getString("returnDim");
        returnX = t.getDouble("returnX");
        returnY = t.getDouble("returnY");
        returnZ = t.getDouble("returnZ");
        ticksLeft = t.getInt("ticksLeft");
        focusGod = t.contains("focusGod") ? t.getInt("focusGod") : -1;
        stash = t.getList("stash", Tag.TAG_COMPOUND);
        lastDreamDay = t.contains("lastDreamDay") ? t.getLong("lastDreamDay") : -1;
        carrying = t.getBoolean("carrying");
    }
}
