package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** How the gods notice what their followers do in the waking world, and what follows from favor. */
public final class DevotionEvents {
    private DevotionEvents() {}

    public static final int KILL_FAVOR_CAP = 10;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onKill);
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onTarget);
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onSpawn);
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(DevotionEvents::onTick);
    }

    static boolean likes(History.God g, String id) {
        return id != null && g.likes.stream().anyMatch(i -> i.id.equals(id));
    }

    static boolean dislikes(History.God g, String id) {
        return id != null && g.dislikes.stream().anyMatch(i -> i.id.equals(id));
    }

    /** Taboo: a disciple builds with something its patron cannot abide. */
    public static void placed(ServerPlayer sp, net.minecraft.world.level.block.state.BlockState state) {
        if (Favor.tier(sp) < 2) return;
        Devotion d = Favor.of(sp);
        History h = WorldHistory.get(sp.server);
        String like = LikeMatch.of(state);
        if (!dislikes(h.god(d.patron), like)) return;
        Favor.add(sp, d.patron, -3);
        warn(sp, "Your patron cannot abide " + LikeMatch.name(like) + ". (favor " + Favor.favor(sp, d.patron) + ")");
    }

    /** A follower kills a creature: its patron is pleased if it hates the creature, and a disciple breaks a taboo if it loves it. */
    public static void killed(ServerPlayer sp, net.minecraft.world.entity.EntityType<?> type) {
        int t = Favor.tier(sp);
        if (t < 1) return;
        Devotion d = Favor.of(sp);
        History.God g = WorldHistory.get(sp.server).god(d.patron);
        String like = LikeMatch.of(type);
        if (likes(g, like) && t >= 2) {
            Favor.add(sp, d.patron, -5);
            warn(sp, Text.cap(g.name) + " loves " + LikeMatch.name(like) + ", and you have killed one. (favor " + Favor.favor(sp, d.patron) + ")");
        } else if (dislikes(g, like)) {
            long day = RiteService.day(sp);
            if (d.killDay != day) {
                d.killDay = day;
                d.killFavorToday = 0;
            }
            if (d.killFavorToday < KILL_FAVOR_CAP) {
                d.killFavorToday++;
                Favor.add(sp, d.patron, 1);
            }
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) placed(sp, e.getPlacedBlock());
    }

    private static void onKill(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer sp && !(e.getEntity() instanceof ServerPlayer)) killed(sp, e.getEntity().getType());
    }

    private static void onTarget(LivingChangeTargetEvent e) {
        if (Guardians.isGuardian(e.getEntity()) && e.getNewAboutToBeSetTarget() instanceof ServerPlayer sp && Guardians.spares(e.getEntity(), sp))
            e.setCanceled(true);
    }

    private static void onSpawn(FinalizeSpawnEvent e) {
        if (e.getSpawnType() != MobSpawnType.NATURAL || !(e.getEntity() instanceof Enemy)) return;
        ServerLevel level = e.getLevel().getLevel();
        if (Wards.get(level.getServer()).warded(level, e.getX(), e.getY(), e.getZ())) e.setSpawnCancelled(true);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Favor.applyGift(sp);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Favor.applyGift(sp);
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 100 == 0) Favor.applyGift(sp);
    }

    private static void warn(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
    }
}
