package io.github.bargainbinbastard.altus.dream;

import io.github.bargainbinbastard.altus.lore.Altars;
import io.github.bargainbinbastard.altus.lore.Echoes;
import io.github.bargainbinbastard.altus.lore.Guardians;
import io.github.bargainbinbastard.altus.lore.Memories;
import io.github.bargainbinbastard.altus.lore.WorldHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game events that drive dreaming. */
public final class DreamEvents {
    private DreamEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DreamEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onToss);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(DreamEvents::onEntityInteract);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 20 == 0) Memories.tick(sp);
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

    /** The thing carried into a dream will not leave the dreamer's hands there. */
    private static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp) || !AltusDimension.isAltus(sp.level())) return;
        ItemStack stack = event.getEntity().getItem();
        if (!Altars.isCarried(stack)) return;
        event.setCanceled(true);
        if (!sp.getInventory().add(stack.copy())) sp.getInventory().setItem(sp.getInventory().selected, stack.copy());
        sp.displayClientMessage(Component.literal("It will not leave your hand here."), true);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel l) || !AltusDimension.isAltus(l)) return;
        if (l.getGameTime() % 40 == 0) {
            Guardians.tick(l, WorldHistory.get(l.getServer()));
            Echoes.tick(l, WorldHistory.get(l.getServer()));
        }
    }

    /** Speaking to an echo or the Archivist. */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !Echoes.isEcho(event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() == InteractionHand.MAIN_HAND) Echoes.interact(sp, event.getTarget());
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
        Memories.sync(sp);
        Memories.updateEffect(sp);
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
