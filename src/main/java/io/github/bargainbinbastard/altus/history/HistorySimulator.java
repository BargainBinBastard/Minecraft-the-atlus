package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import io.github.bargainbinbastard.altus.history.History.Claimed;
import io.github.bargainbinbastard.altus.history.History.EventData;
import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.Group;
import io.github.bargainbinbastard.altus.history.History.Grudge;
import io.github.bargainbinbastard.altus.history.History.HistoryEvent;
import io.github.bargainbinbastard.altus.history.History.Lie;
import io.github.bargainbinbastard.altus.history.History.LogLine;
import io.github.bargainbinbastard.altus.history.History.Principle;
import io.github.bargainbinbastard.altus.history.History.Region;
import io.github.bargainbinbastard.altus.history.History.Seclusion;
import io.github.bargainbinbastard.altus.history.History.Secret;
import io.github.bargainbinbastard.altus.history.History.War;

/**
 * Generates a world's history. A faithful port of the HTML prototype: given the same seed it
 * produces the same history and the same log, line for line.
 */
public final class HistorySimulator {
    public static final int YEARS = 300;
    public static final double ACT_P = 0.07;
    private static final double INF = Double.POSITIVE_INFINITY;

    private final Rng R;
    private final History S;
    private final List<God> gods;
    private final List<HistoryEvent> events;
    private final List<Lie> lies;
    private final List<Group> groups;
    private final List<Grudge> grudges;
    private final List<War> wars;
    private final List<Secret> secrets;
    private int evc, liec, grc, secc;
    private final List<PendingCorpse> pendingCorpses = new ArrayList<>();
    private final Set<String> usedNames = new HashSet<>();
    private final Set<String> usedGroupNames = new HashSet<>();

    private HistorySimulator(String seed) {
        this.R = new Rng(seed, "history");
        this.S = new History(seed, YEARS);
        this.gods = S.gods;
        this.events = S.events;
        this.lies = S.lies;
        this.groups = S.groups;
        this.grudges = S.grudges;
        this.wars = S.wars;
        this.secrets = S.secrets;
    }

    public static History simulate(String seed) {
        return new HistorySimulator(seed).run();
    }

    // ------------------------------------------------------------------ small types

    static final class Motive {
        final String type;
        final double score;
        String trait;
        Grudge grudge;
        God target;
        Group group;
        Secret secret;

        Motive(String type, double score) {
            this.type = type;
            this.score = score;
        }

        static Motive of(String type, double score) {
            return new Motive(type, score);
        }
    }

    private static final class PendingCorpse {
        final int dead, year, power;
        final List<Integer> killers;
        final Secret secret;

        PendingCorpse(int dead, int year, int power, List<Integer> killers, Secret secret) {
            this.dead = dead;
            this.year = year;
            this.power = power;
            this.killers = killers;
            this.secret = secret;
        }
    }

    private static final class Presence {
        final String desc, ref;

        Presence(String desc, String ref) {
            this.desc = desc;
            this.ref = ref;
        }
    }

    private static final class Scapegoat {
        final int id;
        final String desc;

        Scapegoat(int id, String desc) {
            this.id = id;
            this.desc = desc;
        }
    }

    private static final class LieOpt {
        List<Integer> tellers;
        Integer group;
        int indent = 1;

        static LieOpt indent(int i) {
            LieOpt o = new LieOpt();
            o.indent = i;
            return o;
        }

        static LieOpt group(List<Integer> tellers, Integer group, int indent) {
            LieOpt o = new LieOpt();
            o.tellers = tellers;
            o.group = group;
            o.indent = indent;
            return o;
        }
    }

    private static final class Proposal {
        final double w;
        final String label;
        final Function<God, List<Motive>> opp;
        final ToIntFunction<God> support;
        final Consumer<List<God>> run;
        List<Integer> exclude;

        Proposal(double w, String label, Function<God, List<Motive>> opp, ToIntFunction<God> support,
                Consumer<List<God>> run) {
            this.w = w;
            this.label = label;
            this.opp = opp;
            this.support = support;
            this.run = run;
        }
    }

    private static final class Opt {
        final double w;
        final String k;

        Opt(double w, String k) {
            this.w = w;
            this.k = k;
        }
    }

    private static final class Choice {
        final double w;
        final Runnable run;

        Choice(double w, Runnable run) {
            this.w = w;
            this.run = run;
        }
    }

    // ------------------------------------------------------------------ basics

    private String nm(int id) {
        return gods.get(id).name;
    }

    private String names(List<Integer> ids) {
        return S.names(ids);
    }

    private void L(int year, String tag, String text, String cls, String cat) {
        L(year, tag, text, cls, cat, 0);
    }

    private void L(int year, String tag, String text, String cls, String cat, int indent) {
        S.log.add(new LogLine(year, tag, Text.cap(text), cls, cat, indent));
    }

    private HistoryEvent newEvent(String type, int year, List<Integer> actors, List<Integer> targets, EventData data) {
        HistoryEvent ev = new HistoryEvent("E-" + Text.pad(++evc, 4), type, year, actors, targets, data);
        events.add(ev);
        S.eventById.put(ev.id, ev);
        return ev;
    }

    private static EventData data() {
        return new EventData();
    }

    private static List<Integer> list(Integer... xs) {
        return new ArrayList<>(Arrays.asList(xs));
    }

    private static int T(God g, String t) {
        return g == null ? 0 : g.trait(t);
    }

    private List<God> alive() {
        List<God> out = new ArrayList<>();
        for (God g : gods) if (g.alive) out.add(g);
        return out;
    }

    private static boolean active(God g, int y) {
        return g.alive && g.secludedUntil < y;
    }

    private List<Group> groupsOf(int id) {
        List<Group> out = new ArrayList<>();
        for (Group gr : groups) if (!gr.dissolved && gr.members.contains(id)) out.add(gr);
        return out;
    }

    private String gname(int gid) {
        return groups.get(gid).name;
    }

    private boolean atWar(int id) {
        for (War w : wars) if (!w.over && (w.sideA.contains(id) || w.sideB.contains(id))) return true;
        return false;
    }

    private static int sumP(Collection<God> list) {
        int s = 0;
        for (God g : list) s += g.power;
        return s;
    }

    private List<God> godsOf(Collection<Integer> ids) {
        List<God> out = new ArrayList<>();
        for (int id : ids) out.add(gods.get(id));
        return out;
    }

    private static List<Integer> idsOf(Collection<God> gs) {
        List<Integer> out = new ArrayList<>();
        for (God g : gs) out.add(g.id);
        return out;
    }

    private boolean kin(int a, int b) {
        if (a == b) return false;
        God A = gods.get(a), B = gods.get(b);
        return eq(A.creator, b) || eq(B.creator, a) || eq(A.sourceCorpse, b) || eq(B.sourceCorpse, a);
    }

    private static boolean eq(Integer a, int b) {
        return a != null && a == b;
    }

    private List<God> aggressorsOf(int id, int year) {
        Set<Integer> seen = new HashSet<>();
        List<God> out = new ArrayList<>();
        for (HistoryEvent e : events) {
            if (year - e.year > 40 || e.year > year) continue;
            if (!(e.type.equals("offense") || e.type.equals("strike") || e.type.equals("kill_attempt"))
                    || !e.targets.contains(id) || e.secretRef != null) continue;
            for (int a : e.actors) if (a != id && gods.get(a).alive && !seen.contains(a)) {
                seen.add(a);
                out.add(gods.get(a));
            }
        }
        return out;
    }

    /** Numeric comparator matching JavaScript's (a, b) => a - b. */
    private static int cmp(double d) {
        return d < 0 ? -1 : d > 0 ? 1 : 0;
    }

    // ------------------------------------------------------------------ opinions

    private double getOp(int a, int b) {
        return S.getOp(a, b);
    }

    private void setOp(int a, int b, double v) {
        S.setOp(a, b, v);
    }

    private void bump(int a, int b, double d) {
        if (a != b) setOp(a, b, getOp(a, b) + d);
    }

    private int friend(int a, int b) {
        if (a == b) return 0;
        double o = getOp(a, b);
        return o >= 3 ? (int) Math.min(10, Math.round(o)) : 0;
    }

    // ------------------------------------------------------------------ grudges

    private Grudge grudgeVs(int h, int t) {
        Grudge best = null;
        for (Grudge g : grudges) {
            if (g.resolved != null || g.holder != h) continue;
            boolean hit;
            if (g.tt.equals("god")) hit = g.target == t;
            else {
                Group gr = groups.get(g.target);
                hit = !gr.dissolved && gr.members.contains(t) && !g.forgiven.contains(t);
            }
            if (hit && (best == null || g.sev > best.sev)) best = g;
        }
        return best;
    }

    private int grudgeScore(int h, int t) {
        Grudge g = grudgeVs(h, t);
        return g != null ? g.sev : 0;
    }

    private Grudge groupGrudge(int h, int gid) {
        for (Grudge g : grudges)
            if (g.resolved == null && g.holder == h && g.tt.equals("group") && g.target == gid) return g;
        return null;
    }

    private String tlabel(String tt, int t) {
        return tt.equals("god") ? nm(t) : "group " + gname(t);
    }

    private Grudge addGrudge(int h, String tt, int t, double sevIn, HistoryEvent ev, int year) {
        return addGrudge(h, tt, t, sevIn, ev, year, 1, null);
    }

    private Grudge addGrudge(int h, String tt, int t, double sevIn, HistoryEvent ev, int year, int indent) {
        return addGrudge(h, tt, t, sevIn, ev, year, indent, null);
    }

    private Grudge addGrudge(int h, String tt, int t, double sevIn, HistoryEvent ev, int year, int indent, String reason) {
        if (tt.equals("god") && h == t) return null;
        if (!gods.get(h).alive) return null;
        if (tt.equals("god") && !gods.get(t).alive) return null;
        int sev = (int) History.clamp(Math.round(sevIn), 1, 10);
        for (Grudge ex : grudges) {
            if (ex.resolved == null && ex.holder == h && ex.tt.equals(tt) && ex.target == t) {
                int old = ex.sev;
                ex.sev = (int) Math.min(10, ex.sev + Math.ceil(sev / 2.0));
                if (ex.sev != old)
                    L(year, "", ex.id + " deepens: " + nm(h) + " → " + tlabel(tt, t) + " sev=" + ex.sev, "grudge", "conflict", indent);
                return ex;
            }
        }
        Grudge g = new Grudge("G-" + Text.pad(++grc, 3), h, tt, t, sev, ev != null ? ev.id : null, year);
        grudges.add(g);
        L(year, "", "GRUDGE " + g.id + " " + nm(h) + " → " + tlabel(tt, t) + " sev=" + sev
                + (ev != null ? " cause=" + ev.id : "") + (reason != null ? "  (" + reason + ")" : ""), "grudge", "conflict", indent);
        g.reason = reason;
        return g;
    }

    // ------------------------------------------------------------------ secrets

    private Secret addSecret(String kind, int about, Integer group, String text, Collection<Integer> keepers,
            Collection<Integer> knowers, int importance, String eventRef, int year, int indent, boolean quiet) {
        Secret s = new Secret("S-" + Text.pad(++secc, 3), kind, about, Text.cap(text), new LinkedHashSet<>(keepers),
                new LinkedHashSet<>(knowers == null ? List.of() : knowers), (int) History.clamp(importance, 1, 10),
                eventRef, group, year);
        secrets.add(s);
        S.secretById.put(s.id, s);
        if (eventRef != null) {
            HistoryEvent ev = S.event(eventRef);
            if (ev != null && ev.secretRef == null) ev.secretRef = s.id;
        }
        if (!quiet)
            L(year, "SECRET", s.id + " " + s.kind + ": “" + s.text + "”  kept by " + names(new ArrayList<>(s.keepers))
                    + "  importance=" + s.importance, "lie", "secrets", indent);
        return s;
    }

    private Secret addSecret(String kind, int about, Integer group, String text, Collection<Integer> keepers,
            Collection<Integer> knowers, int importance, String eventRef, int year) {
        return addSecret(kind, about, group, text, keepers, knowers, importance, eventRef, year, 1, false);
    }

    private int importanceFor(God g, Secret s) {
        return Math.min(10, s.importance + (T(g, "secretive") != 0 ? 2 : 0));
    }

    private Secret purposeSecret(Group gr) {
        for (Secret s : secrets) if (s.kind.equals("GROUP_PURPOSE") && eq(s.group, gr.id)) return s;
        return null;
    }

    // ------------------------------------------------------------------ lies

    private Presence presence(int c, int year, String exId) {
        for (War w : wars)
            if ((w.sideA.contains(c) || w.sideB.contains(c)) && w.start <= year && (!w.over || w.endYear >= year))
                return new Presence("at war that year (" + w.eventId + ")", w.eventId);
        for (Seclusion s : gods.get(c).seclusions)
            if (s.start <= year && s.end >= year) return new Presence("in seclusion that year (" + s.ev + ")", s.ev);
        for (HistoryEvent e : events)
            if (!e.id.equals(exId) && e.year == year && (e.actors.contains(c) || e.targets.contains(c)))
                return new Presence("busy elsewhere that year (" + e.id + ")", e.id);
        if (gods.get(c).born > year) return new Presence("not yet born that year", null);
        return null;
    }

    private Scapegoat scapegoat(HistoryEvent ev, int teller, List<Integer> pref) {
        Set<Integer> excl = new HashSet<>(ev.actors);
        excl.addAll(ev.targets);
        excl.add(teller);
        List<Integer> order = new ArrayList<>();
        for (int x : pref) if (!excl.contains(x)) order.add(x);
        List<Integer> all = new ArrayList<>();
        for (God g : gods) all.add(g.id);
        for (int x : R.shuffle(all)) if (!excl.contains(x) && !pref.contains(x)) order.add(x);
        for (int c : order) {
            if (!gods.get(c).alive) continue;
            Presence p = presence(c, ev.year, ev.id);
            if (p != null) return new Scapegoat(c, p.desc);
        }
        return null;
    }

    private static String storyReveal(HistoryEvent ev, Lie lie) {
        if (ev.type.equals("location")) return "LOCATION";
        return lie.distortion.equals("CHANGE_CAUSE") ? "WHY" : "WHO";
    }

    private Lie makeLie(HistoryEvent ev, int teller, String motive, String distortion, Claimed claimed, Integer targetGod,
            String contra, int year) {
        return makeLie(ev, teller, motive, distortion, claimed, targetGod, contra, year, new LieOpt());
    }

    private Lie makeLie(HistoryEvent ev, int teller, String motive, String distortion, Claimed claimed, Integer targetGod,
            String contra, int year, LieOpt opt) {
        List<Integer> candidates = opt.tellers != null ? opt.tellers : List.of(teller);
        List<Integer> tellers = new ArrayList<>();
        for (int t : candidates)
            if (T(gods.get(t), "historian") == 0 && S.storyOf(ev.id, t) == null) tellers.add(t);
        if (tellers.isEmpty()) return null;
        Lie lie = new Lie("L-" + Text.pad(++liec, 3), ev.id, tellers, motive, distortion, claimed, targetGod, contra,
                opt.group, year);
        lies.add(lie);
        S.lieById.put(lie.id, lie);
        for (int t : tellers) S.story.put(ev.id + "|" + t, lie);
        L(year, opt.group != null ? "COORDINATED" : "LIE", lie.id + " "
                + (opt.group != null ? gname(opt.group) + " agrees on one story (" + names(tellers) + ")" : "told by " + nm(tellers.get(0)))
                + "  motive=" + motive + "  distortion=" + distortion + "  about " + ev.id, "lie", "secrets", opt.indent);
        L(year, "", "official story: “" + Text.renderEvent(ev, storyReveal(ev, lie), lie, S) + "”", "lie", "secrets", opt.indent + 1);
        L(year, "", "can be caught: " + contra, "meta", "secrets", opt.indent + 1);
        return lie;
    }

    // ------------------------------------------------------------------ motivation contests

    private static String mlabel(Motive m) {
        if (m.score == INF) return m.type + " (absolute)";
        return m.type + (m.trait != null ? ":" + m.trait : "") + " " + Text.num(m.score);
    }

    private static Motive topOf(List<Motive> list) {
        Motive best = null;
        for (Motive o : list) {
            if (o == null || !(o.score > 0)) continue;
            if (best == null || o.score > best.score) best = o;
        }
        return best;
    }

    private boolean decide(God g, Motive driver, List<Motive> opps, int year, String what) {
        Motive top = topOf(opps);
        if (top == null) return true;
        boolean win = top.score != INF && (driver.score > top.score || (driver.score == top.score && R.chance(.5)));
        if (!win)
            L(year, "RESTRAINED", g.name + " wanted to " + what + " (" + mlabel(driver) + ") but " + mlabel(top) + " won"
                    + (driver.score == top.score ? " (tie, chance)" : ""), "meta", "motive");
        else if (driver.score == top.score)
            L(year, "TIE", g.name + ": " + mlabel(driver) + " equals " + mlabel(top) + "; chance favors acting", "meta", "motive");
        return win;
    }

    private List<Motive> oppHarm(God g, God t) {
        List<Motive> o = new ArrayList<>();
        int f = friend(g.id, t.id);
        if (f != 0) o.add(Motive.of("FRIENDSHIP", f));
        for (Group gr : groupsOf(g.id)) if (gr.members.contains(t.id)) o.add(Motive.of("LOYALTY", gr.loyalty.get(g.id)));
        return o;
    }

    private static List<Motive> historianOpp(God g) {
        List<Motive> o = new ArrayList<>();
        if (T(g, "historian") != 0) o.add(Motive.of("TRAIT:historian", INF));
        return o;
    }

    // ------------------------------------------------------------------ generation helpers

    private String godName(List<Item> likes) {
        List<Item> nonAction = new ArrayList<>();
        for (Item l : likes) if (!l.cat.equals("action")) nonAction.add(l);
        List<Item> anchors = R.shuffle(nonAction);
        List<String> eps = R.shuffle(Content.EPITHETS);
        for (Item a : anchors)
            for (String e : eps) {
                String n = "the " + e + " of " + a.label;
                if (!usedNames.contains(n)) {
                    usedNames.add(n);
                    return n;
                }
            }
        Item a = anchors.get(0);
        String e = eps.get(0);
        int k = 2;
        while (usedNames.contains("the " + e + " of " + a.label + " the " + Text.ord(k))) k++;
        String n = "the " + e + " of " + a.label + " the " + Text.ord(k);
        usedNames.add(n);
        return n;
    }

    private static boolean compatible(Map<String, Integer> t, String name) {
        for (String[] p : Content.EXCL) {
            boolean incl = p[0].equals(name) || p[1].equals(name);
            if (!incl) continue;
            for (String x : p) if (!x.equals(name) && t.containsKey(x)) return false;
        }
        return true;
    }

    private LinkedHashMap<String, Integer> genTraits(int n, List<Map.Entry<String, Integer>> inherit) {
        LinkedHashMap<String, Integer> t = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : inherit) {
            if (t.size() >= n) break;
            String name = e.getKey();
            if (!name.equals("progenitor") && !t.containsKey(name) && compatible(t, name))
                t.put(name, (int) History.clamp(e.getValue() + R.nextInt(-1, 1), 2, 9));
        }
        for (String name : R.shuffle(Content.TRAIT_NAMES)) {
            if (t.size() >= n) break;
            if (Content.RARE.contains(name) && R.chance(.6)) continue;
            if (!t.containsKey(name) && compatible(t, name)) t.put(name, R.nextInt(3, 9));
        }
        return t;
    }

    private static final class LikeSet {
        final List<Item> likes, dislikes;

        LikeSet(List<Item> likes, List<Item> dislikes) {
            this.likes = likes;
            this.dislikes = dislikes;
        }
    }

    private LikeSet genLikes(God from) {
        List<Item> likes = new ArrayList<>();
        if (from != null) {
            List<Item> sh = R.shuffle(from.likes);
            int k = R.nextInt(1, 2);
            likes.addAll(sh.subList(0, Math.min(k, sh.size())));
        }
        int needBlock = R.nextInt(2, 3), needEntity = R.nextInt(1, 2), needAction = R.nextInt(2, 3);
        List<Item> pBlock = R.shuffle(Content.BLOCKS), pEntity = R.shuffle(Content.ENTITIES), pAction = R.shuffle(Content.ACTIONS);
        String[] cats = {"block", "entity", "action"};
        int[] need = {needBlock, needEntity, needAction};
        List<List<Item>> pools = List.of(pBlock, pEntity, pAction);
        for (int ci = 0; ci < 3; ci++) {
            int have = 0;
            for (Item l : likes) if (l.cat.equals(cats[ci])) have++;
            for (Item l : pools.get(ci)) {
                if (have >= need[ci]) break;
                if (!Likes.contains(likes, l)) {
                    likes.add(l);
                    have++;
                }
            }
        }
        List<Item> dislikes = new ArrayList<>();
        for (int ci = 0; ci < 3; ci++) {
            int k = R.nextInt(1, 2);
            for (Item l : pools.get(ci)) {
                if (k <= 0) break;
                if (!Likes.contains(likes, l) && !Likes.contains(dislikes, l) && !(from != null && Likes.contains(from.likes, l))) {
                    dislikes.add(l);
                    k--;
                }
            }
        }
        return new LikeSet(likes, dislikes);
    }

    private God makeGod(String origin, Integer creator, Integer sourceCorpse, int power,
            LinkedHashMap<String, Integer> traits, LikeSet lk, int year) {
        int id = gods.size();
        God g = new God(id, godName(lk.likes), origin, creator, sourceCorpse, traits, lk.likes, lk.dislikes, power, year);
        gods.add(g);
        return g;
    }

    private void conflictGrudges(God c, int year) {
        for (God o : alive()) {
            if (o == c) continue;
            God[][] pairs = {{c, o}, {o, c}};
            for (God[] pr : pairs) {
                God holder = pr[0], liker = pr[1];
                List<Item> items = Likes.conflicts(liker, holder);
                if (items.isEmpty()) continue;
                int sev = (int) History.clamp(Math.round(1.5 + 1.5 * items.size() + (T(holder, "vengeful") != 0 ? 2 : 0)
                        - (T(holder, "forgiving") != 0 ? 1 : 0)), 1, 8);
                List<String> lbls = new ArrayList<>();
                for (Item it : items) lbls.add(it.lbl());
                Grudge gg = addGrudge(holder.id, "god", liker.id, sev, null, year, 1,
                        liker.name + " likes " + String.join(" and ", lbls) + ", which " + holder.name + " despises");
                if (gg != null && gg.items == null) gg.items = items;
            }
        }
    }

    private void birthNotes(God g, int year) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : g.traits.entrySet()) parts.add(e.getKey() + " " + e.getValue());
        L(year, "", "traits: " + String.join(", ", parts), "meta", "births", 1);
        conflictGrudges(g, year);
        if (T(g, "secretive") != 0)
            addSecret("EXISTENCE", g.id, null, g.name + " exists, though it hides itself from every record.", List.of(g.id),
                    null, T(g, "secretive"), null, year);
    }

    // ------------------------------------------------------------------ origins

    private void birthGlory(int year) {
        LinkedHashMap<String, Integer> traits = genTraits(R.nextInt(1, 3), List.of());
        LikeSet lk = genLikes(null);
        God g = makeGod("glory", null, null, 10, traits, lk, year);
        g.progenitor = R.nextInt(3, 5);
        HistoryEvent ev = newEvent("glory_birth", year, list(g.id), list(), data());
        L(year, "GLORY", ev.id + " " + g.name + " came from glory and made the world (god-from-glory, power 10)", "birth", "births");
        birthNotes(g, year);
    }

    private God createGod(God cr, String reason, int year, String mot, Integer target, Integer sev, Secret keepSecret) {
        int p = cr.power / 10;
        if (p < 1) return null;
        List<Map.Entry<String, Integer>> sh = R.shuffle(new ArrayList<>(cr.traits.entrySet()));
        int k = R.nextInt(1, 2);
        List<Map.Entry<String, Integer>> inh = sh.subList(0, Math.min(k, sh.size()));
        LinkedHashMap<String, Integer> traits = genTraits(R.nextInt(1, 3), inh);
        LikeSet lk = genLikes(cr);
        God c = makeGod("gods", cr.id, null, p, traits, lk, year);
        EventData d = data();
        d.reason = reason;
        d.target = target;
        HistoryEvent ev = newEvent("creation", year, list(cr.id), list(c.id), d);
        setOp(c.id, cr.id, 7);
        setOp(cr.id, c.id, 6);
        L(year, "CREATION", ev.id + " " + cr.name + " made " + c.name + " (god-from-god, power " + p + ")  reason=" + reason
                + "  [" + mot + "]", "birth", "births");
        birthNotes(c, year);
        if (reason.equals("champion") && target != null)
            addGrudge(c.id, "god", target, sev != null && sev != 0 ? sev : 6, ev, year);
        if (reason.equals("keeper") && keepSecret != null) {
            keepSecret.keepers.add(c.id);
            L(year, "", c.name + " now keeps " + keepSecret.id, "lie", "secrets", 1);
        }
        if (cr.progenitor > 0) cr.progenitor--;
        if (T(cr, "secretive") != 0 && !reason.equals("progenitor") && R.chance(.5))
            addSecret("DEED", cr.id, null, cr.name + " made " + c.name + ".", list(cr.id, c.id), null, T(cr, "secretive"),
                    ev.id, year);
        return c;
    }

    private void emergeCorpse(PendingCorpse pc, int year) {
        God d = gods.get(pc.dead);
        int n = R.nextInt(1, 3);
        List<Map.Entry<String, Integer>> sh = R.shuffle(new ArrayList<>(d.traits.entrySet()));
        int k = R.nextInt(1, 2);
        LinkedHashMap<String, Integer> traits = genTraits(n, sh.subList(0, Math.min(k, sh.size())));
        LikeSet lk = genLikes(d);
        God c = makeGod("corpse", null, d.id, 2 * pc.power, traits, lk, year);
        EventData ed = data();
        ed.killers = pc.killers;
        HistoryEvent ev = newEvent("corpse_birth", year, list(c.id), list(d.id), ed);
        L(year, "CORPSE", ev.id + " " + c.name + " rose from the corpse of " + d.name + " (god-from-corpse, power " + c.power + ")",
                "birth", "births");
        birthNotes(c, year);
        if (R.chance(.8)) for (int kk : pc.killers) if (gods.get(kk).alive) addGrudge(c.id, "god", kk, R.nextInt(6, 9), ev, year);
        List<God> mourners = new ArrayList<>();
        for (God o : alive())
            if (o != c && (friend(o.id, d.id) >= 4 || kin(o.id, d.id)) && !pc.killers.contains(o.id)) mourners.add(o);
        boolean anyKillerAlive = false;
        for (int kk : pc.killers) if (gods.get(kk).alive) anyKillerAlive = true;
        if (!mourners.isEmpty() && anyKillerAlive && R.chance(.6)) {
            List<God> founders = new ArrayList<>();
            founders.add(c);
            founders.addAll(mourners.subList(0, Math.min(3, mourners.size())));
            Principle p = new Principle("AVENGE");
            p.dead = d.id;
            p.killers = new ArrayList<>(pc.killers);
            p.known = true;
            p.target = firstAlive(pc.killers);
            foundGroup(founders, p, year, "rose to avenge " + d.name);
        }
        if (pc.secret != null) {
            pc.secret.knowers.add(c.id);
            L(year, "", c.name + " remembers who killed " + d.name + "; it now knows " + pc.secret.id, "lie", "secrets", 1);
        }
    }

    private Integer firstAlive(List<Integer> ids) {
        for (int x : ids) if (gods.get(x).alive) return x;
        return null;
    }

    private void birthNowhere(int year) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (God g : alive()) {
            lo = Math.min(lo, g.power);
            hi = Math.max(hi, g.power);
        }
        int p = R.nextInt(lo, hi);
        LinkedHashMap<String, Integer> traits = genTraits(R.nextInt(1, 3), List.of());
        LikeSet lk = genLikes(null);
        God c = makeGod("nowhere", null, null, p, traits, lk, year);
        HistoryEvent ev = newEvent("nowhere_birth", year, list(c.id), list(), data());
        L(year, "NOWHERE", ev.id + " " + c.name + " came from nowhere (god-from-nowhere, power " + p + ")", "birth", "births");
        birthNotes(c, year);
    }

    // ------------------------------------------------------------------ motivations

    private List<Motive> motivations(God g) {
        List<Motive> M = new ArrayList<>();
        if (g.progenitor > 0) {
            Motive m = Motive.of("TRAIT", 9);
            m.trait = "progenitor";
            M.add(m);
        }
        for (Grudge gd : grudges) {
            if (gd.resolved != null || gd.holder != g.id) continue;
            if (gd.tt.equals("god") && !gods.get(gd.target).alive) continue;
            if (gd.tt.equals("group") && groups.get(gd.target).dissolved) continue;
            Motive m = Motive.of("GRUDGE", gd.sev);
            m.grudge = gd;
            M.add(m);
        }
        for (God o : alive())
            if (o != g) {
                int f = friend(g.id, o.id);
                if (f != 0) {
                    Motive m = Motive.of("FRIENDSHIP", f);
                    m.target = o;
                    M.add(m);
                }
            }
        for (Map.Entry<String, Integer> e : g.traits.entrySet())
            if (!e.getKey().equals("mad")) {
                Motive m = Motive.of("TRAIT", e.getValue());
                m.trait = e.getKey();
                M.add(m);
            }
        for (Group gr : groupsOf(g.id)) {
            Motive m = Motive.of("LOYALTY", gr.loyalty.get(g.id));
            m.group = gr;
            M.add(m);
        }
        for (Secret s : secrets)
            if (!s.exposed && s.keepers.contains(g.id)) {
                Motive m = Motive.of("SECRET", importanceFor(g, s));
                m.secret = s;
                M.add(m);
            }
        return M;
    }

    private void godAct(God g, int year) {
        int mad = T(g, "mad");
        if (mad != 0 && R.chance(.15 * mad / 9)) {
            madAct(g, year);
            return;
        }
        List<Motive> M = motivations(g);
        if (M.isEmpty()) return;
        Motive d = R.weighted(M, m -> m.score * m.score * (m.type.equals("FRIENDSHIP") ? .35 : 1));
        switch (d.type) {
            case "GRUDGE": actGrudge(g, d, year); break;
            case "FRIENDSHIP": actFriend(g, d, year); break;
            case "TRAIT": actTrait(g, d, year); break;
            case "LOYALTY": council(d.group, year, g, d); break;
            case "SECRET": actSecret(g, d, year); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ basic acts

    private Secret coverUp(HistoryEvent ev, God doer, int year) {
        if (T(doer, "historian") != 0) return null;
        int s = T(doer, "secretive");
        if (s != 0 && R.chance(.6)) {
            List<Integer> knowers = new ArrayList<>();
            for (int t : ev.targets) if (gods.get(t).alive) knowers.add(t);
            Secret sec = addSecret("DEED", doer.id, null, Text.renderEvent(ev, "WHO", null, S), list(doer.id), knowers, s, ev.id, year);
            List<Integer> gt = grudgeTargets(doer, ev);
            Scapegoat sg = scapegoat(ev, doer.id, gt);
            if (sg != null && grudgeScore(doer.id, sg.id) > friend(doer.id, sg.id))
                makeLie(ev, doer.id, "KEEP_SECRET", "SWAP_ACTOR", Claimed.actor(sg.id), sg.id, nm(sg.id) + " was " + sg.desc, year);
            else
                makeLie(ev, doer.id, "KEEP_SECRET", "OMIT_ACTOR", Claimed.actor(null), null, names(ev.targets) + " saw who did it", year);
            return sec;
        }
        if (R.chance(.18)) {
            List<Integer> gt = grudgeTargets(doer, ev);
            if (!gt.isEmpty()) {
                Scapegoat sg = scapegoat(ev, doer.id, gt);
                if (sg != null && gt.contains(sg.id)) {
                    Motive d = Motive.of("GRUDGE", grudgeScore(doer.id, sg.id));
                    if (decide(doer, d, oppHarm(doer, gods.get(sg.id)), year, "blame " + nm(sg.id)))
                        makeLie(ev, doer.id, "SMEAR_RIVAL", "SWAP_ACTOR", Claimed.actor(sg.id), sg.id, nm(sg.id) + " was " + sg.desc, year);
                }
            }
        } else if (T(doer, "proud") != 0 && R.chance(.25) && !ev.targets.isEmpty()) {
            String v = Text.poss(nm(ev.targets.get(0)));
            makeLie(ev, doer.id, "INFLATE_DEEDS", "CHANGE_CAUSE", Claimed.cause(v + " insolence demanded it"), null,
                    v + " account records no provocation", year);
        }
        return null;
    }

    private List<Integer> grudgeTargets(God doer, HistoryEvent ev) {
        List<Integer> gt = new ArrayList<>();
        for (Grudge g : grudges)
            if (g.resolved == null && g.holder == doer.id && g.tt.equals("god") && gods.get(g.target).alive
                    && !ev.targets.contains(g.target)) gt.add(g.target);
        return gt;
    }

    private void offense(God a, God b, int year, String mot) {
        List<Item> ci = Likes.conflicts(a, b);
        if (ci.isEmpty()) return;
        Item item = R.pick(ci);
        EventData d = data();
        d.item = item;
        HistoryEvent ev = newEvent("offense", year, list(a.id), list(b.id), d);
        bump(b.id, a.id, -3);
        L(year, "OFFENSE", ev.id + " " + a.name + " " + Text.actPhrase(item) + ", which " + b.name + " despises  [" + mot + "]",
                "event", "conflict");
        Secret sec = coverUp(ev, a, year);
        if (sec == null) addGrudge(b.id, "god", a.id, R.nextInt(3, 6) + (T(b, "vengeful") != 0 ? 2 : 0), ev, year);
        else addGrudge(b.id, "god", a.id, R.nextInt(2, 4), ev, year);
    }

    private HistoryEvent strike(List<God> atts, God t, int year, String mot, Group gr) {
        if (!t.alive || atts.isEmpty()) return null;
        List<Integer> ids = idsOf(atts);
        int pa = sumP(atts), pt = t.power;
        boolean ok = R.f() < (double) pa / (pa + pt);
        EventData d = data();
        d.success = ok;
        d.group = gr != null ? gr.id : null;
        HistoryEvent ev = newEvent("strike", year, ids, list(t.id), d);
        String who = gr != null ? gr.name + " (" + names(ids) + ")" : atts.get(0).name;
        L(year, "STRIKE", ev.id + " " + who + " " + (ok ? "struck and wounded" : "struck at, and failed to hurt,") + " " + t.name
                + "  power " + pa + " vs " + pt + "  [" + mot + "]", "war", "conflict");
        if (ok) {
            t.power = Math.max(1, t.power - 1);
            if (gr != null || T(atts.get(0), "ambitious") != 0 || T(atts.get(0), "devourer") != 0) {
                atts.get(0).power += 1;
                L(year, "", "power: " + atts.get(0).name + " +1, " + t.name + " −1", "meta", "conflict", 1);
            } else L(year, "", "power: " + t.name + " −1", "meta", "conflict", 1);
        }
        if (gr != null) {
            if (gr.secret && R.chance(.6)) {
                Secret ps = purposeSecret(gr);
                addSecret("DEED", ids.get(0), gr.id, gr.name + " struck " + t.name + " in the " + Text.ord(year) + " year: " + names(ids) + ".",
                        ids, null, ps != null ? ps.importance : 6, ev.id, year);
                L(year, "", t.name + " never saw who struck", "meta", "secrets", 1);
            } else addGrudge(t.id, "group", gr.id, R.nextInt(3, 5) + (T(t, "vengeful") != 0 ? 1 : 0), ev, year);
        } else {
            Secret sec = coverUp(ev, atts.get(0), year);
            addGrudge(t.id, "god", atts.get(0).id, sec != null ? R.nextInt(2, 3) : R.nextInt(3, 5) + (T(t, "vengeful") != 0 ? 2 : 0), ev, year);
        }
        return ev;
    }

    private void seclude(God g, int year, String mot) {
        String place = R.pick(Content.SECLUDE);
        int dur = R.nextInt(8, 25);
        EventData d = data();
        d.place = place;
        d.dur = dur;
        HistoryEvent ev = newEvent("seclusion", year, list(g.id), list(), d);
        g.seclusions.add(new Seclusion(year, year + dur, ev.id));
        g.secludedUntil = year + dur;
        L(year, "SECLUSION", ev.id + " " + g.name + " withdrew " + place + " until " + Text.year(year + dur) + "  [" + mot + "]", "event", "misc");
    }

    private void forgive(God g, God t, Grudge gd, int year, String mot) {
        EventData d = data();
        d.grudge = gd.id;
        HistoryEvent ev = newEvent("forgive", year, list(g.id), list(t.id), d);
        if (gd.tt.equals("god")) {
            gd.resolved = ev.id;
            L(year, "FORGIVE", ev.id + " " + g.name + " forgave " + t.name + "; " + gd.id + " ends  [" + mot + "]", "grudge", "conflict");
        } else {
            gd.forgiven.add(t.id);
            L(year, "FORGIVE", ev.id + " " + g.name + " forgave " + t.name + ", but not " + gname(gd.target) + "; " + gd.id
                    + " still stands for the others  [" + mot + "]", "grudge", "conflict");
        }
        bump(t.id, g.id, 2);
    }

    private void accuse(God g, God t, int year, String mot) {
        HistoryEvent ev = newEvent("accusation", year, list(g.id), list(t.id), data());
        L(year, "ACCUSES", ev.id + " " + g.name + " accused " + t.name + " of plotting against it  [" + mot + "]", "event", "conflict");
        addGrudge(g.id, "god", t.id, R.nextInt(2, 3), ev, year);
        bump(t.id, g.id, -2);
    }

    private void madAct(God g, int year) {
        List<God> friends = new ArrayList<>();
        for (God o : alive()) if (o != g && friend(g.id, o.id) != 0) friends.add(o);
        List<Secret> ks = new ArrayList<>();
        for (Secret s : secrets) if (!s.exposed && s.keepers.contains(g.id)) ks.add(s);
        L(year, "MADNESS", g.name + " acts against its own heart (mad " + T(g, "mad") + ")", "death", "motive");
        if (!friends.isEmpty() && R.chance(.5)) {
            strike(List.of(g), R.pick(friends), year, "MAD", null);
            return;
        }
        if (!ks.isEmpty() && R.chance(.6)) {
            expose(R.pick(ks), g, year, "MAD", null);
            return;
        }
        List<God> o = new ArrayList<>();
        for (God x : alive()) if (x != g) o.add(x);
        if (!o.isEmpty()) accuse(g, R.pick(o), year, "MAD");
    }

    // ------------------------------------------------------------------ war

    private int maxLoyaltyWith(God x, int other) {
        int best = Integer.MIN_VALUE;
        for (Group q : groupsOf(x.id)) if (q.members.contains(other)) best = Math.max(best, q.loyalty.get(x.id));
        return best;
    }

    private void startWar(God g, God t, int year, String mot, List<God> extraA, Group gr) {
        if (!t.alive || atWar(t.id)) return;
        List<Integer> sideA = list(g.id);
        for (God x : extraA) if (x.id != g.id && !atWar(x.id)) sideA.add(x.id);
        List<Integer> sideB = list(t.id);
        for (God x : alive()) {
            if (sideA.contains(x.id) || sideB.contains(x.id) || !active(x, year) || atWar(x.id)) continue;
            int forT = Math.max(friend(x.id, t.id), maxLoyaltyWith(x, t.id));
            int forG = Math.max(friend(x.id, g.id), maxLoyaltyWith(x, g.id));
            if (forT >= 6 && forT > forG && R.chance(.6)) sideB.add(x.id);
            else if (forG >= 6 && forG > forT && R.chance(.6)) sideA.add(x.id);
        }
        EventData d = data();
        d.group = gr != null ? gr.id : null;
        HistoryEvent ev = newEvent("war_start", year, new ArrayList<>(sideA), new ArrayList<>(sideB), d);
        wars.add(new War(ev.id, sideA, sideB, year, year + R.nextInt(3, 14)));
        L(year, "WAR", ev.id + " " + (gr != null ? gr.name + ": " : "") + names(sideA) + " against " + names(sideB) + "  power "
                + sumP(godsOf(sideA)) + " vs " + sumP(godsOf(sideB)) + "  [" + mot + "]", "war", "conflict");
        if (gr != null) addGrudge(t.id, "group", gr.id, 5, ev, year);
        else addGrudge(t.id, "god", g.id, R.nextInt(4, 6), ev, year);
    }

    private void endWar(War w, int year) {
        w.over = true;
        w.endYear = year;
        List<Integer> A = new ArrayList<>(), B = new ArrayList<>();
        for (int id : w.sideA) if (gods.get(id).alive) A.add(id);
        for (int id : w.sideB) if (gods.get(id).alive) B.add(id);
        if (A.isEmpty() || B.isEmpty()) {
            L(year, "WAR END", w.eventId + " ended; one side no longer stands", "war", "conflict");
            return;
        }
        double pa = sumP(godsOf(A)) * (0.75 + R.f() * .5), pb = sumP(godsOf(B)) * (0.75 + R.f() * .5);
        double diff = Math.abs(pa - pb) / Math.max(pa, pb);
        if (diff < .12) {
            EventData d = data();
            d.outcome = "stalemate";
            d.war = w.eventId;
            HistoryEvent ev = newEvent("war_end", year, new ArrayList<>(A), new ArrayList<>(B), d);
            L(year, "WAR END", ev.id + " " + w.eventId + " ended in stalemate", "war", "conflict");
            for (int ldr : List.of(A.get(0), B.get(0)))
                if (T(gods.get(ldr), "proud") != 0 && R.chance(.5))
                    makeLie(ev, ldr, "INFLATE_DEEDS", "INVERT_OUTCOME", Claimed.outcome("victory", ldr), null, "the other side's account", year);
            return;
        }
        List<Integer> win = pa > pb ? A : B, lose = pa > pb ? B : A;
        God lw = gods.get(win.get(0)), ll = gods.get(lose.get(0));
        EventData d = data();
        d.outcome = "victory";
        d.war = w.eventId;
        d.decisive = diff > .4;
        HistoryEvent ev = newEvent("war_end", year, new ArrayList<>(win), new ArrayList<>(lose), d);
        int dw = diff > .4 ? 2 : 1;
        lw.power += dw;
        ll.power = Math.max(1, ll.power - dw);
        L(year, "WAR END", ev.id + " " + names(win) + " prevailed over " + names(lose) + (diff > .4 ? ", a rout" : "") + "  (power "
                + lw.name + " +" + dw + ", " + ll.name + " −" + dw + ")", "war", "conflict");
        Grudge gd = grudgeVs(lw.id, ll.id);
        if (gd != null && diff > .4 && gd.tt.equals("god")) {
            gd.resolved = ev.id;
            L(year, "", gd.id + " satisfied by victory", "grudge", "conflict", 1);
        }
        addGrudge(ll.id, "god", lw.id, R.nextInt(4, 6) + (T(ll, "vengeful") != 0 ? 1 : 0), ev, year);
        if (R.chance(.25 + (T(ll, "proud") != 0 ? .3 : 0)))
            makeLie(ev, ll.id, "COVER_UP", "INVERT_OUTCOME", Claimed.outcome("stalemate", null), null,
                    Text.poss(lw.name) + " account, and " + Text.poss(ll.name) + " lost power", year);
        if (win.size() > 1 && T(lw, "proud") != 0 && R.chance(.6))
            makeLie(ev, lw.id, "INFLATE_DEEDS", "OMIT_ALLIES", Claimed.none(), null,
                    names(win.subList(1, win.size())) + " remember fighting beside them", year);
    }

    // ------------------------------------------------------------------ killing

    private void attemptKill(List<God> atts, God t, int year, String mot, Group gr) {
        if (!t.alive || atts.isEmpty()) return;
        List<Integer> ids = idsOf(atts);
        int pa = sumP(atts), pt = t.power;
        double p = pa > pt ? Math.min(.2, .015 + .22 * (pa - pt) / pa) : .005;
        double roll = R.f();
        String who = gr != null ? gr.name + " (" + names(ids) + ")" : atts.get(0).name;
        if (roll >= p) {
            EventData d = data();
            d.group = gr != null ? gr.id : null;
            HistoryEvent ev = newEvent("kill_attempt", year, ids, list(t.id), d);
            L(year, "KILL FAILED", ev.id + " " + who + " tried to kill " + t.name + " and failed  power " + pa + " vs " + pt + ", p="
                    + Text.toFixed(p, 2) + "  [" + mot + "]", "war", "conflict");
            if (gr != null && gr.secret && R.chance(.5)) {
                addSecret("DEED", ids.get(0), gr.id, gr.name + " tried to kill " + t.name + ": " + names(ids) + ".", ids, null, 9, ev.id, year);
                L(year, "", t.name + " survived without learning who struck", "meta", "secrets", 1);
            } else if (gr != null) addGrudge(t.id, "group", gr.id, 8, ev, year);
            else {
                Secret sec = coverUp(ev, atts.get(0), year);
                addGrudge(t.id, "god", atts.get(0).id, sec != null ? 4 : 8, ev, year);
            }
            return;
        }
        EventData d = data();
        d.group = gr != null ? gr.id : null;
        d.victimPower = pt;
        HistoryEvent ev = newEvent("deicide", year, ids, list(t.id), d);
        List<God> friendsOfT = new ArrayList<>();
        for (God o : alive())
            if (o != t && !ids.contains(o.id) && (friend(o.id, t.id) >= 4 || eq(o.creator, t.id) || eq(t.creator, o.id))) friendsOfT.add(o);
        t.alive = false;
        t.deathYear = year;
        t.killers = new ArrayList<>(ids);
        S.deicides.add(ev);
        L(year, "DEICIDE", ev.id + " " + who + " slew " + t.name + "  power " + pa + " vs " + pt + ", roll " + Text.toFixed(roll, 3)
                + " < " + Text.toFixed(p, 2) + "  [" + mot + "]", "death", "conflict");
        List<String> lb = new ArrayList<>();
        for (Item it : t.likes) lb.add(it.lbl());
        L(year, "", "orphaned likes: " + String.join(", ", lb), "death", "conflict", 1);
        int gain = pt / 2;
        if (gain != 0) {
            atts.get(0).power += gain;
            L(year, "", "power: " + atts.get(0).name + " +" + gain + " (half of " + Text.poss(t.name) + " " + pt + ")", "meta", "conflict", 1);
        }
        boolean hidden = (gr != null && gr.secret && R.chance(.6)) || (gr == null && T(atts.get(0), "secretive") != 0 && R.chance(.6));
        Secret sec = null;
        if (hidden) {
            sec = addSecret("DEED", ids.get(0), gr != null ? gr.id : null, names(ids) + " slew " + t.name + " in the " + Text.ord(year) + " year.",
                    ids, null, 10, ev.id, year);
            makeLie(ev, ids.get(0), "KEEP_SECRET", "OMIT_ACTOR", Claimed.actor(null), null, "the corpse of " + t.name + " remembers its killers",
                    year, LieOpt.group(ids, gr != null ? gr.id : null, 1));
            L(year, "", "no one saw the killers; the heavens do not know who struck", "meta", "secrets", 1);
        } else {
            for (God f : friendsOfT) addGrudge(f.id, gr != null ? "group" : "god", gr != null ? gr.id : atts.get(0).id, R.nextInt(5, 8), ev, year);
            if (ids.size() > 1 && T(atts.get(0), "proud") != 0 && R.chance(.7))
                makeLie(ev, ids.get(0), "INFLATE_DEEDS", "OMIT_ALLIES", Claimed.none(), null, names(ids.subList(1, ids.size())) + " were there", year);
        }
        for (Grudge g : grudges)
            if (g.resolved == null && (g.holder == t.id || (g.tt.equals("god") && g.target == t.id))) g.resolved = ev.id;
        for (int qi = 0; qi < groups.size(); qi++) {
            Group q = groups.get(qi);
            if (q.dissolved) continue;
            if (q.members.contains(t.id)) leaveGroup(q, t.id, year);
            if (q.dissolved) continue;
            if (eq(q.principle.target, t.id)) {
                if (q.principle.kind.equals("AVENGE")) {
                    Integer nx = null;
                    if (q.principle.killers != null)
                        for (int k : q.principle.killers) if (gods.get(k).alive && k != t.id) { nx = k; break; }
                    if (nx != null) {
                        q.principle.target = nx;
                        L(year, "", q.name + " turns on " + nm(nx), "ally", "groups", 1);
                    } else dissolve(q, year, "its purpose is fulfilled");
                } else dissolve(q, year, "its purpose is fulfilled");
            } else if (eq(q.principle.protect, t.id)) dissolve(q, year, t.name + ", whom it protected, is dead");
        }
        {
            List<God> mourners = new ArrayList<>();
            for (God f : friendsOfT) if (f.alive) mourners.add(f);
            mourners.sort((a, b) -> cmp(friend(b.id, t.id) - friend(a.id, t.id)));
            if (mourners.size() > 4) mourners = new ArrayList<>(mourners.subList(0, 4));
            if (mourners.size() >= 2 && R.chance(.7)) {
                boolean known = sec == null;
                Principle pr = new Principle("AVENGE");
                pr.dead = t.id;
                pr.killers = new ArrayList<>(ids);
                pr.known = known;
                pr.target = known ? firstAlive(ids) : null;
                pr.secretId = sec != null ? sec.id : null;
                foundGroup(mourners, pr, year, "grief for " + t.name);
            }
        }
        for (War w : wars)
            if (!w.over && (w.sideA.contains(t.id) || w.sideB.contains(t.id))) {
                w.over = true;
                w.endYear = year;
                L(year, "", w.eventId + " ends with " + Text.poss(t.name) + " death", "war", "conflict", 1);
            }
        if (R.chance(.65)) pendingCorpses.add(new PendingCorpse(t.id, year + R.nextInt(5, 30), pt, ids, sec));
    }

    // ------------------------------------------------------------------ groups

    private int alignScore(God g, Principle p) {
        switch (p.kind) {
            case "OPPOSE":
            case "KILL":
                return eq(p.target, g.id) ? 0 : grudgeScore(g.id, p.target);
            case "AVENGE":
                return Math.max(Math.max(friend(g.id, p.dead), kin(g.id, p.dead) ? 6 : 0),
                        p.target != null ? grudgeScore(g.id, p.target) : 0);
            case "GUARD": {
                Secret s = S.secret(p.secretId);
                if (s == null || s.exposed) return 0;
                return s.keepers.contains(g.id) ? importanceFor(g, s) : (s.knowers.contains(g.id) ? 5 : 0);
            }
            case "PROTECT":
                return Math.max(friend(g.id, p.protect), kin(g.id, p.protect) ? 6 : 0);
            case "RECORD":
                return T(g, "historian");
            case "VEIL":
                return Math.max(T(g, "obfuscator"), T(g, "secretive"));
            default:
                return 0;
        }
    }

    private List<Motive> principleOpp(God g, Principle p) {
        List<Motive> o = new ArrayList<>();
        if (p.target != null && gods.get(p.target).alive) {
            if (p.target == g.id) o.add(Motive.of("SELF", INF));
            else o.addAll(oppHarm(g, gods.get(p.target)));
        }
        if (p.kind.equals("VEIL")) o.addAll(historianOpp(g));
        if (p.kind.equals("GUARD")) o.addAll(historianOpp(g));
        if (T(g, "reclusive") != 0) o.add(Motive.of("TRAIT:reclusive", T(g, "reclusive")));
        return o;
    }

    private String groupName() {
        String n;
        int tries = 0;
        do {
            n = "the " + R.pick(Content.G_ADJ) + " " + R.pick(Content.G_NOUN);
            tries++;
        } while (usedGroupNames.contains(n) && tries < 30);
        usedGroupNames.add(n);
        return n;
    }

    private Integer chooseLeader(Group gr, God init) {
        List<God> ms = godsOf(gr.members);
        ms.sort((a, b) -> cmp(b.power - a.power));
        if (init != null && (T(init, "proud") != 0 || T(init, "ambitious") != 0)) return init.id;
        if (ms.size() > 1 && ms.get(0).power >= 1.5 * ms.get(1).power) return ms.get(0).id;
        return null;
    }

    private static int initLoyalty(God g, boolean founder) {
        return (int) History.clamp(5 + Math.round(T(g, "loyal") / 2.0) - Math.round(T(g, "opportunist") / 2.0) + (founder ? 1 : 0), 1, 10);
    }

    private Secret membershipSecret(Group gr, int m, int year) {
        LinkedHashSet<Integer> keepers = new LinkedHashSet<>();
        keepers.add(m);
        keepers.addAll(gr.members);
        return addSecret("MEMBERSHIP", m, gr.id, nm(m) + " is a member of " + gr.name + ".", keepers, null, R.nextInt(5, 8), null,
                year, 1, true);
    }

    private Group foundGroup(List<God> founders, Principle p, int year, String mot) {
        God init = founders.get(0);
        List<God> members = new ArrayList<>();
        members.add(init);
        for (God f : founders.subList(1, founders.size())) {
            if (!f.alive || members.contains(f)) continue;
            int sup = Math.max(alignScore(f, p), friend(f.id, init.id));
            Motive o = topOf(principleOpp(f, p));
            if (o == null || (o.score != INF && (sup > o.score || (sup == o.score && R.chance(.5))))) members.add(f);
            else L(year, "DECLINES", f.name + " declined " + Text.poss(init.name) + " offer to band together " + Text.principlePhrase(p, S)
                    + " (" + sup + " vs " + mlabel(o) + ")", "meta", "groups");
        }
        if (members.size() < 2) return null;
        boolean anySecretive = false;
        for (God m : members) if (T(m, "secretive") != 0) anySecretive = true;
        boolean secret = p.kind.equals("KILL") || p.kind.equals("VEIL") || p.kind.equals("GUARD") || (!p.kind.equals("RECORD") && anySecretive);
        Group gr = new Group(groups.size(), groupName(), p, secret, idsOf(members), year);
        for (God m : members) gr.loyalty.put(m.id, initLoyalty(m, m == init));
        gr.leader = chooseLeader(gr, init);
        groups.add(gr);
        for (int a : gr.members) for (int b : gr.members) if (a != b) bump(a, b, 2);
        if (p.kind.equals("GUARD")) {
            Secret gs = S.secret(p.secretId);
            if (gs != null) gs.keepers.addAll(gr.members);
        }
        EventData d = data();
        d.group = gr.id;
        d.leader = gr.leader;
        HistoryEvent ev = newEvent("group_found", year, new ArrayList<>(gr.members), p.target != null ? list(p.target) : list(), d);
        L(year, "FOUNDED", ev.id + " " + names(gr.members) + " founded " + gr.name + ", sworn " + Text.principlePhrase(p, S) + "  [" + mot + "]",
                "ally", "groups");
        List<String> loy = new ArrayList<>();
        for (God m : members) loy.add(m.name + " " + gr.loyalty.get(m.id));
        L(year, "", (secret ? "secret" : "public") + "; " + (gr.leader != null ? "led by " + nm(gr.leader) : "leaderless, decides by vote")
                + "; loyalty " + String.join(", ", loy), "meta", "groups", 1);
        if (secret) {
            addSecret("GROUP_PURPOSE", gr.id, gr.id, gr.name + " is a group sworn " + Text.principlePhrase(p, S) + ".", gr.members, null,
                    p.kind.equals("KILL") ? 9 : 7, ev.id, year);
            for (int m : gr.members) membershipSecret(gr, m, year);
            L(year, "", "each member's membership is kept as its own secret", "lie", "secrets", 1);
        }
        return gr;
    }

    private void newLeader(Group gr, int year) {
        List<God> ms = godsOf(gr.members);
        ms.sort((a, b) -> cmp(b.power - a.power));
        God cand = null;
        for (God m : ms) if (T(m, "proud") != 0 || T(m, "ambitious") != 0) { cand = m; break; }
        gr.leader = cand != null ? cand.id : null;
        L(year, "", gr.name + " " + (gr.leader != null ? "now follows " + nm(gr.leader) : "now decides by vote"), "meta", "groups", 1);
    }

    private void leaveGroup(Group gr, int id, int year) {
        List<Integer> nm = new ArrayList<>();
        for (int m : gr.members) if (m != id) nm.add(m);
        gr.members = nm;
        if (eq(gr.leader, id) && !gr.members.isEmpty()) newLeader(gr, year);
        if (gr.members.size() < 2) dissolve(gr, year, "too few remain");
    }

    private void desert(God g, Group gr, int year, String mot) {
        List<Integer> others = new ArrayList<>();
        for (int m : gr.members) if (m != g.id) others.add(m);
        EventData d = data();
        d.group = gr.id;
        HistoryEvent ev = newEvent("defect", year, list(g.id), others, d);
        ev.secretRef = gSecretRef(gr);
        L(year, "DEFECT", ev.id + " " + g.name + " walked away from " + gr.name + "  [" + mot + "]", "ally", "groups");
        for (int m : gr.members) bump(m, g.id, -2);
        leaveGroup(gr, g.id, year);
    }

    private void dissolve(Group gr, int year, String why) {
        if (gr.dissolved) return;
        gr.dissolved = true;
        gr.dissolvedWhy = why;
        gr.dissolvedYear = year;
        L(year, "DISSOLVED", gr.name + " is no more: " + why, "ally", "groups", 1);
    }

    private String gSecretRef(Group gr) {
        if (!gr.secret) return null;
        Secret s = purposeSecret(gr);
        return s != null && !s.exposed ? s.id : null;
    }

    private void invite(Group gr, God c, int year, int bonus) {
        Principle p = gr.principle;
        int sup = Math.max(alignScore(c, p), bonus);
        for (int m : gr.members) sup = Math.max(sup, friend(c.id, m));
        List<Motive> opps = new ArrayList<>(principleOpp(c, p));
        for (int m : gr.members) {
            int s = grudgeScore(c.id, m);
            opps.add(s != 0 ? Motive.of("GRUDGE", s) : null);
        }
        Grudge gg = groupGrudge(c.id, gr.id);
        if (gg != null) opps.add(Motive.of("GRUDGE", gg.sev));
        Motive o = topOf(opps);
        boolean ok = o == null || (o.score != INF && (sup > o.score || (sup == o.score && R.chance(.5))));
        if (!ok) {
            L(year, "", c.name + " refuses the invitation (" + sup + " vs " + mlabel(o) + ")", "meta", "groups", 1);
            return;
        }
        List<Integer> nm = new ArrayList<>(gr.members);
        nm.add(c.id);
        gr.members = nm;
        gr.loyalty.put(c.id, initLoyalty(c, false));
        if (p.kind.equals("GUARD")) {
            Secret gs = S.secret(p.secretId);
            if (gs != null) gs.keepers.add(c.id);
        }
        EventData d = data();
        d.group = gr.id;
        HistoryEvent ev = newEvent("group_join", year, list(c.id), list(), d);
        L(year, "", c.name + " joins " + gr.name + " (loyalty " + gr.loyalty.get(c.id) + ")", "ally", "groups", 1);
        if (gr.secret) {
            Secret ps = purposeSecret(gr);
            if (ps != null) ps.keepers.add(c.id);
            Secret ms = membershipSecret(gr, c.id, year);
            ev.secretRef = ms.id;
            for (Secret s : secrets) if (s.kind.equals("MEMBERSHIP") && eq(s.group, gr.id)) s.keepers.add(c.id);
        }
    }

    private void expel(Group gr, God x, int year) {
        leaveGroup(gr, x.id, year);
        EventData d = data();
        d.group = gr.id;
        HistoryEvent ev = newEvent("group_expel", year, new ArrayList<>(gr.members), list(x.id), d);
        ev.secretRef = gSecretRef(gr);
        L(year, "", x.name + " is cast out of " + gr.name, "ally", "groups", 1);
        if (!gr.dissolved) addGrudge(x.id, "group", gr.id, R.nextInt(4, 6), ev, year, 2);
    }

    private void buildHall(Group gr, int year) {
        gr.hall = true;
        EventData d = data();
        d.group = gr.id;
        HistoryEvent ev = newEvent("hall_built", year, new ArrayList<>(gr.members), list(), d);
        ev.secretRef = gSecretRef(gr);
        L(year, "", gr.name + " raises a hall in the Altus" + (gr.secret ? ", hidden from all others" : ""), "ally", "groups", 1);
        if (gr.secret)
            addSecret("LOCATION", gr.id, gr.id, "The hall of " + gr.name + " lies hidden in the Altus.", gr.members, null, 6, ev.id, year, 2, false);
    }

    private static final class Vote {
        final boolean yes;
        final String why;

        Vote(boolean yes, String why) {
            this.yes = yes;
            this.why = why;
        }
    }

    private Vote vote(Group gr, Proposal P, God m) {
        int sup = Math.max(gr.loyalty.get(m.id), P.support != null ? P.support.applyAsInt(m) : 0);
        Motive o = topOf(P.opp.apply(m));
        if (o == null) return new Vote(true, "no objection");
        boolean yes = o.score != INF && (sup > o.score || (sup == o.score && R.chance(.5)));
        return new Vote(yes, sup + " vs " + mlabel(o));
    }

    private void council(Group gr, int year, God proposer, Motive drive) {
        if (gr.dissolved) return;
        List<Integer> live = new ArrayList<>();
        for (int m : gr.members) if (gods.get(m).alive) live.add(m);
        gr.members = live;
        if (gr.members.size() < 2) {
            dissolve(gr, year, "too few remain");
            return;
        }
        God prop = proposer != null && gr.members.contains(proposer.id) ? proposer : gods.get(R.pick(gr.members));
        Proposal P = genProposal(gr, prop, year);
        if (P == null) return;
        boolean passed;
        String txt;
        God ld = gr.leader != null && gr.members.contains(gr.leader) ? gods.get(gr.leader) : null;
        if (ld != null) {
            Vote v = ld == prop ? new Vote(true, "its own proposal") : vote(gr, P, ld);
            passed = v.yes;
            txt = ld.name + " " + (passed ? "approves" : "rejects") + " (" + v.why + ")";
        } else {
            int y = 0, n = 0;
            for (int m : gr.members) {
                if (m == prop.id) {
                    y++;
                    continue;
                }
                if (vote(gr, P, gods.get(m)).yes) y++;
                else n++;
            }
            passed = y > n || (y == n && R.chance(.5));
            txt = "vote " + y + "–" + n + (y == n ? ", tie broken by chance" : "") + ": " + (passed ? "carried" : "rejected");
        }
        L(year, "COUNCIL", gr.name + ": " + prop.name + " proposes to " + P.label + (drive != null ? " [" + mlabel(drive) + "]" : "") + ". " + txt,
                "ally", "groups");
        if (!passed) return;
        List<God> parts = new ArrayList<>();
        parts.add(prop);
        for (int m : gr.members) {
            if (m == prop.id || (P.exclude != null && P.exclude.contains(m))) continue;
            God g = gods.get(m);
            Motive o = topOf(P.opp.apply(g));
            if (o == null) {
                parts.add(g);
                continue;
            }
            int lo = gr.loyalty.get(m);
            if (o.score == INF || o.score > lo || (o.score == lo && R.chance(.5))) {
                for (int x : gr.members) if (x != m) bump(x, m, -2);
                gr.loyalty.put(m, Math.max(1, lo - 1));
                L(year, "REFUSES", g.name + " refuses to take part (" + mlabel(o) + " > LOYALTY " + lo + "); its standing in " + gr.name + " falls",
                        "meta", "groups", 1);
                if (o.score != INF) addGrudge(m, "group", gr.id, Math.max(1, Math.ceil(o.score / 3)), null, year, 2);
            } else {
                gr.loyalty.put(m, Math.max(1, lo - 1));
                parts.add(g);
                L(year, "COMPLIES", g.name + " takes part against its will (LOYALTY " + lo + " ≥ " + mlabel(o) + ")", "meta", "groups", 1);
                addGrudge(m, "group", gr.id, Math.min(10, o.score), null, year, 2);
            }
        }
        P.run.accept(parts);
    }

    private static List<Motive> none() {
        return new ArrayList<>();
    }

    private Proposal genProposal(Group gr, God prop, int year) {
        Principle p = gr.principle;
        List<Proposal> C = new ArrayList<>();
        List<God> memb = godsOf(gr.members);
        God tgt = p.target != null ? gods.get(p.target) : null;
        String by = "council of " + gr.name;
        if (tgt != null && tgt.alive) {
            Function<God, List<Motive>> hOpp = m -> {
                if (m.id == tgt.id) {
                    List<Motive> l = none();
                    l.add(Motive.of("SELF", INF));
                    return l;
                }
                return oppHarm(m, tgt);
            };
            ToIntFunction<God> sup = m -> grudgeScore(m.id, tgt.id);
            C.add(new Proposal(2, "strike " + tgt.name, hOpp, sup, ps -> strike(ps, tgt, year, by, gr)));
            if (!atWar(tgt.id))
                C.add(new Proposal(.6, "make war on " + tgt.name, hOpp, sup, ps -> startWar(ps.get(0), tgt, year, by, ps.subList(1, ps.size()), gr)));
            C.add(new Proposal(1, "agree on a lie about " + tgt.name, m -> {
                List<Motive> l = new ArrayList<>(hOpp.apply(m));
                l.addAll(historianOpp(m));
                return l;
            }, sup, ps -> smear(ps.get(0), tgt, year, by, gr, ps)));
            if (p.kind.equals("KILL") || p.kind.equals("AVENGE")) {
                int pa = sumP(memb);
                C.add(new Proposal(pa > tgt.power ? 1.1 : .15, "kill " + tgt.name, hOpp, sup, ps -> attemptKill(ps, tgt, year, by, gr)));
            }
        }
        if (p.kind.equals("AVENGE") && !p.known) {
            Secret s = S.secret(p.secretId);
            if (s != null && !s.exposed)
                C.add(new Proposal(3, "hunt the slayers of " + nm(p.dead) + " (" + s.id + ")", m -> none(),
                        m -> Math.max(friend(m.id, p.dead), 6), ps -> investigate(ps, s, year, by, gr)));
        }
        if (p.kind.equals("GUARD")) {
            Secret s = S.secret(p.secretId);
            if (s != null && !s.exposed) {
                List<Integer> th = new ArrayList<>();
                for (int x : s.threats) if (gods.get(x).alive && !gr.members.contains(x)) th.add(x);
                if (!th.isEmpty()) {
                    God x = gods.get(R.pick(th));
                    C.add(new Proposal(2.5, "silence " + x.name + ", who has sought the secret", m -> oppHarm(m, x),
                            m -> importanceFor(m, s), ps -> strike(ps, x, year, by, gr)));
                }
                HistoryEvent ev = s.eventRef != null ? S.event(s.eventRef) : null;
                if (ev != null) {
                    int un = 0;
                    for (God m : memb) if (S.storyOf(ev.id, m.id) == null && T(m, "historian") == 0) un++;
                    if (un >= 2)
                        C.add(new Proposal(1.8, "agree on one story about " + ev.id, HistorySimulator::historianOpp, m -> importanceFor(m, s), ps -> {
                            List<Integer> ts = new ArrayList<>();
                            for (God x : ps) if (S.storyOf(ev.id, x.id) == null && T(x, "historian") == 0) ts.add(x.id);
                            if (ts.size() < 2) return;
                            if (ev.type.equals("location"))
                                makeLie(ev, ts.get(0), "GUARD_SECRET", "FABRICATE_LOCATION", Claimed.place(R.pick(Content.WOODS_PLACES)), null,
                                        "the sanctum's holder gives the true door", year, LieOpt.group(ts, gr.id, 1));
                            else
                                makeLie(ev, ts.get(0), "GUARD_SECRET", "OMIT_ACTOR", Claimed.actor(null), null, "those who were there remember",
                                        year, LieOpt.group(ts, gr.id, 1));
                        }));
                }
            }
        }
        if (p.kind.equals("PROTECT")) {
            God pr = p.protect != null ? gods.get(p.protect) : null;
            if (pr != null && pr.alive) {
                List<God> ag = new ArrayList<>();
                for (God a : aggressorsOf(pr.id, year)) if (!gr.members.contains(a.id)) ag.add(a);
                if (!ag.isEmpty()) {
                    God a = ag.get(0);
                    C.add(new Proposal(2.4, "strike " + a.name + ", who harmed " + pr.name, m -> oppHarm(m, a), m -> friend(m.id, pr.id),
                            ps -> strike(ps, a, year, by, gr)));
                    if (!atWar(a.id) && sumP(memb) >= a.power)
                        C.add(new Proposal(.8, "make war on " + a.name, m -> oppHarm(m, a), m -> friend(m.id, pr.id),
                                ps -> startWar(ps.get(0), a, year, by, ps.subList(1, ps.size()), gr)));
                }
            }
        }
        if (p.kind.equals("RECORD")) {
            List<Secret> sec = new ArrayList<>();
            for (Secret s : secrets) {
                if (s.exposed) continue;
                boolean kept = false;
                for (int m : gr.members) if (s.keepers.contains(m)) kept = true;
                if (!kept) sec.add(s);
            }
            if (!sec.isEmpty()) {
                Secret s = R.pick(sec);
                C.add(new Proposal(2, "uncover a hidden thing (" + s.id + ")", m -> none(), null, ps -> investigate(ps, s, year, by, gr)));
            }
            List<Lie> ls = new ArrayList<>();
            for (Lie l : lies) {
                if (l.exposed) continue;
                boolean told = false;
                for (int t : l.tellers) if (gr.members.contains(t)) told = true;
                if (!told) ls.add(l);
            }
            if (!ls.isEmpty()) {
                Lie l = R.pick(ls);
                C.add(new Proposal(2, "expose " + Text.poss(nm(l.teller)) + " lie (" + l.id + ")", m -> none(), null,
                        ps -> correct(ps, l, year, by, gr)));
            }
        }
        if (p.kind.equals("VEIL"))
            C.add(new Proposal(2, "agree on a false account to spread", HistorySimulator::historianOpp, null,
                    ps -> obfuscate(ps.get(0), year, by, gr, ps)));
        List<God> cands = new ArrayList<>();
        for (God o : alive())
            if (!gr.members.contains(o.id) && !eq(p.target, o.id) && alignScore(o, p) >= 3 && grudgeVs(o.id, prop.id) == null) cands.add(o);
        if (!cands.isEmpty() && gr.members.size() < 6) {
            God c = R.pick(cands);
            C.add(new Proposal(1.2, "invite " + c.name, m -> {
                int s = grudgeScore(m.id, c.id);
                List<Motive> l = none();
                if (s != 0) l.add(Motive.of("GRUDGE", s));
                return l;
            }, m -> friend(m.id, c.id), ps -> invite(gr, c, year, 0)));
        }
        List<God> bad = new ArrayList<>();
        for (God m : memb) {
            if (m == prop) continue;
            Grudge gg = groupGrudge(m.id, gr.id);
            boolean isBad = (gg != null ? gg.sev : 0) >= 3;
            if (!isBad) {
                double s = 0;
                for (int x : gr.members) if (x != m.id) s += getOp(x, m.id);
                isBad = s / (gr.members.size() - 1) < -1;
            }
            if (isBad) bad.add(m);
        }
        if (!bad.isEmpty()) {
            God x = R.pick(bad);
            Proposal pr = new Proposal(2, "cast out " + x.name, m -> {
                List<Motive> l = none();
                if (m == x) l.add(Motive.of("SELF", INF));
                else if (friend(m.id, x.id) != 0) l.add(Motive.of("FRIENDSHIP", friend(m.id, x.id)));
                return l;
            }, null, ps -> expel(gr, x, year));
            pr.exclude = list(x.id);
            C.add(pr);
        }
        if (!gr.hall && gr.members.size() >= 3)
            C.add(new Proposal(.7, "raise a hall in the Altus", m -> none(), null, ps -> buildHall(gr, year)));
        if (gr.leader == null && (T(prop, "proud") != 0 || T(prop, "ambitious") != 0))
            C.add(new Proposal(.6, "make " + prop.name + " its leader", m -> {
                List<Motive> l = none();
                if (m == prop) return l;
                int gs = grudgeScore(m.id, prop.id);
                l.add(gs != 0 ? Motive.of("GRUDGE", gs) : null);
                l.add(T(m, "proud") != 0 ? Motive.of("TRAIT:proud", T(m, "proud")) : null);
                return l;
            }, null, ps -> {
                gr.leader = prop.id;
                L(year, "", prop.name + " now leads " + gr.name, "ally", "groups", 1);
            }));
        if (C.isEmpty()) return null;
        return R.weighted(C, x -> x.w);
    }

    // ------------------------------------------------------------------ internal drama

    private void internalDrama(God g, Group gr, Motive d, int year) {
        List<Integer> symp = new ArrayList<>();
        for (int m : gr.members) if (m != g.id && (getOp(m, g.id) >= 3 || groupGrudge(m, gr.id) != null)) symp.add(m);
        boolean anyKept = false;
        for (Secret s : secrets) if (!s.exposed && eq(s.group, gr.id)) anyKept = true;
        List<Opt> opts = new ArrayList<>();
        opts.add(new Opt(1, "defect"));
        if (!symp.isEmpty()) opts.add(new Opt(1.3, "splinter"));
        if (anyKept || !gr.secret) opts.add(new Opt(1, "betray"));
        String k = R.weighted(opts, o -> o.w).k;
        if (!decide(g, d, List.of(Motive.of("LOYALTY", gr.loyalty.get(g.id))), year, k + " " + gr.name)) return;
        String mot = mlabel(d) + " vs " + gr.name;
        if (k.equals("defect")) {
            desert(g, gr, year, mot);
            return;
        }
        if (k.equals("betray")) {
            betrayGroup(g, gr, year, mot);
            return;
        }
        List<God> followers = godsOf(symp);
        List<Integer> actors = list(g.id);
        actors.addAll(symp);
        EventData ed = data();
        ed.group = gr.id;
        HistoryEvent ev = newEvent("splinter", year, actors, list(), ed);
        ev.secretRef = gSecretRef(gr);
        L(year, "SPLINTER", ev.id + " " + names(actors) + " broke away from " + gr.name + "  [" + mot + "]", "ally", "groups");
        for (int m : new ArrayList<>(actors)) leaveGroup(gr, m, year);
        List<God> founders = new ArrayList<>();
        founders.add(g);
        founders.addAll(followers);
        Group ng = foundGroup(founders, gr.principle.copy(), year, "splinter of " + gr.name);
        if (ng != null) {
            ng.parent = gr.id;
            ev.data.newGroup = ng.id;
            if (!gr.dissolved) for (int m : gr.members) addGrudge(m, "group", ng.id, R.nextInt(3, 5), ev, year);
        }
    }

    private void betrayGroup(God g, Group gr, int year, String mot) {
        List<Secret> ks = new ArrayList<>();
        for (Secret s : secrets) if (!s.exposed && eq(s.group, gr.id) && s.about != g.id) ks.add(s);
        List<Integer> others = new ArrayList<>();
        for (int m : gr.members) if (m != g.id) others.add(m);
        EventData d = data();
        d.group = gr.id;
        d.leaked = !ks.isEmpty();
        HistoryEvent ev = newEvent("betrayal", year, list(g.id), new ArrayList<>(others), d);
        L(year, "BETRAYAL", ev.id + " " + g.name + " betrayed " + gr.name + "  [" + mot + "]", "war", "groups");
        leaveGroup(gr, g.id, year);
        for (int m : others) addGrudge(m, "god", g.id, R.nextInt(5, 8), ev, year);
        if (!ks.isEmpty()) {
            Secret s = null;
            for (Secret x : ks) if (x.kind.equals("GROUP_PURPOSE")) { s = x; break; }
            if (s == null) s = R.pick(ks);
            expose(s, g, year, "betrayal", null);
        } else {
            List<Integer> victims = new ArrayList<>();
            for (int m : others) if (gods.get(m).alive) victims.add(m);
            if (!victims.isEmpty()) strike(List.of(g), gods.get(R.pick(victims)), year, "betrayal", null);
        }
    }

    // ------------------------------------------------------------------ knowledge acts

    private void expose(Secret s, God ex, int year, String mot, Group gr) {
        if (s.exposed) return;
        s.exposed = true;
        s.exposer = ex.id;
        List<Integer> actors = new ArrayList<>();
        if (gr != null) {
            for (int m : gr.members) if (gods.get(m).alive) actors.add(m);
        } else actors.add(ex.id);
        List<Integer> targets = new ArrayList<>();
        for (int k : s.keepers) if (gods.get(k).alive && k != ex.id) targets.add(k);
        EventData d = data();
        d.secret = s.id;
        d.group = gr != null ? gr.id : null;
        HistoryEvent ev = newEvent("exposure", year, actors, targets, d);
        L(year, "EXPOSED", ev.id + " " + (gr != null ? gr.name : ex.name) + " laid bare " + s.id + ": “" + s.text + "”  [" + mot + "]", "lie", "secrets");
        for (Group q : groups) {
            if (q.dissolved) continue;
            if (q.principle.kind.equals("GUARD") && s.id.equals(q.principle.secretId)) dissolve(q, year, "its secret is out");
            else if (q.principle.kind.equals("AVENGE") && !q.principle.known && s.id.equals(q.principle.secretId)) {
                HistoryEvent e = s.eventRef != null ? S.event(s.eventRef) : null;
                List<Integer> ks = new ArrayList<>();
                if (e != null) for (int k : e.actors) if (gods.get(k).alive) ks.add(k);
                q.principle.known = true;
                q.principle.killers = e != null ? new ArrayList<>(e.actors) : new ArrayList<>();
                q.principle.target = ks.isEmpty() ? null : ks.get(0);
                L(year, "", q.name + " learns who slew " + nm(q.principle.dead) + ": " + names(q.principle.killers), "ally", "groups", 1);
                for (int m : q.members) for (int k : ks) addGrudge(m, "god", k, 7, ev, year, 2);
                if (ks.isEmpty()) dissolve(q, year, "its quarry is already dead");
            }
        }
        for (int k : new ArrayList<>(s.keepers))
            if (k != ex.id && gods.get(k).alive && !(gr != null && gr.members.contains(k)))
                addGrudge(k, gr != null ? "group" : "god", gr != null ? gr.id : ex.id, Math.ceil(s.importance / 2.0) + 1, ev, year);
        if (s.kind.equals("GROUP_PURPOSE")) {
            Group q = groups.get(s.group);
            if (q.principle.target != null && gods.get(q.principle.target).alive && !q.dissolved)
                addGrudge(q.principle.target, "group", q.id, q.principle.kind.equals("KILL") ? 8 : 6, ev, year);
        }
        if (s.kind.equals("MEMBERSHIP")) {
            Group q = groups.get(s.group);
            Integer t = q.principle.target;
            if (t != null && gods.get(t).alive && t != s.about) addGrudge(t, "god", s.about, 4, ev, year);
        }
        if (s.kind.equals("DEED") && s.eventRef != null) {
            HistoryEvent e = S.event(s.eventRef);
            if (e.type.equals("deicide")) {
                int v = e.targets.get(0);
                for (God o : alive())
                    if ((friend(o.id, v) >= 4 || eq(o.creator, v) || eq(o.sourceCorpse, v)) && !e.actors.contains(o.id))
                        for (int a : e.actors) addGrudge(o.id, "god", a, 6, ev, year);
            } else {
                for (int v : e.targets) if (gods.get(v).alive) for (int a : e.actors) addGrudge(v, "god", a, 5, ev, year);
            }
        }
    }

    private void investigate(List<God> ps, Secret s, int year, String mot, Group gr) {
        List<God> keepersAlive = new ArrayList<>();
        for (int k : s.keepers) if (gods.get(k).alive) keepersAlive.add(gods.get(k));
        int kp = sumP(keepersAlive), pa = sumP(ps);
        double p = kp != 0 ? (double) pa / (pa + kp) : 1;
        if (R.f() < p) {
            expose(s, ps.get(0), year, mot + ", power " + pa + " vs " + kp, gr);
            return;
        }
        for (God x : ps) s.threats.add(x.id);
        L(year, "FOILED", (gr != null ? gr.name : ps.get(0).name) + " sought " + s.id + " and failed (power " + pa + " vs " + kp
                + "); its keepers have noticed  [" + mot + "]", "meta", "secrets");
    }

    private void correct(List<God> ps, Lie l, int year, String mot, Group gr) {
        God t = gods.get(l.teller);
        int pa = sumP(ps), pt = t.alive ? t.power : 0;
        double threshold = pt != 0 ? (double) pa / (pa + pt) : .9;
        if (R.f() >= threshold) {
            L(year, "FOILED", (gr != null ? gr.name : ps.get(0).name) + " tried to disprove " + l.id + " and failed  [" + mot + "]", "meta", "secrets");
            return;
        }
        l.exposed = true;
        EventData d = data();
        d.lie = l.id;
        d.group = gr != null ? gr.id : null;
        HistoryEvent ev = newEvent("correction", year, idsOf(ps), list(l.teller), d);
        L(year, "CORRECTED", ev.id + " " + (gr != null ? gr.name : ps.get(0).name) + " proved " + Text.poss(nm(l.teller)) + " story false ("
                + l.id + ")  [" + mot + "]", "lie", "secrets");
        for (int x : l.tellers)
            if (gods.get(x).alive) addGrudge(x, gr != null ? "group" : "god", gr != null ? gr.id : ps.get(0).id, R.nextInt(3, 5), ev, year);
        if (l.targetGod != null && gods.get(l.targetGod).alive)
            for (int x : l.tellers) if (gods.get(x).alive) addGrudge(l.targetGod, "god", x, R.nextInt(3, 6), ev, year);
    }

    private static final Set<String> HARMFUL = Set.of("offense", "strike", "kill_attempt", "insult", "betrayal");

    private void smear(God g, God t, int year, String mot, Group gr, List<God> ps) {
        List<HistoryEvent> harmful = new ArrayList<>();
        for (HistoryEvent e : events)
            if (HARMFUL.contains(e.type) && !e.actors.contains(t.id) && !e.targets.contains(t.id) && !e.targets.contains(g.id)
                    && e.secretRef == null && e.year >= t.born) harmful.add(e);
        if (harmful.isEmpty()) return;
        List<HistoryEvent> withAlibi = new ArrayList<>();
        for (HistoryEvent e : harmful) if (presence(t.id, e.year, e.id) != null) withAlibi.add(e);
        HistoryEvent ev = R.pick(!withAlibi.isEmpty() ? withAlibi : harmful);
        Presence al = presence(t.id, ev.year, ev.id);
        LieOpt opt = gr != null ? LieOpt.group(idsOf(ps), gr.id, 1) : LieOpt.indent(0);
        Lie lie = makeLie(ev, g.id, gr != null ? "GROUP_SMEAR" : "SMEAR_RIVAL", "SWAP_ACTOR", Claimed.actor(t.id), t.id,
                al != null ? t.name + " was " + al.desc : names(ev.targets) + " saw who truly did it", year, opt);
        if (lie == null) return;
        for (int v : ev.targets)
            if (gods.get(v).alive && T(gods.get(v), "historian") == 0) {
                bump(v, t.id, -2);
                if (R.chance(.35)) {
                    L(year, "", nm(v) + " believes it", "lie", "secrets", 2);
                    addGrudge(v, "god", t.id, 3, ev, year, 2);
                }
            }
    }

    private static final Set<String> OBFUSCATABLE = Set.of("offense", "strike", "war_end", "deicide", "creation", "corpse_birth",
            "group_found", "kill_attempt");

    private Lie obfuscate(God g, int year, String mot, Group gr, List<God> ps) {
        List<HistoryEvent> pool = new ArrayList<>();
        for (HistoryEvent e : events) if (OBFUSCATABLE.contains(e.type) && e.secretRef == null) pool.add(e);
        if (pool.isEmpty()) return null;
        HistoryEvent ev = R.pick(pool);
        LieOpt opt = gr != null ? LieOpt.group(idsOf(ps), gr.id, 1) : LieOpt.indent(0);
        if (ev.type.equals("war_end")) {
            Claimed c = "stalemate".equals(ev.data.outcome) ? Claimed.outcome("victory", ev.actors.get(0)) : Claimed.outcome("stalemate", null);
            return makeLie(ev, g.id, "OBFUSCATE", "INVERT_OUTCOME", c, null, "any other account of the war", year, opt);
        }
        List<God> others = new ArrayList<>();
        for (God o : alive()) if (!ev.actors.contains(o.id) && !ev.targets.contains(o.id) && o.born <= ev.year) others.add(o);
        if (!others.isEmpty() && !(ev.type.equals("creation") || ev.type.equals("corpse_birth") || ev.type.equals("group_found"))) {
            God o = R.pick(others);
            return makeLie(ev, g.id, "OBFUSCATE", "SWAP_ACTOR", Claimed.actor(o.id), o.id, "any other account names " + names(ev.actors), year, opt);
        }
        return makeLie(ev, g.id, "OBFUSCATE", "SHIFT_DATE", Claimed.year(Math.max(1, ev.year + R.nextInt(-40, 40))), null,
                "the true year is " + ev.year + ", as other accounts show", year, opt);
    }

    private static final Set<String> HARM3 = Set.of("offense", "strike", "kill_attempt");

    private void sowDiscord(God g, God t, int year, String mot) {
        List<God> friends = new ArrayList<>();
        for (God b : alive()) if (b != g && b != t && getOp(b.id, t.id) >= 2) friends.add(b);
        for (God b : R.shuffle(friends)) {
            List<HistoryEvent> evs = new ArrayList<>();
            for (HistoryEvent e : events)
                if (HARM3.contains(e.type) && e.targets.contains(b.id) && !e.actors.contains(t.id) && !e.actors.contains(g.id)
                        && e.secretRef == null && e.year >= t.born && S.storyOf(e.id, g.id) == null) evs.add(e);
            if (evs.isEmpty()) continue;
            HistoryEvent ev = R.pick(evs);
            Presence al = presence(t.id, ev.year, ev.id);
            Lie lie = makeLie(ev, g.id, "SOW_DISCORD", "SWAP_ACTOR", Claimed.actor(t.id), t.id,
                    al != null ? t.name + " was " + al.desc : Text.poss(b.name) + " own memory of who did it", year, LieOpt.indent(0));
            if (lie != null) {
                bump(b.id, t.id, -3);
                L(year, "", b.name + " hears it; " + b.name + "→" + t.name + " −3  [" + mot + "]", "lie", "secrets", 2);
                if (R.chance(.5)) addGrudge(b.id, "god", t.id, R.nextInt(2, 4), ev, year, 2);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ motivation-driven acts

    private void actGrudge(God g, Motive d, int year) {
        Grudge gd = d.grudge;
        if (gd.tt.equals("group")) {
            Group gr = groups.get(gd.target);
            if (gr.members.contains(g.id)) {
                internalDrama(g, gr, d, year);
                return;
            }
            List<Secret> ks = new ArrayList<>();
            for (Secret s : secrets)
                if (!s.exposed && eq(s.group, gr.id) && (s.keepers.contains(g.id) || s.knowers.contains(g.id))) ks.add(s);
            if (!ks.isEmpty() && R.chance(.5)) {
                Secret s = R.pick(ks);
                if (decide(g, d, List.of(Motive.of("SECRET", s.keepers.contains(g.id) ? importanceFor(g, s) : 0)), year, "reveal " + s.id))
                    expose(s, g, year, mlabel(d) + " vs " + gr.name, null);
                return;
            }
            List<Integer> c = new ArrayList<>();
            for (int m : gr.members) if (gods.get(m).alive && !gd.forgiven.contains(m) && m != g.id) c.add(m);
            if (c.isEmpty()) return;
            harm(g, gods.get(R.pick(c)), d, year);
            return;
        }
        List<Secret> kn = new ArrayList<>();
        for (Secret s : secrets)
            if (!s.exposed && s.knowers.contains(g.id) && s.keepers.contains(gd.target) && !s.keepers.contains(g.id)) kn.add(s);
        if (!kn.isEmpty() && R.chance(.35)) {
            Secret s = R.pick(kn);
            if (!decide(g, d, oppHarm(g, gods.get(gd.target)), year, "reveal " + s.id)) return;
            expose(s, g, year, mlabel(d) + " vs " + nm(gd.target) + ", using what it was told", null);
            return;
        }
        harm(g, gods.get(gd.target), d, year);
    }

    private void harm(God g, God t, Motive d, int year) {
        if (!t.alive || t == g) return;
        double sev = d.score;
        List<Opt> opts = new ArrayList<>();
        if (sev >= 9 && (T(g, "devourer") != 0 || T(g, "vengeful") != 0)) opts.add(new Opt(.8, "kill"));
        if (sev >= 6 && !atWar(g.id) && !atWar(t.id)) opts.add(new Opt(1, "war"));
        List<God> allies = new ArrayList<>();
        for (God o : alive())
            if (o != g && o != t && grudgeScore(o.id, t.id) >= 3 && grudgeScore(o.id, t.id) > friend(o.id, t.id)) allies.add(o);
        if (!allies.isEmpty() && sev >= 4) opts.add(new Opt(1.2, "plot"));
        if (g.power >= 10 && t.power >= g.power * .5 && sev >= 6) opts.add(new Opt(.6, "champion"));
        opts.add(new Opt(.7, "smear"));
        boolean friendsT = false;
        for (God b : alive()) if (b != g && b != t && getOp(b.id, t.id) >= 2) friendsT = true;
        if (friendsT && sev >= 3 && g.power <= t.power * 1.2) opts.add(new Opt(1.2, "sow"));
        if (sev >= 3 && !Likes.conflicts(g, t).isEmpty()) opts.add(new Opt(1.5, "offense"));
        if (sev >= 4) opts.add(new Opt(2, "strike"));
        String k = R.weighted(opts, o -> o.w).k;
        String what;
        switch (k) {
            case "kill": what = "kill " + t.name; break;
            case "war": what = "make war on " + t.name; break;
            case "plot": what = "plot against " + t.name; break;
            case "champion": what = "make a champion against " + t.name; break;
            case "smear": what = "spread lies about " + t.name; break;
            case "sow": what = "turn " + Text.poss(t.name) + " friends against it"; break;
            case "offense": what = "offend " + t.name; break;
            default: what = "strike " + t.name;
        }
        if (!decide(g, d, oppHarm(g, t), year, what)) return;
        String mot = mlabel(d) + " vs " + t.name;
        switch (k) {
            case "kill": attemptKill(List.of(g), t, year, mot, null); break;
            case "war": startWar(g, t, year, mot, List.of(), null); break;
            case "plot": {
                List<God> sh = R.shuffle(allies);
                int n = R.nextInt(1, 3);
                List<God> founders = new ArrayList<>();
                founders.add(g);
                founders.addAll(sh.subList(0, Math.min(n, sh.size())));
                Principle p = new Principle((sev >= 8 || T(g, "devourer") != 0) ? "KILL" : "OPPOSE");
                p.target = t.id;
                foundGroup(founders, p, year, mot);
                break;
            }
            case "champion": createGod(g, "champion", year, mot, t.id, (int) sev, null); break;
            case "smear": smear(g, t, year, mot, null, null); break;
            case "sow": sowDiscord(g, t, year, mot); break;
            case "offense": offense(g, t, year, mot); break;
            default: strike(List.of(g), t, year, mot, null);
        }
    }

    private void actFriend(God g, Motive d, int year) {
        God t = d.target;
        if (!t.alive) return;
        friendAct(g, t, mlabel(d) + " for " + t.name, year, d);
    }

    private void friendAct(God g, God t, String mot, int year, Motive d) {
        if (!t.alive || t == g) return;
        List<Choice> C = new ArrayList<>();
        Grudge gd = grudgeVs(g.id, t.id);
        if (gd != null)
            C.add(new Choice(2 + (T(g, "forgiving") != 0 ? 2 : 0) - (T(g, "vengeful") != 0 ? 1.5 : 0), () -> {
                if (!decide(g, d, List.of(Motive.of("GRUDGE", gd.sev)), year, "forgive " + t.name)) return;
                forgive(g, t, gd, year, mot);
            }));
        War w = null;
        for (War x : wars)
            if (!x.over && (x.sideA.contains(t.id) || x.sideB.contains(t.id)) && !x.sideA.contains(g.id) && !x.sideB.contains(g.id)) {
                w = x;
                break;
            }
        if (w != null) {
            War war = w;
            C.add(new Choice(1.6, () -> {
                List<Integer> side = war.sideA.contains(t.id) ? war.sideA : war.sideB;
                God en = gods.get((side == war.sideA ? war.sideB : war.sideA).get(0));
                if (!decide(g, d, oppHarm(g, en), year, "join " + Text.poss(t.name) + " war")) return;
                side.add(g.id);
                bump(g.id, t.id, 2);
                bump(t.id, g.id, 2);
                L(year, "JOINS WAR", g.name + " joined " + war.eventId + " beside " + t.name + "  [" + mot + "]", "war", "conflict");
                addGrudge(en.id, "god", g.id, 3, null, year);
            }));
        }
        List<God> ag = new ArrayList<>();
        for (God a : aggressorsOf(t.id, year)) if (a != g && friend(g.id, a.id) == 0) ag.add(a);
        if (!ag.isEmpty())
            C.add(new Choice(2.2, () -> {
                God a = ag.get(0);
                List<God> co = new ArrayList<>();
                for (God o : alive()) if (o != g && o != t && o != a && friend(o.id, t.id) >= 3) co.add(o);
                if (!co.isEmpty() && !hasProtectGroup(t.id) && R.chance(.5)) {
                    List<God> founders = new ArrayList<>();
                    founders.add(g);
                    founders.addAll(co.subList(0, Math.min(2, co.size())));
                    Principle p = new Principle("PROTECT");
                    p.protect = t.id;
                    if (foundGroup(founders, p, year, mot) != null) return;
                }
                if (!decide(g, d, oppHarm(g, a), year, "avenge " + t.name)) return;
                bump(t.id, g.id, 2);
                strike(List.of(g), a, year, mot + " (defending " + t.name + ")", null);
            }));
        List<Secret> ks = new ArrayList<>();
        for (Secret s : secrets)
            if (!s.exposed && s.keepers.contains(g.id) && !s.keepers.contains(t.id) && !s.knowers.contains(t.id) && s.group == null) ks.add(s);
        if (!ks.isEmpty())
            C.add(new Choice(1, () -> {
                Secret s = R.pick(ks);
                s.knowers.add(t.id);
                bump(t.id, g.id, 2);
                bump(g.id, t.id, 1);
                L(year, "CONFIDES", g.name + " confided " + s.id + " to " + t.name + "  [" + mot + "]", "lie", "secrets");
            }));
        List<God> foes = new ArrayList<>();
        for (God x : alive()) {
            if (x == g || x == t || grudgeScore(g.id, x.id) < 3 || grudgeScore(t.id, x.id) < 3) continue;
            boolean already = false;
            for (Group q : groupsOf(g.id)) if (eq(q.principle.target, x.id)) already = true;
            if (!already) foes.add(x);
        }
        if (!foes.isEmpty())
            C.add(new Choice(1.4, () -> {
                God x = R.pick(foes);
                boolean deadly = grudgeScore(g.id, x.id) >= 8 && grudgeScore(t.id, x.id) >= 8;
                Principle p = new Principle(deadly ? "KILL" : "OPPOSE");
                p.target = x.id;
                foundGroup(List.of(g, t), p, year, mot);
            }));
        if (C.isEmpty()) return;
        R.weighted(C, c -> c.w).run.run();
    }

    private boolean hasProtectGroup(int id) {
        for (Group q : groups) if (!q.dissolved && q.principle.kind.equals("PROTECT") && eq(q.principle.protect, id)) return true;
        return false;
    }

    private static final Set<String> OWN_DEEDS = Set.of("offense", "strike", "war_start", "group_found", "creation", "kill_attempt");

    private God weakest(List<God> c) {
        c.sort((a, b) -> cmp(a.power - b.power));
        return c.get(0);
    }

    private void actTrait(God g, Motive d, int year) {
        String mot = mlabel(d);
        List<God> others = new ArrayList<>();
        for (God o : alive()) if (o != g) others.add(o);
        switch (d.trait) {
            case "progenitor":
                createGod(g, "progenitor", year, mot, null, null, null);
                return;
            case "secretive": {
                if (R.chance(.4)) {
                    seclude(g, year, mot);
                    return;
                }
                List<HistoryEvent> own = new ArrayList<>();
                for (HistoryEvent e : events)
                    if (e.actors.contains(g.id) && e.secretRef == null && S.storyOf(e.id, g.id) == null && OWN_DEEDS.contains(e.type)) own.add(e);
                if (!own.isEmpty()) {
                    HistoryEvent ev = R.pick(own);
                    makeLie(ev, g.id, "HIDE_SELF", "OMIT_ACTOR", Claimed.actor(null), null,
                            (!ev.targets.isEmpty() ? names(ev.targets) : "others") + " remember who it was", year, LieOpt.indent(0));
                    return;
                }
                seclude(g, year, mot);
                return;
            }
            case "obfuscator": {
                List<God> peers = new ArrayList<>();
                Principle veil = new Principle("VEIL");
                for (God o : others) if (alignScore(o, veil) >= 3 && T(o, "historian") == 0) peers.add(o);
                if (!peers.isEmpty() && R.chance(.25) && !inGroupOfKind(g, "VEIL")) {
                    List<God> f = new ArrayList<>();
                    f.add(g);
                    f.addAll(peers.subList(0, Math.min(2, peers.size())));
                    foundGroup(f, new Principle("VEIL"), year, mot);
                    return;
                }
                obfuscate(g, year, mot, null, null);
                return;
            }
            case "historian": {
                List<God> peers = new ArrayList<>();
                for (God o : others) if (T(o, "historian") != 0) peers.add(o);
                if (!peers.isEmpty() && R.chance(.25) && !inGroupOfKind(g, "RECORD")) {
                    List<God> f = new ArrayList<>();
                    f.add(g);
                    f.addAll(peers.subList(0, Math.min(2, peers.size())));
                    foundGroup(f, new Principle("RECORD"), year, mot);
                    return;
                }
                List<Secret> sec = new ArrayList<>();
                for (Secret s : secrets) if (!s.exposed && !s.keepers.contains(g.id)) sec.add(s);
                List<Lie> ls = new ArrayList<>();
                for (Lie l : lies) if (!l.exposed && !l.tellers.contains(g.id)) ls.add(l);
                if (!sec.isEmpty() && (R.chance(.5) || ls.isEmpty())) {
                    investigate(List.of(g), R.pick(sec), year, mot, null);
                    return;
                }
                if (!ls.isEmpty()) correct(List.of(g), R.pick(ls), year, mot, null);
                return;
            }
            case "vengeful": {
                List<Grudge> gs = new ArrayList<>();
                for (Grudge x : grudges) if (x.resolved == null && x.holder == g.id && x.sev < 10) gs.add(x);
                if (!gs.isEmpty()) {
                    gs.sort((a, b) -> cmp(b.sev - a.sev));
                    Grudge x = gs.get(0);
                    x.sev++;
                    L(year, "BROODS", g.name + " broods on " + x.id + "; sev=" + x.sev + "  [" + mot + "]", "grudge", "conflict");
                }
                return;
            }
            case "forgiving": {
                List<Grudge> gs = new ArrayList<>();
                for (Grudge x : grudges) if (x.resolved == null && x.holder == g.id && x.tt.equals("god") && gods.get(x.target).alive) gs.add(x);
                if (!gs.isEmpty()) {
                    Grudge x = R.pick(gs);
                    if (!decide(g, d, List.of(Motive.of("GRUDGE", x.sev)), year, "forgive " + nm(x.target))) return;
                    forgive(g, gods.get(x.target), x, year, mot);
                }
                return;
            }
            case "proud": {
                List<HistoryEvent> won = new ArrayList<>();
                for (HistoryEvent e : events)
                    if (((e.type.equals("war_end") && "victory".equals(e.data.outcome)) || e.type.equals("deicide")) && e.actors.get(0) == g.id
                            && e.actors.size() > 1 && S.storyOf(e.id, g.id) == null && e.secretRef == null) won.add(e);
                if (!won.isEmpty() && R.chance(.6)) {
                    HistoryEvent ev = R.pick(won);
                    makeLie(ev, g.id, "INFLATE_DEEDS", "OMIT_ALLIES", Claimed.none(), null,
                            names(ev.actors.subList(1, ev.actors.size())) + " were there too", year, LieOpt.indent(0));
                    return;
                }
                List<God> c = new ArrayList<>();
                for (God o : others) if (o.power < g.power && !Likes.conflicts(g, o).isEmpty()) c.add(o);
                if (!c.isEmpty()) {
                    God t = R.pick(c);
                    if (!decide(g, d, oppHarm(g, t), year, "flaunt what " + t.name + " despises")) return;
                    offense(g, t, year, mot + " (flaunting)");
                }
                return;
            }
            case "ambitious": {
                if (g.power >= 10 && R.chance(.15)) {
                    createGod(g, "servant", year, mot, null, null, null);
                    return;
                }
                List<God> c = new ArrayList<>();
                for (God o : others) if (o.power < g.power) c.add(o);
                if (c.isEmpty()) return;
                God t = weakest(c);
                if (!decide(g, d, oppHarm(g, t), year, "take power from " + t.name)) return;
                strike(List.of(g), t, year, mot, null);
                return;
            }
            case "loyal": {
                List<Group> gs = groupsOf(g.id);
                if (!gs.isEmpty() && R.chance(.6)) {
                    council(R.pick(gs), year, g, d);
                    return;
                }
                List<God> f = new ArrayList<>();
                for (God o : others) if (friend(g.id, o.id) != 0) f.add(o);
                if (!f.isEmpty()) friendAct(g, R.pick(f), mot, year, d);
                return;
            }
            case "opportunist": {
                Function<Group, Integer> gp = q -> sumP(godsOf(q.members));
                List<Group> mine = groupsOf(g.id);
                List<Group> mineSorted = new ArrayList<>(mine);
                mineSorted.sort((a, b) -> cmp(gp.apply(a) - gp.apply(b)));
                Group weakestGroup = mineSorted.isEmpty() ? null : mineSorted.get(0);
                List<Group> cands = new ArrayList<>();
                for (Group q : groups)
                    if (!q.dissolved && !q.secret && !q.members.contains(g.id) && q.members.size() < 7
                            && (weakestGroup == null || gp.apply(q) > gp.apply(weakestGroup) * 1.15)) cands.add(q);
                if (!cands.isEmpty() && R.chance(.8)) {
                    cands.sort((a, b) -> cmp(gp.apply(b) - gp.apply(a)));
                    Group q = cands.get(0);
                    if (weakestGroup != null) {
                        if (!decide(g, d, List.of(Motive.of("LOYALTY", weakestGroup.loyalty.get(g.id))), year,
                                "desert " + weakestGroup.name + " for " + q.name)) return;
                        desert(g, weakestGroup, year, mot + " (for stronger " + q.name + ")");
                    }
                    invite(q, g, year, T(g, "opportunist"));
                    return;
                }
                List<War> ws = new ArrayList<>();
                for (War w : wars) if (!w.over && !w.sideA.contains(g.id) && !w.sideB.contains(g.id)) ws.add(w);
                if (!ws.isEmpty()) {
                    War w = R.pick(ws);
                    int pa = sumP(godsOf(w.sideA)), pb = sumP(godsOf(w.sideB));
                    List<Integer> side = pa >= pb ? w.sideA : w.sideB, other = side == w.sideA ? w.sideB : w.sideA;
                    God en = gods.get(other.get(0));
                    if (!decide(g, d, oppHarm(g, en), year, "join the stronger side of " + w.eventId)) return;
                    side.add(g.id);
                    List<Integer> beside = new ArrayList<>();
                    for (int x : side) if (x != g.id) beside.add(x);
                    L(year, "JOINS WAR", g.name + " joined " + w.eventId + " on the stronger side, beside " + names(beside) + "  [" + mot + "]",
                            "war", "conflict");
                    addGrudge(en.id, "god", g.id, 3, null, year);
                }
                return;
            }
            case "zealous": {
                if (g.answered == null) g.answered = new HashSet<>();
                Set<String> done = g.answered;
                List<HistoryEvent> offs = new ArrayList<>();
                for (HistoryEvent e : events)
                    if (e.type.equals("offense") && year - e.year <= 60 && !done.contains(e.id) && !e.actors.contains(g.id)
                            && gods.get(e.actors.get(0)).alive && Likes.contains(g.dislikes, e.data.item) && e.secretRef == null) offs.add(e);
                if (offs.isEmpty()) return;
                HistoryEvent ev = R.pick(offs);
                done.add(ev.id);
                God t = gods.get(ev.actors.get(0));
                if (!decide(g, d, oppHarm(g, t), year, "punish " + t.name + " for " + ev.id)) return;
                strike(List.of(g), t, year, mot + ", punishing " + ev.id, null);
                return;
            }
            case "reclusive":
                seclude(g, year, mot);
                return;
            case "gregarious": {
                List<God> f = new ArrayList<>();
                for (God o : others) if (friend(g.id, o.id) != 0) f.add(o);
                if (f.isEmpty()) {
                    if (g.power >= 10 && R.chance(.4)) createGod(g, "companion", year, mot, null, null, null);
                    return;
                }
                friendAct(g, R.pick(f), mot, year, d);
                return;
            }
            case "paranoid": {
                if (others.isEmpty()) return;
                List<God> sorted = new ArrayList<>(others);
                sorted.sort((a, b) -> cmp(getOp(g.id, a.id) - getOp(g.id, b.id)));
                accuse(g, sorted.get(0), year, mot);
                return;
            }
            case "protective": {
                List<God> mine = new ArrayList<>();
                for (God o : others) if (friend(g.id, o.id) != 0 || kin(g.id, o.id)) mine.add(o);
                List<God> hurt = new ArrayList<>();
                for (God o : mine) {
                    boolean any = false;
                    for (God a : aggressorsOf(o.id, year)) if (a != g) any = true;
                    if (any) hurt.add(o);
                }
                if (hurt.isEmpty()) return;
                God v = R.pick(hurt);
                List<God> ags = new ArrayList<>();
                for (God x : aggressorsOf(v.id, year)) if (x != g) ags.add(x);
                God a = R.pick(ags);
                if (!decide(g, d, oppHarm(g, a), year, "avenge " + v.name)) return;
                List<God> co = new ArrayList<>();
                for (God o : mine) if (o != v && friend(o.id, v.id) >= 3) co.add(o);
                if (!co.isEmpty() && !hasProtectGroup(v.id) && R.chance(.5)) {
                    List<God> f = new ArrayList<>();
                    f.add(g);
                    f.addAll(co.subList(0, Math.min(2, co.size())));
                    Principle p = new Principle("PROTECT");
                    p.protect = v.id;
                    if (foundGroup(f, p, year, mot) != null) return;
                }
                strike(List.of(g), a, year, mot + " (defending " + v.name + ")", null);
                return;
            }
            case "devourer": {
                List<God> c = new ArrayList<>();
                for (God o : others) if (friend(g.id, o.id) == 0) c.add(o);
                if (c.isEmpty()) return;
                God t = weakest(c);
                if (!decide(g, d, oppHarm(g, t), year, "devour " + t.name)) return;
                if (g.power > t.power) {
                    attemptKill(List.of(g), t, year, mot, null);
                    return;
                }
                List<God> allies = new ArrayList<>();
                for (God o : others) if (o != t && grudgeScore(o.id, t.id) >= 3) allies.add(o);
                if (!allies.isEmpty()) {
                    List<God> f = new ArrayList<>();
                    f.add(g);
                    f.addAll(allies.subList(0, Math.min(2, allies.size())));
                    Principle p = new Principle("KILL");
                    p.target = t.id;
                    foundGroup(f, p, year, mot);
                }
                return;
            }
            default:
                return;
        }
    }

    private boolean inGroupOfKind(God g, String kind) {
        for (Group q : groupsOf(g.id)) if (q.principle.kind.equals(kind)) return true;
        return false;
    }

    private void actSecret(God g, Motive d, int year) {
        Secret s = d.secret;
        String mot = mlabel(d) + " (" + s.id + ")";
        List<Integer> th = new ArrayList<>();
        for (int x : s.threats) if (gods.get(x).alive && x != g.id) th.add(x);
        if (!th.isEmpty() && R.chance(.6)) {
            God t = gods.get(R.pick(th));
            if (!decide(g, d, oppHarm(g, t), year, "silence " + t.name)) return;
            strike(List.of(g), t, year, mot + " silencing a seeker", null);
            return;
        }
        boolean guarded = false;
        for (Group q : groups) if (!q.dissolved && q.principle.kind.equals("GUARD") && s.id.equals(q.principle.secretId)) guarded = true;
        if (s.importance >= 7 && s.group == null && !guarded) {
            LinkedHashSet<Integer> pool = new LinkedHashSet<>(s.keepers);
            for (int k : s.knowers) if (friend(g.id, k) >= 3) pool.add(k);
            List<God> ks = new ArrayList<>();
            for (int k : pool) {
                God kg = gods.get(k);
                if (kg.alive && kg != g && T(kg, "historian") == 0) ks.add(kg);
            }
            if (!ks.isEmpty() && T(g, "historian") == 0 && R.chance(.5)) {
                List<God> f = new ArrayList<>();
                f.add(g);
                f.addAll(ks.subList(0, Math.min(3, ks.size())));
                Principle p = new Principle("GUARD");
                p.secretId = s.id;
                if (foundGroup(f, p, year, mot) != null) return;
            }
        }
        if (s.eventRef != null && S.storyOf(s.eventRef, g.id) == null) {
            HistoryEvent ev = S.event(s.eventRef);
            makeLie(ev, g.id, "KEEP_SECRET", "OMIT_ACTOR", Claimed.actor(null), null, "those who were there remember", year, LieOpt.indent(0));
            return;
        }
        if (g.power >= 10 && s.importance >= 8 && R.chance(.15)) {
            createGod(g, "keeper", year, mot, null, null, s);
            return;
        }
        if (R.chance(.25)) seclude(g, year, mot);
    }

    // ------------------------------------------------------------------ upkeep and the year loop

    private void upkeep(int year) {
        List<God> al = alive();
        for (God a : al)
            for (God b : al) {
                if (a == b) continue;
                double r = T(a, "forgiving") != 0 ? .08 : T(a, "vengeful") != 0 ? .03 : .05;
                double o = getOp(a.id, b.id);
                setOp(a.id, b.id, o + (S.base(a.id, b.id) - o) * r);
            }
        for (Grudge g : grudges) {
            God h = gods.get(g.holder);
            if (g.resolved == null && T(h, "vengeful") != 0 && g.sev < 10 && R.chance(.02 * T(h, "vengeful") / 5)) {
                g.sev++;
                if (g.sev >= 8)
                    L(year, "FESTERS", g.id + " " + nm(g.holder) + " → " + tlabel(g.tt, g.target) + " festers, sev=" + g.sev, "grudge", "conflict");
            }
        }
        for (God g : al) if (R.chance(.015 + T(g, "ambitious") * .002)) g.power++;
    }

    private History run() {
        for (int year = 1; year <= YEARS; year++) {
            if (year == 1) birthGlory(year);
            for (int i = 0; i < pendingCorpses.size(); i++) {
                PendingCorpse pc = pendingCorpses.get(i);
                if (pc.year == year) emergeCorpse(pc, year);
            }
            if (year > 15 && !alive().isEmpty() && alive().size() < 14 && R.chance(.004)) birthNowhere(year);
            for (int i = 0; i < wars.size(); i++) {
                War w = wars.get(i);
                if (!w.over && w.endYear == year) endWar(w, year);
            }
            upkeep(year);
            if (alive().isEmpty()) break;
            for (God g : R.shuffle(alive())) {
                if (!active(g, year)) continue;
                double p = g.progenitor > 0 ? .3 : ACT_P;
                if (R.chance(p)) godAct(g, year);
            }
            for (int i = 0; i < groups.size(); i++) {
                Group gr = groups.get(i);
                if (!gr.dissolved && R.chance(.05)) council(gr, year, null, null);
            }
        }
        finish();
        return S;
    }

    private List<God> claim(String name, List<God> pool, List<String> places) {
        if (pool.isEmpty()) {
            S.regions.add(new Region(name, "unclaimed"));
            return List.of();
        }
        List<God> hs = (pool.size() > 1 && pool.get(0).power - pool.get(1).power <= 2) ? List.of(pool.get(0), pool.get(1)) : List.of(pool.get(0));
        S.regions.add(new Region(name, hs.size() > 1 ? "contested by " + hs.get(0).name + " and " + hs.get(1).name : "held by " + hs.get(0).name));
        for (God h : hs) {
            String place = R.pick(places);
            EventData d = data();
            d.place = place;
            d.region = name;
            HistoryEvent ev = newEvent("location", YEARS, list(h.id), list(), d);
            L(YEARS, "LOCATION", ev.id + " " + Text.poss(h.name) + " sanctum lies " + place, "event", "misc");
            if (T(h, "secretive") != 0)
                addSecret("LOCATION", h.id, null, "The door to " + Text.poss(h.name) + " sanctum lies " + place + ".", list(h.id), null,
                        T(h, "secretive"), ev.id, YEARS);
            God liar = null;
            for (God x : R.shuffle(alive()))
                if (x != h && (T(x, "obfuscator") != 0 || ((T(x, "secretive") != 0 || T(x, "paranoid") != 0) && grudgeScore(x.id, h.id) > 0))) {
                    liar = x;
                    break;
                }
            if (liar != null && R.chance(.75))
                makeLie(ev, liar.id, T(liar, "obfuscator") != 0 ? "OBFUSCATE" : "LURE_MORTALS", "FABRICATE_LOCATION",
                        Claimed.place(R.pick(Content.WOODS_PLACES)), h.id, Text.poss(h.name) + " own account gives the true door " + place,
                        YEARS, LieOpt.indent(0));
        }
        return hs;
    }

    private void finish() {
        List<God> live = alive();
        live.sort((a, b) -> cmp(b.power - a.power));
        S.regions.add(new Region("The Woods", "unclaimed; the same in every world"));
        L(YEARS, "ALTUS", "region claims computed", "head", "misc");
        List<God> m = claim("The Mountain", live, Content.MOUNTAIN_PLACES);
        List<God> rest = new ArrayList<>();
        for (God g : live) if (!m.contains(g)) rest.add(g);
        claim("The House on the mountainside", rest, Content.HOUSE_PLACES);
        for (Group gr : groups)
            if (gr.hall) {
                Secret hs = purposeSecret(gr);
                S.regions.add(new Region("Hall of " + gr.name,
                        (gr.dissolved ? "abandoned" : "kept by its members") + (gr.secret && hs != null && !hs.exposed ? "; hidden" : "")));
            }
        for (HistoryEvent d : S.deicides)
            S.regions.add(new Region("Ruins of " + nm(d.targets.get(0)),
                    "a broken shrine on the Mountain's northern slope, from the " + Text.ord(d.year) + " year"));

        for (Grudge g : grudges)
            if (g.resolved == null && g.sev >= 5 && gods.get(g.holder).alive
                    && (g.tt.equals("group") ? !groups.get(g.target).dissolved : gods.get(g.target).alive))
                S.tensions.add(g.id + ": " + nm(g.holder) + " holds a grudge against " + tlabel(g.tt, g.target) + " (sev " + g.sev + ")");
        for (Group gr : groups) {
            if (gr.dissolved) continue;
            List<Integer> unhappy = new ArrayList<>();
            for (int mm : gr.members) {
                Grudge gg = groupGrudge(mm, gr.id);
                if ((gg != null ? gg.sev : 0) >= gr.loyalty.get(mm)) unhappy.add(mm);
            }
            if (!unhappy.isEmpty())
                S.tensions.add(gr.name + " is strained: " + names(unhappy) + " resent" + (unhappy.size() == 1 ? "s" : "") + " it more than "
                        + (unhappy.size() == 1 ? "it is" : "they are") + " loyal");
            if (gr.principle.kind.equals("KILL") && gods.get(gr.principle.target).alive)
                S.tensions.add(gr.name + " still plots to kill " + nm(gr.principle.target) + (gr.secret ? " (in secret)" : ""));
        }
        for (War w : wars)
            if (!w.over) S.tensions.add(w.eventId + ": war between " + names(w.sideA) + " and " + names(w.sideB) + " is unresolved");
        int hiddenS = 0;
        for (Secret s : secrets) if (!s.exposed) hiddenS++;
        if (hiddenS > 0) S.tensions.add(hiddenS + " secret" + (hiddenS > 1 ? "s" : "") + " still kept");
        int liesLeft = 0;
        for (Lie l : lies) if (!l.exposed) liesLeft++;
        if (liesLeft > 0) S.tensions.add(liesLeft + " lie" + (liesLeft > 1 ? "s" : "") + " still believed");
    }
}
