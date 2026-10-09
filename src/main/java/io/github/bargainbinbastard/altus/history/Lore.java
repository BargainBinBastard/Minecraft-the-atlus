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
 * <p>Ids: {@code W:<facet>:<god>} Woods inscriptions (level 1); {@code OP:<god>:<other>} opinions,
 * {@code GR:<grudge>} grudges and {@code GP:<group>:<god>} groups (level 2, sanctum murals);
 * {@code EV:<event>:<teller>:<reveals>} a god's account of an event and {@code LOC:<god>} where a
 * god's door stands (level 3, sanctum reliquaries); {@code SC:<secret>:<keeper>} kept secrets and
 * {@code TR:<event>:<god>} the truth behind a god's lie (level 4 or 5, inner sanctums); and
 * {@code CF:<event>:<god>} a liar's confession (level 4).
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

    /** Renders a testimony without door locations. */
    public static Testimony render(History h, String id) {
        return render(h, null, id);
    }

    /** Renders a testimony, or returns null if the id doesn't make sense for this world. */
    public static Testimony render(History h, Sites sites, String id) {
        String[] p = id.split(":");
        try {
            switch (p[0]) {
                case "W": return p.length == 3 ? woods(h, id, p[1], Integer.parseInt(p[2])) : null;
                case "OP": return p.length == 3 ? opinion(h, id, Integer.parseInt(p[1]), Integer.parseInt(p[2])) : null;
                case "GR": return p.length == 2 ? grudge(h, id, p[1]) : null;
                case "GP": return p.length == 3 ? group(h, id, Integer.parseInt(p[1]), Integer.parseInt(p[2])) : null;
                case "EV": return p.length == 4 ? event(h, id, p[1], Integer.parseInt(p[2]), p[3]) : null;
                case "LOC": return p.length == 2 && sites != null ? location(h, sites, id, Integer.parseInt(p[1])) : null;
                case "SC": return p.length == 3 ? secret(h, id, p[1], Integer.parseInt(p[2])) : null;
                case "TR": return p.length == 3 ? truth(h, id, p[1], Integer.parseInt(p[2]), false) : null;
                case "CF": return p.length == 3 ? truth(h, id, p[1], Integer.parseInt(p[2]), true) : null;
                default: return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean validGod(History h, int god) {
        return god >= 0 && god < h.gods.size();
    }

    private static Testimony woods(History h, String id, String facet, int god) {
        if (!validGod(h, god)) return null;
        God g = h.god(god);
        String carved = "Carved in stone, in the Woods of the Altus.";
        switch (facet) {
            case "LIKES":
                return new Testimony(id, 1, god, "The Loves of " + g.name, Text.cap(g.name) + " delights in " + phrases(g.likes) + ".", carved);
            case "DISLIKES":
                return new Testimony(id, 1, god, "The Hatreds of " + g.name, Text.cap(g.name) + " cannot abide " + phrases(g.dislikes) + ".", carved);
            case "NATURE": {
                List<String> ts = new ArrayList<>();
                for (String t : g.traits.keySet()) ts.add(Content.TRAIT_ADJ.getOrDefault(t, t));
                return new Testimony(id, 1, god, "The Nature of " + g.name, Text.cap(g.name) + " is said to be " + Text.listPhrase(ts) + ".", carved);
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

    private static String painted(History h, int god) {
        return "Painted on the walls of " + Text.poss(h.name(god)) + " sanctum.";
    }

    private static Testimony opinion(History h, String id, int a, int b) {
        if (!validGod(h, a) || !validGod(h, b) || a == b) return null;
        long o = Math.round(h.getOp(a, b));
        String A = Text.cap(h.name(a)), B = h.name(b);
        String text;
        if (o >= 5) text = A + " holds " + B + " dear.";
        else if (o >= 3) text = A + " thinks well of " + B + ".";
        else if (o <= -5) text = A + " despises " + B + ".";
        else if (o <= -3) text = A + " distrusts " + B + ".";
        else text = A + " feels little either way about " + B + ".";
        return new Testimony(id, 2, a, "What " + h.name(a) + " Thinks of " + B, text, painted(h, a));
    }

    private static Testimony grudge(History h, String id, String grudgeId) {
        History.Grudge gd = null;
        for (History.Grudge x : h.grudges) if (x.id.equals(grudgeId)) gd = x;
        if (gd == null) return null;
        String holder = h.name(gd.holder);
        String target = gd.tt.equals("god") ? h.name(gd.target) : h.groups.get(gd.target).name;
        String why = "";
        if (gd.items != null && !gd.items.isEmpty())
            why = ", for " + target + " likes " + phrases(gd.items) + ", which " + holder + " despises";
        else if (gd.cause != null && h.event(gd.cause) != null)
            why = ", born of the events of the " + Text.ord(h.event(gd.cause).year) + " year";
        String adj = gd.sev >= 8 ? "bitter" : gd.sev >= 5 ? "deep" : "lingering";
        return new Testimony(id, 2, gd.holder, "The Grudge of " + holder,
                Text.cap(holder) + " holds a " + adj + " grudge against " + target + why + ".", painted(h, gd.holder));
    }

    private static Testimony group(History h, String id, int groupId, int god) {
        if (groupId < 0 || groupId >= h.groups.size() || !validGod(h, god)) return null;
        History.Group q = h.groups.get(groupId);
        String ldr = q.leader != null && q.members.contains(q.leader) ? Text.cap(h.name(q.leader)) + " leads it." : "It has no leader.";
        String text = Text.cap(q.name) + " is sworn " + Text.principlePhrase(q.principle, h) + ". " + ldr + " Its members are "
                + h.names(q.members) + ".";
        if (q.dissolved) text = Text.cap(q.name) + " was sworn " + Text.principlePhrase(q.principle, h) + ", and is no more.";
        return new Testimony(id, 2, god, "On " + q.name, text, painted(h, god));
    }

    private static Testimony event(History h, String id, String eventId, int teller, String reveals) {
        HistoryEvent e = h.event(eventId);
        if (e == null || !validGod(h, teller)) return null;
        History.Lie lie = h.storyOf(eventId, teller);
        boolean ownDeath = e.type.equals("deicide") && e.targets.contains(teller);
        String att = ownDeath ? "So the corpse of " + h.name(teller) + " remembers it."
                : h.god(teller).alive ? "So " + h.name(teller) + " tells it." : "So " + h.name(teller) + ", who is no more, once told it.";
        boolean secret = e.secretRef != null && h.secret(e.secretRef) != null && !h.secret(e.secretRef).exposed;
        return new Testimony(id, secret ? 4 : 3, teller, Text.eventTitle(e, h), Text.renderEvent(e, reveals, lie, h), Text.cap(att));
    }

    /** A secret its keeper guards in its innermost chamber. The weightiest secrets are level 5. */
    private static Testimony secret(History h, String id, String secretId, int keeper) {
        History.Secret sc = h.secret(secretId);
        if (sc == null || !validGod(h, keeper)) return null;
        return new Testimony(id, sc.importance >= 8 ? 5 : 4, keeper, Fragments.SECRET_TITLE.getOrDefault(sc.kind, "A Hidden Truth"),
                sc.text, "Kept in the innermost chamber of " + Text.poss(h.name(keeper)) + " sanctum.");
    }

    /**
     * What really happened in an event a god lies about: kept in its inner sanctum ({@code TR}), or
     * wrung from its echo when a dreamer confronts it with proof ({@code CF}).
     */
    private static Testimony truth(History h, String id, String eventId, int god, boolean confession) {
        HistoryEvent e = h.event(eventId);
        if (e == null || !validGod(h, god) || !Sanctum.lieShows(h, e, god)) return null;
        String truth = Text.renderEvent(e, Sanctum.reveals(h, e, god), null, h);
        if (confession)
            return new Testimony(id, 4, god, "The Confession of " + h.name(god),
                    "Confronted with the truth, " + h.name(god) + " admitted it: " + truth, "Wrung from the echo of " + h.name(god) + ".");
        return new Testimony(id, 4, god, Text.eventTitle(e, h) + ", As It Was", truth,
                "What " + h.name(god) + " knows, and does not tell.");
    }

    /**
     * Whether a dreamer's writings catch a god in a lie: they have written the god's own (false)
     * account of an event and something that contradicts it. Returns the event's id, or null.
     */
    public static String contradiction(History h, java.util.Set<String> written, int god) {
        List<String> all = contradictions(h, written, god);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every event about which a dreamer's writings catch the god lying, in a stable order. */
    public static List<String> contradictions(History h, java.util.Set<String> written, int god) {
        java.util.TreeSet<String> found = new java.util.TreeSet<>();
        for (String w : written) {
            String[] p = w.split(":");
            if (p.length != 4 || !p[0].equals("EV") || !p[2].equals(Integer.toString(god))) continue;
            String ev = p[1];
            HistoryEvent e = h.event(ev);
            if (e == null || !Sanctum.lieShows(h, e, god) || !p[3].equals(Sanctum.reveals(h, e, god))) continue;
            if (written.contains("TR:" + ev + ":" + god) || written.contains("CF:" + ev + ":" + god)) {
                found.add(ev);
                continue;
            }
            for (String o : written) {
                String[] q = o.split(":");
                if (q.length == 4 && q[0].equals("EV") && q[1].equals(ev) && !q[2].equals(p[2])) {
                    try {
                        if (h.storyOf(ev, Integer.parseInt(q[2])) == null) found.add(ev);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static Testimony location(History h, Sites sites, String id, int god) {
        if (!validGod(h, god)) return null;
        Sites.Site s = sites.of(god);
        if (s == null) return null;
        return new Testimony(id, 3, god, "The Door of " + h.name(god),
                "The door to " + Text.poss(h.name(god)) + " sanctum lies at " + s.label + ".", "Kept among the relics of the Mountain.");
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
