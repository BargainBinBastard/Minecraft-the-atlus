package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.Group;
import io.github.bargainbinbastard.altus.history.History.Grudge;
import io.github.bargainbinbastard.altus.history.History.HistoryEvent;
import io.github.bargainbinbastard.altus.history.History.Lie;

/**
 * What a god's sanctum holds. The gallery's murals are level 2: what the god thinks of others,
 * its grudges, its groups. The archive's reliquaries are level 3: the god's own account of its
 * deeds (lies included), and where another god's door can be found.
 */
public final class Sanctum {
    public final int god;
    public final List<String> murals;
    public final List<String> reliquaries;
    /** What the god's echo will say, in order. */
    public final List<String> echo;
    /** Behind the inner door: the god's kept secrets and the truth behind its lies. */
    public final List<String> inner;

    private Sanctum(int god, List<String> murals, List<String> reliquaries, List<String> echo, List<String> inner) {
        this.god = god;
        this.murals = murals;
        this.reliquaries = reliquaries;
        this.echo = echo;
        this.inner = inner;
    }

    /** The echo's words: its account of its weightiest deed, motive and all, then its view of its bitterest enemy. */
    static List<String> echoOf(History h, int god, List<HistoryEvent> deeds, List<String> taken) {
        List<String> out = new ArrayList<>();
        for (HistoryEvent e : deeds) {
            List<String> rv = Fragments.REVEALS.getOrDefault(e.type, List.of("WHO"));
            String reveal = rv.contains("WHY") ? "WHY" : rv.get(rv.size() - 1);
            String id = "EV:" + e.id + ":" + god + ":" + reveal;
            if (!taken.contains(id)) {
                out.add(id);
                break;
            }
        }
        God worst = null;
        double low = -2.5;
        for (God o : h.gods) {
            if (o.id == god || Lore.hidden(h, o.id)) continue;
            double op = h.getOp(god, o.id);
            if (op < low) {
                low = op;
                worst = o;
            }
        }
        if (worst != null && !taken.contains("OP:" + god + ":" + worst.id)) out.add("OP:" + god + ":" + worst.id);
        return List.copyOf(out);
    }

    /** The god's kept secrets, then the truth behind each lie it tells, weightiest first. */
    static List<String> innerOf(History h, int god) {
        List<String> out = new ArrayList<>();
        List<History.Secret> kept = new ArrayList<>();
        for (History.Secret sc : h.secrets) if (!sc.exposed && sc.keepers.contains(god)) kept.add(sc);
        kept.sort((a, b) -> Integer.compare(b.importance, a.importance));
        for (History.Secret sc : kept) if (out.size() < 3) out.add("SC:" + sc.id + ":" + god);
        for (Lie l : h.lies) {
            if (out.size() >= 5) break;
            if ((l.teller == god || l.tellers.contains(god)) && h.event(l.eventId) != null) {
                String id = "TR:" + l.eventId + ":" + god;
                if (!out.contains(id) && lieShows(h, h.event(l.eventId), god)) out.add(id);
            }
        }
        return List.copyOf(out);
    }

    private static final Set<String> TELLABLE = Set.of("offense", "strike", "kill_attempt", "deicide", "war_start",
            "war_end", "creation", "corpse_birth", "group_found", "betrayal", "splinter", "defect", "exposure",
            "correction", "forgive", "accusation", "seclusion");

    public static Sanctum plan(History h, Sites sites, int god) {
        God g = h.god(god);
        List<String> murals = new ArrayList<>();

        // What it thinks of the god it feels most strongly about.
        God best = null;
        double bestAbs = 2.5;
        for (God o : h.gods) {
            if (o == g || !o.alive || Lore.hidden(h, o.id)) continue;
            double op = h.getOp(god, o.id);
            if (Math.abs(Math.round(op)) >= 3 && Math.abs(op) > bestAbs) {
                bestAbs = Math.abs(op);
                best = o;
            }
        }
        if (best != null) murals.add("OP:" + god + ":" + best.id);

        // Its worst living grudge.
        Grudge worst = null;
        for (Grudge gd : h.grudges)
            if (gd.resolved == null && gd.holder == god && gd.tt.equals("god") && h.god(gd.target).alive && !Lore.hidden(h, gd.target)
                    && (worst == null || gd.sev > worst.sev)) worst = gd;
        if (worst != null) murals.add("GR:" + worst.id);

        // A public group it belongs to.
        for (Group q : h.groups) {
            if (q.dissolved || q.secret || !q.members.contains(god)) continue;
            boolean visible = true;
            for (int m : q.members) if (Lore.hidden(h, m)) visible = false;
            if (visible) {
                murals.add("GP:" + q.id + ":" + god);
                break;
            }
        }
        // Never leave the gallery bare.
        for (int f = 0; murals.size() < 3 && f < Lore.WOODS_FACETS.length; f++) murals.add(Lore.woodsId(f, god));

        // The god's account of the deeds it took part in, weightiest first.
        List<HistoryEvent> deeds = new ArrayList<>();
        for (HistoryEvent e : h.events) {
            if (!TELLABLE.contains(e.type) || !(e.actors.contains(god) || e.targets.contains(god))) continue;
            if (Fragments.hidden(h, e, hiddenGods(h))) continue;
            deeds.add(e);
        }
        deeds.sort((a, b) -> {
            int c = Double.compare(Fragments.FW.getOrDefault(b.type, 1.0), Fragments.FW.getOrDefault(a.type, 1.0));
            return c != 0 ? c : Integer.compare(b.year, a.year);
        });
        // In its own sanctum a god tells its official version: the deeds it lies about come first.
        List<String> relics = new ArrayList<>();
        for (HistoryEvent e : deeds) {
            if (relics.size() >= 2) break;
            if (lieShows(h, e, god)) relics.add("EV:" + e.id + ":" + god + ":" + reveals(h, e, god));
        }
        for (HistoryEvent e : deeds) {
            if (relics.size() >= 3) break;
            String id = "EV:" + e.id + ":" + god + ":" + reveals(h, e, god);
            if (!relics.contains(id)) relics.add(id);
        }

        // Where a friend's door stands, so every sanctum points somewhere new.
        God friend = null;
        double fop = Double.NEGATIVE_INFINITY;
        for (God o : h.gods) {
            if (o == g || !o.alive || Lore.hidden(h, o.id) || sites.of(o.id) == null) continue;
            double op = h.getOp(god, o.id) + (kin(g, o) ? 5 : 0);
            if (op > fop) {
                fop = op;
                friend = o;
            }
        }
        if (friend != null) relics.add("LOC:" + friend.id);
        return new Sanctum(god, List.copyOf(murals), List.copyOf(relics), echoOf(h, god, deeds, relics), innerOf(h, god));
    }

    /**
     * What remains of a dead god's sanctum. Its first reliquary is the corpse's own memory of its
     * death, true even when the killing was kept secret, so it names the killers.
     */
    public static Sanctum planRuin(History h, Sites sites, int god) {
        List<String> murals = new ArrayList<>(List.of(Lore.woodsId(3, god), Lore.woodsId(2, god), Lore.woodsId(0, god)));
        List<String> relics = new ArrayList<>();
        for (HistoryEvent e : h.events)
            if (e.type.equals("deicide") && e.targets.contains(god)) {
                relics.add("EV:" + e.id + ":" + god + ":WHO");
                break;
            }
        Sanctum living = plan(h, sites, god);
        for (String r : living.reliquaries) {
            if (relics.size() >= 4) break;
            if (!relics.contains(r)) relics.add(r);
        }
        return new Sanctum(god, List.copyOf(murals), List.copyOf(relics), living.echo, living.inner);
    }

    private static boolean kin(God a, God b) {
        return (a.creator != null && a.creator == b.id) || (b.creator != null && b.creator == a.id)
                || (a.sourceCorpse != null && a.sourceCorpse == b.id) || (b.sourceCorpse != null && b.sourceCorpse == a.id);
    }

    static Set<Integer> hiddenGods(History h) {
        Set<Integer> out = new java.util.HashSet<>();
        for (History.Secret s : h.secrets) if (s.kind.equals("EXISTENCE") && !s.exposed) out.add(s.about);
        return out;
    }

    private static final List<String> ALL_REVEALS = List.of("WHO", "WHY", "WHEN", "OUTCOME", "LOCATION");

    /**
     * Which part of a deed the teller's account emphasizes. If the teller lies about it, this is
     * the part the lie distorts, so its account and the truth visibly disagree.
     */
    public static String reveals(History h, HistoryEvent e, int teller) {
        List<String> base = Fragments.REVEALS.getOrDefault(e.type, List.of("WHO"));
        Lie lie = h.storyOf(e.id, teller);
        if (lie != null) {
            List<String> cands = new ArrayList<>(base);
            for (String r : ALL_REVEALS) if (!cands.contains(r)) cands.add(r);
            for (String r : cands)
                if (!Text.renderEvent(e, r, lie, h).equals(Text.renderEvent(e, r, null, h))) return r;
        }
        return base.get(0);
    }

    /** Whether the teller's lie about an event shows in what it says. */
    public static boolean lieShows(History h, HistoryEvent e, int teller) {
        Lie lie = h.storyOf(e.id, teller);
        if (lie == null) return false;
        String r = reveals(h, e, teller);
        return !Text.renderEvent(e, r, lie, h).equals(Text.renderEvent(e, r, null, h));
    }
}
