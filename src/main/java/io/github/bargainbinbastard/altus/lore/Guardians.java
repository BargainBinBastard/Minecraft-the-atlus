package io.github.bargainbinbastard.altus.lore;

import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Warden Stones call up a god's guardians when a dreamer comes near: the hostile creatures it
 * likes, or zombies if it likes none. Mightier gods are better guarded.
 */
public final class Guardians {
    private Guardians() {}

    public static final String TAG = "altus_guardian";
    public static final int WAKE_RANGE = 14;

    public static EntityType<? extends Mob> typeFor(History.God g) {
        for (Item l : g.likes) {
            if (!l.cat.equals("entity")) continue;
            switch (l.id) {
                case "skeleton": return EntityType.SKELETON;
                case "spider": return EntityType.SPIDER;
                case "drowned": return EntityType.DROWNED;
                case "enderman": return EntityType.ENDERMAN;
                case "phantom": return EntityType.PHANTOM;
                case "witch": return EntityType.WITCH;
                default: break;
            }
        }
        return EntityType.ZOMBIE;
    }

    public static int capFor(History.God g) {
        return Math.max(1, Math.min(4, 1 + g.power / 6));
    }

    /** Runs every couple of seconds in the Altus: each stone near a dreamer tops up its guardians. */
    public static void tick(ServerLevel altus, History h) {
        for (Map.Entry<BlockPos, Integer> e : AltusWorld.wardens().entrySet()) {
            BlockPos stone = e.getKey();
            if (!altus.isLoaded(stone) || !dreamerNear(altus, stone, WAKE_RANGE)) continue;
            History.God g = h.god(e.getValue());
            List<Mob> near = altus.getEntitiesOfClass(Mob.class, new AABB(stone).inflate(WAKE_RANGE + 4), m -> m.getTags().contains(TAG));
            if (near.size() >= capFor(g)) continue;
            spawn(altus, stone, typeFor(g));
        }
    }

    static boolean dreamerNear(ServerLevel l, BlockPos p, int range) {
        for (ServerPlayer sp : l.players())
            if (!sp.isSpectator() && !sp.isCreative() && sp.blockPosition().closerThan(p, range)) return true;
        return false;
    }

    /** Calls one guardian beside a stone. Returns it, or null if there was no room. */
    public static Mob spawn(ServerLevel l, BlockPos stone, EntityType<? extends Mob> type) {
        for (int i = 0; i < 12; i++) {
            BlockPos p = stone.offset(l.random.nextInt(7) - 3, l.random.nextInt(3) - 1, l.random.nextInt(7) - 3);
            if (!l.getBlockState(p).isAir() || !l.getBlockState(p.above()).isAir()) continue;
            if (l.getBlockState(p.below()).getCollisionShape(l, p.below()).isEmpty()) continue;
            Mob mob = type.create(l);
            if (mob == null) return null;
            mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, l.random.nextFloat() * 360f, 0f);
            EventHooks.finalizeMobSpawn(mob, l, l.getCurrentDifficultyAt(p), MobSpawnType.SPAWNER, null);
            mob.setPersistenceRequired();
            mob.addTag(TAG);
            mob.restrictTo(stone, 10);
            l.addFreshEntity(mob);
            l.levelEvent(LevelEvent.PARTICLES_MOBBLOCK_SPAWN, p, 0);
            return mob;
        }
        return null;
    }

    public static boolean isGuardian(Entity e) {
        return e.getTags().contains(TAG);
    }
}
