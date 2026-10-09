package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Wards of the Night: places where, for a day, no monster spawns. Saved with the world. */
public class Wards extends SavedData {
    public static final int RADIUS = 64;

    record Ward(String dim, BlockPos pos, long until) {}

    private final List<Ward> wards = new ArrayList<>();

    public static Wards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Wards::new, Wards::load), "altus_wards");
    }

    public void add(ServerLevel level, BlockPos pos, long until) {
        wards.removeIf(w -> w.until < level.getGameTime());
        wards.add(new Ward(level.dimension().location().toString(), pos.immutable(), until));
        setDirty();
    }

    public boolean warded(ServerLevel level, double x, double y, double z) {
        String dim = level.dimension().location().toString();
        long now = level.getServer().overworld().getGameTime();
        for (Ward w : wards)
            if (w.until > now && w.dim.equals(dim) && w.pos.distToCenterSqr(x, y, z) < (double) RADIUS * RADIUS) return true;
        return false;
    }

    static Wards load(CompoundTag tag, HolderLookup.Provider provider) {
        Wards w = new Wards();
        for (Tag t : tag.getList("wards", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            w.wards.add(new Ward(c.getString("dim"), new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")), c.getLong("until")));
        }
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Ward w : wards) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", w.dim);
            c.putInt("x", w.pos.getX());
            c.putInt("y", w.pos.getY());
            c.putInt("z", w.pos.getZ());
            c.putLong("until", w.until);
            l.add(c);
        }
        tag.put("wards", l);
        return tag;
    }
}
