package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.List;

import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.HistoryEvent;
import io.github.bargainbinbastard.altus.history.History.Secret;

/**
 * Lore a player can carry and write down. Each piece is a "testimony" with a stable id, so it can
 * be stored on a player or in a Tome and rendered again later from the world's history.
 *
 * <p>Ids so far: {@code W:<facet>:<godId>}, the Woods inscriptions, with facet LIKES, DISLIKES,
 * NATURE or ORIGIN.
 */
public final class Lore {
    private Lore() {}

    public static final String[] WOODS_FACETS = {"LIKES", "DISLIKES", "NATURE", "ORIGIN"};

    public static final class Testimony {
        public final String id;
        public final int level;
        /** The god this is mainly about. */
        public final int subject;
        public final String title;
        public final String text;
        public final String attribution;

        Testimony(String id, int level, int subject, String title, String text, String attribution) {
            this.id = id;
            this.level = level;
            this.subject = subject;
            this.title = title;
            this.text = text;
            this.attribution = attribution;
        }
    }

    public static String woodsId(int facet, int god) {
        return "W:" + WOODS_FACETS[facet] + ":" + god;
    }

    /** Whether a god's very existence is still a kept secret. */
    public static boolean hidden(History h, int god) {
        for (Secret s : h.secrets) if (s.kind.equals("EXISTENCE") && s.about == god && !s.exposed) return true;
        return false;
    }

    /**
     * The god the Woods speak of in a dream with no focus: the maker of the world if it still
     * stands and is known, otherwise the first living god that is not hidden. -1 if there is none.
     */
    public static int woodsFallbackGod(History h) {
        God maker = h.gods.isEmpty() ? null : h.gods.get(0);
        if (maker != null && maker.alive && !hidden(h, maker.id)) return maker.id;
        for (God g : h.gods) if (g.alive && !hidden(h, g.id)) return g.id;
        return -1;
    }

    /** Renders a testimony, or returns null if the id doesn't make sense for this world. */
    public static Testimony render(History h, String id) {
        String[] p = id.split(":");
        if (p.length != 3 || !p[0].equals("W")) return null;
        int god;
        try {
            god = Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (god < 0 || god >= h.gods.size()) return null;
        God g = h.god(god);
        String carved = "Carved in stone, in the Woods of the Altus.";
        switch (p[1]) {
            case "LIKES":
                return new Testimony(id, 1, god, "The Loves of " + g.name,
                        Text.cap(g.name) + " delights in " + phrases(g.likes) + ".", carved);
            case "DISLIKES":
                return new Testimony(id, 1, god, "The Hatreds of " + g.name,
                        Text.cap(g.name) + " cannot abide " + phrases(g.dislikes) + ".", carved);
            case "NATURE": {
                List<String> ts = new ArrayList<>();
                for (String t : g.traits.keySet()) ts.add(Content.TRAIT_ADJ.getOrDefault(t, t));
                return new Testimony(id, 1, god, "The Nature of " + g.name,
                        Text.cap(g.name) + " is said to be " + Text.listPhrase(ts) + ".", carved);
            }
            case "ORIGIN": {
                HistoryEvent birth = birthOf(h, god);
                if (birth == null) return null;
                return new Testimony(id, 1, god, Text.eventTitle(birth, h), Text.renderEvent(birth, "WHO", null, h), carved);
            }
            default:
                return null;
        }
    }

    private static String phrases(List<Item> items) {
        List<String> out = new ArrayList<>();
        for (Item i : items) out.add(Text.itemPhrase(i));
        return Text.listPhrase(out);
    }

    /** The event in which a god came into being. */
    public static HistoryEvent birthOf(History h, int god) {
        for (HistoryEvent e : h.events) {
            switch (e.type) {
                case "glory_birth":
                case "nowhere_birth":
                case "corpse_birth":
                    if (e.actors.get(0) == god) return e;
                    break;
                case "creation":
                    if (e.targets.get(0) == god) return e;
                    break;
                default:
                    break;
            }
        }
        return null;
    }
}
