package io.github.bargainbinbastard.altus.dream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.AltusConfig;
import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.history.FocusPicker;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Item;
import io.github.bargainbinbastard.altus.history.Text;
import io.github.bargainbinbastard.altus.lore.Altars;
import io.github.bargainbinbastard.altus.lore.AltusWorld;
import io.github.bargainbinbastard.altus.lore.BoundItem;
import io.github.bargainbinbastard.altus.lore.Memories;
import io.github.bargainbinbastard.altus.lore.WorldHistory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Falling into the Altus and waking from it. */
public final class Dreams {
    private Dreams() {}

    public static AltusSession session(ServerPlayer sp) {
        return sp.getData(AltusAttachments.SESSION);
    }

    /**
     * Called once a sleeping player has slept long enough. Dreams only if a god's blocks are
     * strong enough around the bed, and at most once per night.
     */
    public static boolean tryBeginFromSleep(ServerPlayer sp) {
        AltusSession s = session(sp);
        if (s.active) return false;
        ServerLevel level = sp.serverLevel();
        if (AltusDimension.isAltus(level)) return false;
        long day = level.getDayTime() / 24000L;
        if (s.lastDreamDay == day) return false;
        BlockPos bed = sp.getSleepingPos().orElse(sp.blockPosition());
        History h = WorldHistory.get(sp.server);
        Map<String, Integer> counts = SleepScan.count(level, bed, AltusConfig.SCAN_RADIUS.get());
        FocusPicker.Focus focus = FocusPicker.pick(h, counts, AltusConfig.SCAN_THRESHOLD.get());
        if (!focus.dreams()) return false;
        s.lastDreamDay = day;
        sp.stopSleepInBed(false, true);
        begin(sp, focus.kind == FocusPicker.Kind.GOD ? focus.god : -1, AltusConfig.DREAM_SECONDS.get() * 20,
                bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5, intro(h, focus, counts));
        return true;
    }

    /** Starts a dream. The inventory is stashed before the player moves, so a crash can't lose it. */
    public static boolean begin(ServerPlayer sp, int focusGod, int ticks, double rx, double ry, double rz, Component intro) {
        AltusSession s = session(sp);
        if (s.active) return false;
        ServerLevel altus = AltusDimension.get(sp.server);
        if (altus == null) {
            AltusMod.LOGGER.error("The Altus dimension is missing; is the mod's data pack loaded?");
            return false;
        }
        s.active = true;
        s.returnDim = sp.level().dimension().location().toString();
        s.returnX = rx;
        s.returnY = ry;
        s.returnZ = rz;
        s.ticksLeft = ticks;
        s.focusGod = focusGod;
        s.stash = sp.getInventory().save(new ListTag());
        sp.getInventory().clearContent();
        BoundItem bound = Altars.bound(sp);
        s.carrying = !bound.stack.isEmpty();
        ItemStack carried = bound.stack.copy();
        bound.stack = ItemStack.EMPTY;
        if (s.carrying) {
            Altars.mark(carried);
            sp.getInventory().setItem(sp.getInventory().selected, carried);
        }
        BlockPos spot = AltusWorld.arrival(sp, focusGod, altus);
        sp.teleportTo(altus, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        sp.displayClientMessage(intro, false);
        if (s.carrying)
            sp.displayClientMessage(Component.literal("You carry the " + carried.getHoverName().getString() + " into the dream.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        AltusMod.LOGGER.info("{} entered the Altus (focus god {}, {} s)", sp.getGameProfile().getName(), focusGod, ticks / 20);
        return true;
    }

    /** Ends a dream: anything picked up in the Altus vanishes, and the stashed inventory returns. */
    public static void end(ServerPlayer sp, String reason) {
        AltusSession s = session(sp);
        if (!s.active) return;
        MinecraftServer server = sp.server;
        ServerLevel home = null;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.returnDim));
            home = server.getLevel(key);
        } catch (Exception ignored) {
            // fall through to the overworld
        }
        double x = s.returnX, y = s.returnY, z = s.returnZ;
        if (home == null || AltusDimension.isAltus(home)) {
            home = server.overworld();
            BlockPos spawn = home.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
        }
        ItemStack carriedBack = ItemStack.EMPTY;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack it = sp.getInventory().getItem(i);
            if (Altars.isCarried(it)) {
                carriedBack = it.copy();
                Altars.unmark(carriedBack);
                break;
            }
        }
        sp.getInventory().clearContent();
        sp.getInventory().load(s.stash);
        s.stash = new ListTag();
        if (!carriedBack.isEmpty()) {
            if (!sp.getInventory().add(carriedBack)) sp.drop(carriedBack, false);
        } else if (s.carrying)
            sp.displayClientMessage(Component.literal("The thing you carried did not come back with you.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        s.carrying = false;
        s.active = false;
        s.ticksLeft = 0;
        sp.teleportTo(home, x, y, z, sp.getYRot(), sp.getXRot());
        Memories.onWake(sp);
        String msg;
        switch (reason) {
            case "death": msg = "You wake with a start."; break;
            case "recover": msg = "You wake, unsure how long you slept."; break;
            case "command": msg = "You are pulled out of the dream."; break;
            default: msg = "You wake.";
        }
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        AltusMod.LOGGER.info("{} left the Altus ({})", sp.getGameProfile().getName(), reason);
    }

    /** Called every tick for a dreaming player. */
    public static void tickDream(ServerPlayer sp, AltusSession s) {
        if (!AltusDimension.isAltus(sp.level())) {
            end(sp, "recover");
            return;
        }
        s.ticksLeft--;
        if (s.ticksLeft <= 0) {
            end(sp, "timer");
            return;
        }
        String warn = null;
        if (s.ticksLeft == 600) warn = "The dream is thinning.";
        else if (s.ticksLeft == 200) warn = "You are beginning to wake.";
        else if (s.ticksLeft % 1200 == 0) {
            int minutes = s.ticksLeft / 1200;
            warn = "The dream will hold for " + minutes + (minutes == 1 ? " more minute." : " more minutes.");
        }
        if (warn != null) sp.displayClientMessage(Component.literal(warn).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    /** The first line a dreamer sees. Hints at the focus god through what it loves, never its name. */
    public static Component intro(History h, FocusPicker.Focus focus, Map<String, Integer> counts) {
        String text;
        if (focus.kind == FocusPicker.Kind.GOD) {
            History.God g = h.god(focus.god);
            List<String> felt = new ArrayList<>();
            for (Item l : g.likes)
                if (l.cat.equals("block") && counts.getOrDefault(l.id, 0) > 0) felt.add(l.label);
            text = felt.isEmpty() ? "You sink into a deep dream. Something dreams with you."
                    : "You sink into a deep dream. Something that loves " + Text.listPhrase(felt) + " dreams with you.";
        } else if (focus.kind == FocusPicker.Kind.TIE) {
            text = "You sink into a deep dream, pulled in more than one direction at once.";
        } else {
            text = "You sink into a deep dream.";
        }
        return Component.literal(text).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }
}
