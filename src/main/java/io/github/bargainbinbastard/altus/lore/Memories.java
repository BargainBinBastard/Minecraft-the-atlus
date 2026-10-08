package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.List;

import io.github.bargainbinbastard.altus.AltusConfig;
import io.github.bargainbinbastard.altus.dream.AltusAttachments;
import io.github.bargainbinbastard.altus.dream.AltusSession;
import io.github.bargainbinbastard.altus.dream.Dreams;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Lore;
import io.github.bargainbinbastard.altus.net.HeldSyncPayload;
import io.github.bargainbinbastard.altus.net.HeldView;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.PacketDistributor;

/** Memories: picking them up in the Altus, waking with them, and losing them if they aren't written down. */
public final class Memories {
    private Memories() {}

    public static final String TOME_HINT = "Craft a Tome from a book and quill surrounded by rotten flesh.";

    public enum Gain { ADDED, ALREADY_HELD, ALREADY_KNOWN }

    public static HeldMemories held(ServerPlayer sp) {
        return sp.getData(AltusAttachments.HELD);
    }

    public static Knowledge knowledge(ServerPlayer sp) {
        return sp.getData(AltusAttachments.KNOWLEDGE);
    }

    public static long now(ServerPlayer sp) {
        return sp.server.overworld().getGameTime();
    }

    /** How long a memory of a given level lasts after waking, in ticks. */
    public static long fadeTicks(int level) {
        return AltusConfig.FADE_MINUTES.get() * 60L * 20L;
    }

    /** Picks up a memory. In the Altus it waits, unreadable, until the player wakes. */
    public static Gain gain(ServerPlayer sp, Lore.Testimony t) {
        if (knowledge(sp).written.contains(t.id)) return Gain.ALREADY_KNOWN;
        HeldMemories h = held(sp);
        if (h.find(t.id) != null) return Gain.ALREADY_HELD;
        boolean dreaming = Dreams.session(sp).active;
        h.add(new HeldMemories.Memory(t.id, t.title, t.level, dreaming ? -1 : now(sp) + fadeTicks(t.level)));
        sync(sp);
        if (!dreaming) updateEffect(sp);
        return Gain.ADDED;
    }

    /** Reading one of the Woods inscriptions. It speaks of the god the dream leans toward. */
    public static void readInscription(ServerPlayer sp, int facet) {
        AltusSession s = Dreams.session(sp);
        if (!s.active) {
            say(sp, "The carving is worn past reading.");
            return;
        }
        History h = WorldHistory.get(sp.server);
        int god = s.focusGod >= 0 && s.focusGod < h.gods.size() ? s.focusGod : Lore.woodsFallbackGod(h);
        if (god < 0 || Lore.hidden(h, god)) {
            say(sp, "The carving has been scraped away.");
            return;
        }
        Lore.Testimony t = Lore.render(h, Lore.woodsId(facet, god));
        if (t == null) {
            say(sp, "The carving has been scraped away.");
            return;
        }
        switch (gain(sp, t)) {
            case ADDED -> say(sp, "You trace the carving. Its meaning slips past you, but you will remember it when you wake.");
            case ALREADY_HELD -> say(sp, "You already carry this memory.");
            case ALREADY_KNOWN -> say(sp, "You have already written this down.");
        }
    }

    /** On waking, memories gathered in the dream become readable and start to fade. */
    public static void onWake(ServerPlayer sp) {
        HeldMemories h = held(sp);
        long now = now(sp);
        List<String> titles = new ArrayList<>();
        for (HeldMemories.Memory m : h.list)
            if (m.pending()) {
                m.expire = now + fadeTicks(m.level);
                titles.add(m.title);
            }
        if (!titles.isEmpty()) {
            int minutes = AltusConfig.FADE_MINUTES.get();
            sp.displayClientMessage(Component.literal("You wake remembering " + (titles.size() == 1 ? "something" : titles.size() + " things")
                    + ": " + String.join("; ", titles) + ". The memories will fade within " + minutes
                    + " minutes unless you write them in a Tome.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            if (!hasTome(sp)) sp.displayClientMessage(Component.literal(TOME_HINT).withStyle(ChatFormatting.GRAY), false);
        }
        sync(sp);
        updateEffect(sp);
    }

    /** Runs every second for each player: lets old memories fade. */
    public static void tick(ServerPlayer sp) {
        HeldMemories h = held(sp);
        if (h.list.isEmpty()) {
            if (sp.hasEffect(AltusRegistry.FADING_MEMORY)) sp.removeEffect(AltusRegistry.FADING_MEMORY);
            return;
        }
        long now = now(sp);
        List<String> faded = new ArrayList<>();
        h.list.removeIf(m -> {
            boolean gone = !m.pending() && m.expire <= now;
            if (gone) faded.add(m.title);
            return gone;
        });
        if (!faded.isEmpty()) {
            for (String t : faded)
                sp.displayClientMessage(Component.literal("A memory fades: " + t + ".").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            sp.displayClientMessage(Component.literal("Write memories in a Tome before they fade. " + TOME_HINT)
                    .withStyle(ChatFormatting.GRAY), false);
            sync(sp);
        }
        updateEffect(sp);
    }

    public static boolean hasTome(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++)
            if (sp.getInventory().getItem(i).is(AltusRegistry.TOME.get())) return true;
        return false;
    }

    /** Keeps the Fading Memory effect's timer in step with the longest-lasting memory. */
    public static void updateEffect(ServerPlayer sp) {
        long now = now(sp);
        long longest = 0;
        for (HeldMemories.Memory m : held(sp).list) if (!m.pending()) longest = Math.max(longest, m.expire - now);
        MobEffectInstance cur = sp.getEffect(AltusRegistry.FADING_MEMORY);
        if (longest <= 0) {
            if (cur != null) sp.removeEffect(AltusRegistry.FADING_MEMORY);
            return;
        }
        int ticks = (int) Math.min(Integer.MAX_VALUE, longest);
        if (cur == null || Math.abs(cur.getDuration() - ticks) > 40)
            sp.addEffect(new MobEffectInstance(AltusRegistry.FADING_MEMORY, ticks, 0, false, false, true));
    }

    /** Sends the player's held memories to their client, if it has the mod's channel. */
    public static void sync(ServerPlayer sp) {
        if (sp.connection == null || !sp.connection.hasChannel(HeldSyncPayload.TYPE)) return;
        long now = now(sp);
        List<HeldView> views = new ArrayList<>();
        for (HeldMemories.Memory m : held(sp).list)
            views.add(new HeldView(m.id, m.title, m.pending() ? 0 : (int) Math.max(0, (m.expire - now) / 20), m.pending()));
        PacketDistributor.sendToPlayer(sp, new HeldSyncPayload(views));
    }

    private static void say(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
