package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Memories a player has picked up but not yet written down. A memory gathered in the Altus is
 * "pending" (expire = -1) until the player wakes; then it starts fading.
 */
public final class HeldMemories implements INBTSerializable<CompoundTag> {
    public static final int CAP = 12;

    public static final class Memory {
        public final String id;
        public final String title;
        public final int level;
        /** Overworld game time when it fades, or -1 while still dreaming. */
        public long expire;

        public Memory(String id, String title, int level, long expire) {
            this.id = id;
            this.title = title;
            this.level = level;
            this.expire = expire;
        }

        public boolean pending() {
            return expire < 0;
        }
    }

    public final List<Memory> list = new ArrayList<>();

    public Memory find(String id) {
        for (Memory m : list) if (m.id.equals(id)) return m;
        return null;
    }

    public boolean remove(String id) {
        return list.removeIf(m -> m.id.equals(id));
    }

    /** Adds a memory, dropping the oldest if the player already carries the maximum. */
    public void add(Memory m) {
        if (find(m.id) != null) return;
        while (list.size() >= CAP) list.remove(0);
        list.add(m);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Memory m : list) {
            CompoundTag t = new CompoundTag();
            t.putString("id", m.id);
            t.putString("title", m.title);
            t.putInt("level", m.level);
            t.putLong("expire", m.expire);
            l.add(t);
        }
        CompoundTag out = new CompoundTag();
        out.put("memories", l);
        return out;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        list.clear();
        ListTag l = nbt.getList("memories", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            list.add(new Memory(t.getString("id"), t.getString("title"), t.getInt("level"), t.getLong("expire")));
        }
    }
}
