package io.github.bargainbinbastard.altus.dream;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game events that drive dreaming. */
public final class DreamEvents {
    private DreamEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DreamEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onLogin);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        AltusSession s = Dreams.session(sp);
        if (s.active) {
            Dreams.tickDream(sp, s);
            return;
        }
        if (!sp.isSleeping()) {
            s.sleepChecked = false;
            return;
        }
        if (sp.isSleepingLongEnough() && !s.sleepChecked) {
            s.sleepChecked = true;
            Dreams.tryBeginFromSleep(sp);
        }
    }

    /** Dying in the Altus costs nothing: the dreamer simply wakes. */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        AltusSession s = Dreams.session(sp);
        if (!s.active || !AltusDimension.isAltus(sp.level())) return;
        event.setCanceled(true);
        sp.setHealth(sp.getMaxHealth());
        sp.clearFire();
        sp.removeAllEffects();
        Dreams.end(sp, "death");
    }

    /** Repairs anything a crash or a disconnect left half-finished. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        AltusSession s = Dreams.session(sp);
        boolean inAltus = AltusDimension.isAltus(sp.level());
        if (s.active && !inAltus) {
            Dreams.end(sp, "recover");
        } else if (s.active) {
            sp.displayClientMessage(Component.literal("You are still dreaming."), true);
        } else if (inAltus) {
            ServerLevel ow = sp.server.overworld();
            BlockPos spawn = ow.getSharedSpawnPos();
            sp.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            sp.displayClientMessage(Component.literal("You wake, unsure how long you slept."), false);
        }
    }
}
