package io.github.bargainbinbastard.altus.lore;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** A player's standing with the gods: favor with each, their patron, and what their rites have set in motion. */
public final class Devotion implements INBTSerializable<CompoundTag> {
    public final Map<Integer, Integer> favor = new HashMap<>();
    public int patron = -1;
    /** The gift effect currently applied for being a disciple, or "". */
    public String gift = "";
    public long offeringDay = -1;
    public final Map<Integer, Integer> offeredToday = new HashMap<>();
    public long killDay = -1;
    public int killFavorToday;
    /** Overworld day each rite (by testimony id) was last performed. */
    public final Map<String, Long> riteDay = new HashMap<>();
    /** Set by rites, used up by the next dream. */
    public int callFocus = -1, unseal = -1;
    public boolean longDream;

    public int favorOf(int god) {
        return favor.getOrDefault(god, 0);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        CompoundTag f = new CompoundTag();
        favor.forEach((g, v) -> f.putInt(Integer.toString(g), v));
        t.put("favor", f);
        t.putInt("patron", patron);
        t.putString("gift", gift);
        t.putLong("offeringDay", offeringDay);
        CompoundTag o = new CompoundTag();
        offeredToday.forEach((g, v) -> o.putInt(Integer.toString(g), v));
        t.put("offeredToday", o);
        t.putLong("killDay", killDay);
        t.putInt("killFavorToday", killFavorToday);
        CompoundTag r = new CompoundTag();
        riteDay.forEach(r::putLong);
        t.put("riteDay", r);
        t.putInt("callFocus", callFocus);
        t.putInt("unseal", unseal);
        t.putBoolean("longDream", longDream);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        favor.clear();
        CompoundTag f = t.getCompound("favor");
        for (String k : f.getAllKeys()) favor.put(Integer.parseInt(k), f.getInt(k));
        patron = t.contains("patron") ? t.getInt("patron") : -1;
        gift = t.getString("gift");
        offeringDay = t.contains("offeringDay") ? t.getLong("offeringDay") : -1;
        offeredToday.clear();
        CompoundTag o = t.getCompound("offeredToday");
        for (String k : o.getAllKeys()) offeredToday.put(Integer.parseInt(k), o.getInt(k));
        killDay = t.contains("killDay") ? t.getLong("killDay") : -1;
        killFavorToday = t.getInt("killFavorToday");
        riteDay.clear();
        CompoundTag r = t.getCompound("riteDay");
        for (String k : r.getAllKeys()) riteDay.put(k, r.getLong(k));
        callFocus = t.contains("callFocus") ? t.getInt("callFocus") : -1;
        unseal = t.contains("unseal") ? t.getInt("unseal") : -1;
        longDream = t.getBoolean("longDream");
    }
}
