package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Lore;
import io.github.bargainbinbastard.altus.history.Sites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * The echoes of the gods, who speak their own accounts in their sanctums, and the Archivist in the
 * House, who points dreamers toward what they have yet to find. They appear when a dreamer comes
 * near and stand perfectly still.
 */
public final class Echoes {
    private Echoes() {}

    public static final String TAG = "altus_echo";

    public static void tick(ServerLevel l, History h) {
        tick(l, h, true);
    }

    /**
     * Puts each figure at its post if a dreamer is near. Waits for the area's saved entities to
     * load first (so a figure already there isn't doubled), unless told not to.
     */
    public static void tick(ServerLevel l, History h, boolean waitForEntities) {
        for (Map.Entry<BlockPos, Integer> e : AltusWorld.echoes().entrySet()) {
            History.God g = h.god(e.getValue());
            ensure(l, e.getKey(), (g.alive ? "Echo of " : "Last echo of ") + g.name, true, 180f, waitForEntities);
        }
        if (AltusWorld.archivist() != null) ensure(l, AltusWorld.archivist(), "The Archivist", false, 0f, waitForEntities);
    }

    /** Makes sure a figure stands at its post if a dreamer is near. Returns it if it was just made. */
    static Mob ensure(ServerLevel l, BlockPos at, String name, boolean echo, float yaw, boolean waitForEntities) {
        if (!l.isLoaded(at) || !Guardians.dreamerNear(l, at, 32)) return null;
        if (waitForEntities && !l.areEntitiesLoaded(ChunkPos.asLong(at))) return null;
        if (!l.getEntitiesOfClass(Mob.class, new AABB(at).inflate(3), Echoes::isEcho).isEmpty()) return null;
        Mob m = echo ? EntityType.ALLAY.create(l) : EntityType.VILLAGER.create(l);
        if (m == null) return null;
        if (m instanceof Villager v) v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(5));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        m.setYHeadRot(yaw);
        m.setNoAi(true);
        m.setInvulnerable(true);
        m.setSilent(true);
        m.setPersistenceRequired();
        m.addTag(TAG);
        m.setCustomName(Component.literal(name).withStyle(echo ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        m.setCustomNameVisible(true);
        l.addFreshEntity(m);
        return m;
    }

    public static boolean isEcho(Entity e) {
        return e.getTags().contains(TAG);
    }

    /** A dreamer speaks to a figure: an echo tells its tale (or is confronted, if they crouch); the Archivist gives a hint. */
    public static void interact(ServerPlayer sp, Entity figure) {
        BlockPos a = AltusWorld.archivist();
        if (a != null && figure.blockPosition().closerThan(a, 4)) {
            hint(sp);
            return;
        }
        for (Map.Entry<BlockPos, Integer> e : AltusWorld.echoes().entrySet())
            if (figure.blockPosition().closerThan(e.getKey(), 4)) {
                if (sp.isShiftKeyDown()) confront(sp, e.getValue());
                else speak(sp, e.getValue());
                return;
            }
    }

    /** The echo tells the next thing it has to tell that the dreamer doesn't already carry or know. */
    public static void speak(ServerPlayer sp, int god) {
        History h = WorldHistory.get(sp.server);
        Sites sites = WorldHistory.sites(sp.server);
        for (String id : AltusWorld.echoWords(god)) {
            Lore.Testimony t = Lore.render(h, sites, id);
            if (t == null) continue;
            if (Memories.gain(sp, t) == Memories.Gain.ADDED) {
                say(sp, greeting(sp, h, god) + "The echo of " + h.name(god) + " speaks of " + t.title
                        + ". Its meaning slips past you, but you will remember it when you wake.");
                return;
            }
        }
        say(sp, greeting(sp, h, god) + "The echo of " + h.name(god) + " has nothing more to tell you.");
    }

    private static String greeting(ServerPlayer sp, History h, int god) {
        return Favor.follows(sp, god) ? "It knows you, " + sp.getGameProfile().getName() + ". " : "";
    }

    /**
     * The dreamer holds up what they have written against the echo. If their writings catch the god
     * in a lie it hasn't yet confessed to them, it confesses.
     */
    public static void confront(ServerPlayer sp, int god) {
        History h = WorldHistory.get(sp.server);
        Sites sites = WorldHistory.sites(sp.server);
        Knowledge k = Memories.knowledge(sp);
        boolean any = false;
        for (String ev : Lore.contradictions(h, k.written, god)) {
            any = true;
            Lore.Testimony t = Lore.render(h, sites, "CF:" + ev + ":" + god);
            if (t == null) continue;
            if (Memories.gain(sp, t) == Memories.Gain.ADDED) {
                sp.displayClientMessage(Component.literal("You hold the truth up against the echo of " + h.name(god)
                        + ". It falters, and confesses.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
                // The liar resents being caught; whoever its lie wronged is grateful.
                Favor.add(sp, god, -20);
                History.Lie lie = h.storyOf(ev, god);
                if (lie != null && lie.targetGod != null && lie.targetGod != god) Favor.add(sp, lie.targetGod, 10);
                return;
            }
        }
        say(sp, any ? "The echo of " + h.name(god) + " has already confessed all you can prove."
                : "You have nothing to hold against " + h.name(god) + ".");
    }

    /** The Archivist's hints: what the dreamer could do next, given what they have written. */
    public static List<String> hints(ServerPlayer sp) {
        History h = WorldHistory.get(sp.server);
        Sites sites = WorldHistory.sites(sp.server);
        Knowledge k = Memories.knowledge(sp);
        List<String> out = new ArrayList<>();
        if (k.written.isEmpty()) {
            out.add("Read the stones in the Clearing. When you wake, write what you remember in a Tome, before it fades.");
            out.add("Sleep among the things a god loves, and you will dream toward it.");
            return out;
        }
        for (Map.Entry<Integer, Integer> e : k.understanding.entrySet()) {
            int g = e.getKey(), u = e.getValue();
            if (g < 0 || g >= h.gods.size()) continue;
            String name = h.name(g);
            Sites.Site s = sites.of(g);
            if (s == null) continue;
            if (!h.god(g).alive)
                out.add(cap(name) + " is dead. What is left of its sanctum stands at " + s.label + ", and its corpse remembers who killed it.");
            else if (!k.written.contains("LOC:" + g))
                out.add("The door of " + name + " stands on the Mountain, set in a wall of "
                        + Palettes.of(h.god(g)).wall().getBlock().getName().getString().toLowerCase() + ". Learn where, and you will wake beside it.");
            if (u < 2) out.add(cap(name) + "'s reliquaries open for those who have written two things about it.");
            else if (u < AltusWorld.INNER_GATE)
                out.add(cap(name) + " keeps its secrets behind an inner door, which opens once you have written "
                        + AltusWorld.INNER_GATE + " things about it.");
            List<String> lies = Lore.contradictions(h, k.written, g);
            for (String ev : lies)
                if (!k.written.contains("CF:" + ev + ":" + g)) {
                    out.add("Two of your writings disagree, and " + name + " is the liar. Find its echo and crouch as you speak to it.");
                    break;
                }
        }
        if (out.isEmpty()) out.add("Sleep among the things a god loves, and you will dream toward it.");
        return out;
    }

    private static void hint(ServerPlayer sp) {
        List<String> hs = hints(sp);
        String pick = hs.get(sp.getRandom().nextInt(hs.size()));
        sp.displayClientMessage(Component.literal("The Archivist: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(pick).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)), false);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void say(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
