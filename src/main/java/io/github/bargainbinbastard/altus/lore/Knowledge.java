package io.github.bargainbinbastard.altus.lore;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * What a player has learned, as far as the gods are concerned: the testimonies they have written
 * into a Tome, and how well they understand each god. Gates in the Altus will read this.
 */
public final class Knowledge implements INBTSerializable<CompoundTag> {
    public final Set<String> written = new LinkedHashSet<>();
    public final Map<Integer, Integer> understanding = new HashMap<>();

    public int understandingOf(int god) {
        return understanding.getOrDefault(god, 0);
    }

    public boolean knowsGod(int god) {
        return understandingOf(god) > 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag out = new CompoundTag();
        ListTag w = new ListTag();
        for (String s : written) w.add(StringTag.valueOf(s));
        out.put("written", w);
        CompoundTag u = new CompoundTag();
        for (Map.Entry<Integer, Integer> e : understanding.entrySet()) u.putInt(Integer.toString(e.getKey()), e.getValue());
        out.put("understanding", u);
        return out;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        written.clear();
        understanding.clear();
        ListTag w = nbt.getList("written", Tag.TAG_STRING);
        for (int i = 0; i < w.size(); i++) written.add(w.getString(i));
        CompoundTag u = nbt.getCompound("understanding");
        for (String k : u.getAllKeys()) {
            try {
                understanding.put(Integer.parseInt(k), u.getInt(k));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }
}
