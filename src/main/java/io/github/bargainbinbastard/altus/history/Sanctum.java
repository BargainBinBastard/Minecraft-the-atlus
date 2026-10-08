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

    private Sanctum(int god, List<String> murals, List<String> reliquaries) {
        this.god = god;
        this.murals = murals;
        this.reliquaries = reliquaries;
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
        List<String> relics = new ArrayList<>();
        for (HistoryEvent e : deeds) {
            if (relics.size() >= 3) break;
            relics.add("EV:" + e.id + ":" + god + ":" + reveals(h, e, god));
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
        return new Sanctum(god, List.copyOf(murals), List.copyOf(relics));
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
        return new Sanctum(god, List.copyOf(murals), List.copyOf(relics));
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

    /** Which part of a deed the teller's account emphasizes. Follows the teller's lie if it has one. */
    static String reveals(History h, HistoryEvent e, int teller) {
        Lie lie = h.storyOf(e.id, teller);
        List<String> base = Fragments.REVEALS.getOrDefault(e.type, List.of("WHO"));
        if (lie != null) {
            switch (lie.distortion) {
                case "CHANGE_CAUSE": if (base.contains("WHY")) return "WHY"; break;
                case "SHIFT_DATE": return "WHEN";
                case "FABRICATE_LOCATION": return "LOCATION";
                default: break;
            }
        }
        return base.get(0);
    }
}
