package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.HistoryEvent;

/**
 * The rules of devotion that don't depend on Minecraft: what each written entry is worth as a
 * rite, whether it is false (a false rite misfires), and what gift each god grants its disciples.
 */
public final class Rites {
    private Rites() {}

    public static final int FOLLOWER = 25, DISCIPLE = 150, EXALTED = 500;

    public enum Effect {
        QUICKEN_FIELDS(1, "Quicken the Fields", "every crop nearby ripens at once"),
        MEND_TOOLS(1, "Mend the Worn", "your tools and armor are partly mended"),
        FAVOR(1, "Gain Favor", "the first god it names is pleased with you"),
        LONG_DREAM(2, "The Long Dream", "your next dream lasts half again as long"),
        WARD_OF_NIGHT(2, "Ward of the Night", "no monsters spawn near the altar for a day"),
        CALL_OF_THE_GOD(2, "Call of the God", "your next dream leans toward the first god it names"),
        BORROWED_GIFT(2, "Borrowed Gift", "you carry the first named god's gift for a day"),
        BATTLE_FURY(3, "Battle Fury", "strength and resistance for ten minutes"),
        UNSEAL(3, "Unseal", "on your next dream, the first named god's doors open as if you knew it well"),
        SEERS_WHISPER(3, "Seer's Whisper", "a whisper of where some hidden lore lies");

        public final int tier;
        public final String title, description;

        Effect(int tier, String title, String description) {
            this.tier = tier;
            this.title = title;
            this.description = description;
        }
    }

    /** The gods an entry names, in order, at most three. They are who the offerings are for. */
    public static List<Integer> namedGods(History h, String id) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        String[] p = id.split(":");
        try {
            switch (p[0]) {
                case "W", "LOC" -> out.add(Integer.parseInt(p[p.length - 1]));
                case "OP" -> {
                    out.add(Integer.parseInt(p[1]));
                    out.add(Integer.parseInt(p[2]));
                }
                case "GR" -> {
                    for (History.Grudge g : h.grudges)
                        if (g.id.equals(p[1])) {
                            out.add(g.holder);
                            if (g.tt.equals("god")) out.add(g.target);
                        }
                }
                case "GP" -> {
                    out.add(Integer.parseInt(p[2]));
                    int gid = Integer.parseInt(p[1]);
                    if (gid >= 0 && gid < h.groups.size()) out.addAll(h.groups.get(gid).members);
                }
                case "SC" -> {
                    out.add(Integer.parseInt(p[2]));
                    History.Secret s = h.secret(p[1]);
                    if (s != null && s.kind.equals("EXISTENCE")) out.add(s.about);
                }
                case "EV", "TR", "CF" -> {
                    HistoryEvent e = h.event(p[1]);
                    if (e != null) {
                        out.addAll(e.actors);
                        out.addAll(e.targets);
                    }
                }
                default -> { }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            // malformed ids name nobody
        }
        out.removeIf(g -> g < 0 || g >= h.gods.size());
        List<Integer> l = new ArrayList<>(out);
        return l.size() > 3 ? l.subList(0, 3) : l;
    }

    public static int tier(Lore.Testimony t, int named) {
        if (t.level <= 1) return 1;
        if (t.level == 2) return named >= 2 ? 2 : 1;
        if (t.level == 3) return 2;
        return 3;
    }

    /** The rite an entry performs: fixed per entry, drawn from the effects of its tier. */
    public static Effect effectOf(History h, Sites sites, String id) {
        Lore.Testimony t = Lore.render(h, sites, id);
        if (t == null) return null;
        int tier = tier(t, namedGods(h, id).size());
        List<Effect> pool = new ArrayList<>();
        for (Effect e : Effect.values()) if (e.tier == tier) pool.add(e);
        return pool.get(Math.floorMod(id.hashCode(), pool.size()));
    }

    /** Whether what an entry says is false. A rite built on a falsehood misfires. */
    public static boolean isFalse(History h, String id) {
        String[] p = id.split(":");
        if (!p[0].equals("EV") || p.length != 4) return false;
        HistoryEvent e = h.event(p[1]);
        if (e == null) return false;
        int teller;
        try {
            teller = Integer.parseInt(p[2]);
        } catch (NumberFormatException ex) {
            return false;
        }
        History.Lie lie = h.storyOf(e.id, teller);
        return lie != null && !Text.renderEvent(e, p[3], lie, h).equals(Text.renderEvent(e, p[3], null, h));
    }

    /** What a god will accept as an offering in a rite: the first thing it likes that can be held. */
    public static String offeringLike(God g) {
        for (Item l : g.likes) if (l.cat.equals("block")) return l.id;
        return null;
    }

    private static final Map<String, String> GIFTS = Map.ofEntries(
            Map.entry("deepslate", "haste"), Map.entry("obsidian", "haste"), Map.entry("iron_block", "haste"),
            Map.entry("copper", "haste"), Map.entry("amethyst", "haste"), Map.entry("bone_block", "haste"), Map.entry("glass", "haste"),
            Map.entry("prismarine", "water_breathing"), Map.entry("kelp", "water_breathing"), Map.entry("coral", "water_breathing"),
            Map.entry("wheat", "health_boost"), Map.entry("moss", "health_boost"), Map.entry("honeycomb", "health_boost"),
            Map.entry("mud", "health_boost"), Map.entry("snow", "speed"),
            Map.entry("sculk", "night_vision"), Map.entry("cobweb", "night_vision"), Map.entry("candle", "night_vision"),
            Map.entry("wolf", "speed"), Map.entry("fox", "speed"), Map.entry("bee", "jump_boost"),
            Map.entry("drowned", "water_breathing"), Map.entry("axolotl", "water_breathing"), Map.entry("enderman", "resistance"),
            Map.entry("skeleton", "strength"), Map.entry("spider", "strength"), Map.entry("witch", "strength"),
            Map.entry("phantom", "slow_falling"), Map.entry("bat", "slow_falling"), Map.entry("sheep", "health_boost"),
            Map.entry("mine_ore", "haste"), Map.entry("dig_deep", "haste"), Map.entry("craft_tool", "haste"), Map.entry("fell_tree", "haste"),
            Map.entry("till_soil", "health_boost"), Map.entry("breed_animals", "health_boost"),
            Map.entry("kill_passive", "strength"), Map.entry("kill_hostile", "strength"),
            Map.entry("brew_potion", "luck"), Map.entry("enchant_item", "luck"),
            Map.entry("burn_item", "fire_resistance"), Map.entry("light_fire", "fire_resistance"),
            Map.entry("sleep_in_nether", "fire_resistance"), Map.entry("extinguish_fire", "water_breathing"),
            Map.entry("build_height", "slow_falling"));

    /** The gift a god grants its disciples, from what it loves most. */
    public static String giftOf(God g) {
        return g.likes.isEmpty() ? "luck" : GIFTS.getOrDefault(g.likes.get(0).id, "luck");
    }
}
