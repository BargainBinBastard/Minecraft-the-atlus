package io.github.bargainbinbastard.altus.lore;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers which version of the stage has been built into this world's Altus. */
public class AltusWorldData extends SavedData {
    public int stageVersion;

    public static AltusWorldData get(ServerLevel altus) {
        return altus.getDataStorage().computeIfAbsent(new SavedData.Factory<>(AltusWorldData::new, AltusWorldData::load), "altus_world");
    }

    static AltusWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        AltusWorldData d = new AltusWorldData();
        d.stageVersion = tag.getInt("stageVersion");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("stageVersion", stageVersion);
        return tag;
    }
}
