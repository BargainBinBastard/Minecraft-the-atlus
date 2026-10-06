package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.Group;
import io.github.bargainbinbastard.altus.history.History.Grudge;
import io.github.bargainbinbastard.altus.history.History.HistoryEvent;
import io.github.bargainbinbastard.altus.history.History.Lie;
import io.github.bargainbinbastard.altus.history.History.Secret;

/**
 * Draws a sample of lore fragments from a history: event fragments told by gods (possibly as
 * their official lies), secret fragments, and static knowledge. Mirrors the prototype.
 */
public final class Fragments {
    private Fragments() {}

    public static final class StaticLie {
        public final String id, motive, distortion, contra;
        public final int teller;

        StaticLie(String id, String distortion, String contra, int teller) {
            this.id = id;
            this.motive = "OBFUSCATE";
            this.distortion = distortion;
            this.contra = contra;
            this.teller = teller;
        }
    }

    public static final class Offering {
        public final String god, item;

        Offering(String god, String item) {
            this.god = god;
            this.item = item;
        }
    }

    public static final class Ritual {
        public final int tier;
        public final String effect;
        public final List<Offering> offerings;
        public final boolean misfire;

        Ritual(int tier, String effect, List<Offering> offerings, boolean misfire) {
            this.tier = tier;
            this.effect = effect;
            this.offerings = offerings;
            this.misfire = misfire;
        }
    }

    public static final class Fragment {
        /** "EVENT", "SECRET" or "STATIC". */
        public String source;
        /** For static fragments: LIKES, DISLIKES, OPINION, GRUDGE, NATURE, GROUP. */
        public String kind;
        public HistoryEvent ev;
        public God teller;
        public String reveals;
        public Lie lie;
        public StaticLie staticLie;
        public Secret secret;
        public String secretSource;
        public String title;
        public String text;
        public String att;
        public String tell;
        public Ritual ritual;
        public int no;

        public boolean isFalse() {
            return lie != null || staticLie != null;
        }
    }

    static final Map<String, List<String>> REVEALS = new HashMap<>();
    static final Map<String, Double> FW = new HashMap<>();
    static final Map<String, Double> COMPLEX = new HashMap<>();
    static final List<List<String>> EFFECTS = List.of(
            List.of("Instantly grow all planted crops nearby to full", "Gain favor with a chosen god",
                    "Locate a newly generated structure", "Mend every tool in your inventory"),
            List.of("Clear or call the weather", "Repel hostile mobs from the area for one day",
                    "Extend your next visit to the Altus", "Reveal the nearest dungeon tome"),
            List.of("Bring one item of your choosing into the Altus", "Add strength to one side of the coming event",
                    "Nudge one god's opinion of another", "Open a sealed door in the Altus for one visit"));
    static final Map<String, String> SECRET_TITLE = Map.of("EXISTENCE", "A Hidden God", "DEED", "A Hidden Deed",
            "GROUP_PURPOSE", "A Hidden Purpose", "MEMBERSHIP", "A Hidden Allegiance", "LOCATION", "A Hidden Place");

    static {
        r("offense", "WHO", "WHY", "WHEN"); r("strike", "WHO", "WHEN"); r("kill_attempt", "WHO", "WHEN");
        r("deicide", "WHO", "OUTCOME", "WHEN"); r("war_start", "WHO", "WHY", "WHEN"); r("war_end", "OUTCOME", "WHO");
        r("creation", "WHO", "WHEN"); r("corpse_birth", "WHO", "WHEN"); r("nowhere_birth", "WHO", "WHEN");
        r("glory_birth", "WHO"); r("group_found", "WHO", "WHY", "WHEN"); r("group_join", "WHO"); r("group_expel", "WHO");
        r("defect", "WHO"); r("splinter", "WHO", "WHEN"); r("betrayal", "WHO"); r("hall_built", "WHO");
        r("exposure", "WHO"); r("correction", "WHO"); r("forgive", "WHO"); r("seclusion", "WHEN", "LOCATION");
        r("accusation", "WHO"); r("location", "LOCATION");
        String[] fw = {"creation", "3", "corpse_birth", "4", "nowhere_birth", "3", "glory_birth", "2", "offense", "2",
                "strike", "1.5", "kill_attempt", "4", "deicide", "6", "war_start", "2", "war_end", "3", "group_found", "3",
                "group_join", "1", "group_expel", "2", "defect", "1.5", "splinter", "3", "betrayal", "4", "hall_built", "2",
                "exposure", "4", "correction", "3", "forgive", "1", "seclusion", ".6", "accusation", "1", "location", "3"};
        for (int i = 0; i < fw.length; i += 2) FW.put(fw[i], Double.parseDouble(fw[i + 1]));
        String[] cx = {"offense", ".05", "strike", ".05", "kill_attempt", ".2", "deicide", ".4", "war_start", ".1",
                "war_end", ".2", "betrayal", ".2", "splinter", ".15", "group_found", ".1", "exposure", ".15",
                "location", ".15", "creation", ".1", "corpse_birth", ".2"};
        for (int i = 0; i < cx.length; i += 2) COMPLEX.put(cx[i], Double.parseDouble(cx[i + 1]));
    }

    private static void r(String type, String... reveals) {
        REVEALS.put(type, List.of(reveals));
    }

    private static String revealFor(HistoryEvent ev, Lie lie, Rng R) {
        List<String> base = REVEALS.getOrDefault(ev.type, List.of("WHO"));
        if (lie == null) return R.pick(base);
        List<String> ok = null;
        switch (lie.distortion) {
            case "CHANGE_CAUSE": ok = List.of("WHY"); break;
            case "FABRICATE_LOCATION": ok = List.of("LOCATION"); break;
            case "SHIFT_DATE": return "WHEN";
            default: break;
        }
        List<String> opts = new ArrayList<>();
        if (ok != null) {
            for (String x : base) if (ok.contains(x)) opts.add(x);
        } else opts.addAll(base);
        return R.pick(!opts.isEmpty() ? opts : base);
    }

    private static String firstTell(God teller) {
        for (String t : teller.traits.keySet()) if (Content.TELLS.containsKey(t)) return Content.TELLS.get(t);
        return null;
    }

    public static List<Fragment> generate(History S) {
        String seed = S.seed;
        Rng R = new Rng(seed, "fragments");
        Set<Integer> hiddenEx = new HashSet<>();
        for (Secret s : S.secrets) if (s.kind.equals("EXISTENCE") && !s.exposed) hiddenEx.add(s.about);
        Set<String> withStory = new HashSet<>();
        for (Lie l : S.story.values()) withStory.add(l.eventId);

        List<Fragment> frags = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Map<String, Integer> perEv = new HashMap<>();
        List<HistoryEvent> pool = new ArrayList<>();
        for (HistoryEvent e : S.events) if (FW.containsKey(e.type) && !hidden(S, e, hiddenEx)) pool.add(e);
        int tries = 0;
        while (frags.size() < 12 && tries < 600 && !pool.isEmpty()) {
            tries++;
            HistoryEvent ev = R.weighted(pool, e -> FW.get(e.type) * (withStory.contains(e.id) ? 2 : 1));
            LinkedHashSet<Integer> inv = new LinkedHashSet<>(ev.actors);
            inv.addAll(ev.targets);
            List<God> cand = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (God g : S.gods) {
                if (g.born > ev.year || hiddenEx.contains(g.id)) continue;
                if (g.deathYear != null && (g.deathYear < ev.year || (ev.type.equals("deicide") && ev.targets.contains(g.id)))) continue;
                double w;
                if (inv.contains(g.id)) w = 3;
                else {
                    boolean close = false;
                    for (int x : inv) if (S.getOp(g.id, x) >= 3) { close = true; break; }
                    w = close ? 1 : .3;
                }
                if (g.has("historian")) w += 1.5;
                if (g.has("secretive")) w *= .3;
                if (S.storyOf(ev.id, g.id) != null) w += 3;
                cand.add(g);
                weights.add(w);
            }
            if (cand.isEmpty()) continue;
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < cand.size(); i++) idx.add(i);
            God teller = cand.get(R.weighted(idx, weights::get));
            Lie lie = S.storyOf(ev.id, teller.id);
            String reveals = revealFor(ev, lie, R);
            String k = ev.id + "|" + teller.id + "|" + reveals;
            if (used.contains(k) || perEv.getOrDefault(ev.id, 0) >= 2) continue;
            used.add(k);
            perEv.merge(ev.id, 1, Integer::sum);
            frags.add(eventFragment(ev, teller, reveals, lie, S, R));
        }

        List<Secret> sp = R.shuffle(S.secrets);
        sp.sort((a, b) -> (b.exposed ? 1 : 0) - (a.exposed ? 1 : 0));
        if (sp.size() > 6) sp = new ArrayList<>(sp.subList(0, 6));
        int ns = 0;
        for (Secret s : sp) {
            if (ns >= 3) break;
            if (s.kind.equals("EXISTENCE") && !s.exposed) continue;
            God teller = null;
            String src;
            List<God> aliveK = new ArrayList<>();
            for (int k : s.keepers) if (S.god(k).alive) aliveK.add(S.god(k));
            if (s.exposed && s.exposer != null) {
                teller = S.god(s.exposer);
                src = "exposed";
            } else if (!s.knowers.isEmpty()) {
                teller = S.god(R.pick(new ArrayList<>(s.knowers)));
                src = "knower";
            } else {
                God mad = null;
                for (God g : aliveK) if (g.has("mad")) { mad = g; break; }
                if (mad != null) {
                    teller = mad;
                    src = "slip";
                } else if (R.chance(.5)) src = "glimpse";
                else continue;
            }
            ns++;
            Fragment f = new Fragment();
            f.source = "SECRET";
            f.secret = s;
            f.secretSource = src;
            f.teller = teller;
            f.title = SECRET_TITLE.getOrDefault(s.kind, "A Hidden Truth");
            f.text = s.text;
            f.reveals = "SECRET";
            f.att = teller != null ? (src.equals("slip") ? teller.name + " let this slip, and did not seem to know it."
                    : "So " + teller.name + " tells it.") : "Glimpsed, unbidden, in the Altus.";
            frags.add(f);
        }

        frags.addAll(staticFragments(S, seed, R, hiddenEx));
        for (Fragment f : frags) f.no = 1000 + R.nextInt(0, 8999);
        return frags;
    }

    static boolean hidden(History S, HistoryEvent e, Set<Integer> hiddenEx) {
        for (int x : e.actors) if (hiddenEx.contains(x)) return true;
        for (int x : e.targets) if (hiddenEx.contains(x)) return true;
        if (e.secretRef == null) return false;
        Secret s = S.secret(e.secretRef);
        return s != null && !s.exposed;
    }

    private static Fragment eventFragment(HistoryEvent ev, God teller, String reveals, Lie lie, History S, Rng R) {
        Fragment f = new Fragment();
        f.source = "EVENT";
        f.ev = ev;
        f.teller = teller;
        f.reveals = reveals;
        f.lie = lie;
        f.title = Text.eventTitle(ev, S);
        f.text = Text.renderEvent(ev, reveals, lie, S);
        if (lie != null && R.chance(.6)) f.tell = firstTell(teller);
        f.att = teller.alive ? "So " + teller.name + " tells it." : "So " + teller.name + ", who is no more, once told it.";
        List<Integer> claimed = Text.distinct(Text.claimedParticipants(ev, lie));
        double comp = COMPLEX.getOrDefault(ev.type, 0.0);
        if (claimed.size() >= 2 && R.chance(Math.min(.8, .04 + .1 * claimed.size() + comp))) {
            double score = claimed.size() + comp * 10;
            int tier = score >= 6 ? 2 : score >= 4 ? 1 : 0;
            String effect = R.pick(EFFECTS.get(tier));
            boolean anyDead = false;
            for (int id : claimed) if (!S.god(id).alive) anyDead = true;
            if (ev.type.equals("deicide") && anyDead) effect = "Call an echo of " + S.name(ev.targets.get(0)) + " (very dangerous)";
            List<Offering> offerings = new ArrayList<>();
            for (int id : claimed) {
                God g = S.god(id);
                Item b = null;
                for (Item l : g.likes) if (l.cat.equals("block")) { b = l; break; }
                if (b == null) b = g.likes.get(0);
                offerings.add(new Offering(g.name, b.label));
            }
            String truth = sortedKey(Text.distinct(Text.claimedParticipants(ev, null)));
            String said = sortedKey(claimed);
            f.ritual = new Ritual(tier + 1, effect, offerings, lie != null && !truth.equals(said));
        }
        return f;
    }

    private static String sortedKey(List<Integer> ids) {
        List<String> s = new ArrayList<>();
        for (int i : ids) s.add(Integer.toString(i));
        s.sort(null);
        return String.join(",", s);
    }

    // ------------------------------------------------------------------ static knowledge

    private static final class TellerCand {
        final God g;
        final double w;

        TellerCand(God g, double w) {
            this.g = g;
            this.w = w;
        }
    }

    private static God chooseTeller(History S, Rng R, Set<Integer> hiddenEx, List<Integer> subjects, List<Integer> prefer) {
        List<TellerCand> cand = new ArrayList<>();
        for (God g : S.gods) {
            if (hiddenEx.contains(g.id)) continue;
            double w = .5;
            if (subjects.contains(g.id)) w = 3;
            else {
                boolean close = false;
                for (int s : subjects) if (S.getOp(g.id, s) >= 3) { close = true; break; }
                if (close) w = 1.5;
            }
            if (prefer != null && prefer.contains(g.id)) w += 2;
            if (g.has("historian")) w += 1.5;
            if (g.has("secretive")) w *= .3;
            cand.add(new TellerCand(g, w));
        }
        TellerCand c = R.weighted(cand, x -> x.w);
        return c != null ? c.g : null;
    }

    private static final class Kind {
        final String k;
        final double w;

        Kind(String k, double w) {
            this.k = k;
            this.w = w;
        }
    }

    private static final List<Kind> KINDS = List.of(new Kind("LIKES", 2), new Kind("DISLIKES", 2), new Kind("OPINION", 3),
            new Kind("GRUDGE", 3), new Kind("NATURE", 1.5), new Kind("GROUP", 2));

    private static final class Pair {
        final God a, b;
        final long o;

        Pair(God a, God b, long o) {
            this.a = a;
            this.b = b;
            this.o = o;
        }
    }

    private static List<Fragment> staticFragments(History S, String seed, Rng R, Set<Integer> hiddenEx) {
        List<Fragment> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int[] slc = {0};
        List<Item> pool = new ArrayList<>(Content.BLOCKS);
        pool.addAll(Content.ENTITIES);
        List<God> visible = new ArrayList<>();
        for (God g : S.gods) if (!hiddenEx.contains(g.id)) visible.add(g);

        int tries = 0;
        while (out.size() < 6 && tries < 120) {
            tries++;
            String k = R.weighted(KINDS, x -> x.w).k;
            if (k.equals("LIKES") || k.equals("DISLIKES")) {
                boolean dis = k.equals("DISLIKES");
                if (visible.isEmpty()) continue;
                God x = R.pick(visible);
                God teller = chooseTeller(S, R, hiddenEx, List.of(x.id), null);
                if (teller == null) continue;
                List<Item> src = R.shuffle(dis ? x.dislikes : x.likes);
                int n = Math.min(src.size(), R.nextInt(2, 3));
                List<Item> shown = new ArrayList<>(src.subList(0, n));
                StaticLie lie = null;
                if (teller.has("obfuscator") && R.chance(.6)) {
                    Rng lr = new Rng(seed, "sl|" + teller.id + "|" + x.id + "|" + k);
                    Item other = null;
                    for (Item it : lr.shuffle(pool))
                        if (!Likes.contains(x.likes, it) && !Likes.contains(x.dislikes, it)) { other = it; break; }
                    if (other != null) {
                        shown.set(lr.nextInt(0, shown.size() - 1), other);
                        lie = new StaticLie("SL-" + Text.pad(++slc[0], 3), "SWAP_ITEM",
                                Text.poss(x.name) + " own deeds, and any other account of what it " + (dis ? "despises" : "loves"), teller.id);
                    }
                }
                List<String> ph = new ArrayList<>();
                for (Item it : shown) ph.add(Text.itemPhrase(it));
                String items = Text.listPhrase(ph);
                push(out, used, R, k, k + "|" + x.id, dis ? "The Hatreds of " + Text.lcT(x.name) : "The Loves of " + Text.lcT(x.name),
                        dis ? x.name + " cannot abide " + items + "." : x.name + " delights in " + items + ".", teller, lie);
            } else if (k.equals("OPINION")) {
                List<Pair> prs = new ArrayList<>();
                for (God a : S.gods)
                    for (God b : S.gods) {
                        if (a == b || !a.alive || !b.alive || hiddenEx.contains(a.id) || hiddenEx.contains(b.id)) continue;
                        long o = Math.round(S.getOp(a.id, b.id));
                        if (Math.abs(o) >= 3) prs.add(new Pair(a, b, o));
                    }
                if (prs.isEmpty()) continue;
                Pair c = R.weighted(prs, p -> Math.abs(p.o));
                God teller = chooseTeller(S, R, hiddenEx, List.of(c.a.id), List.of(c.a.id));
                if (teller == null) continue;
                long o = c.o;
                StaticLie lie = null;
                if (teller.has("obfuscator") && R.chance(.6)) {
                    o = -o;
                    lie = new StaticLie("SL-" + Text.pad(++slc[0], 3), "INVERT_FEELING", Text.poss(c.a.name) + " own deeds toward " + c.b.name,
                            teller.id);
                }
                String verb = o >= 5 ? "holds" : o >= 3 ? "thinks well of" : o <= -5 ? "despises" : "distrusts";
                push(out, used, R, k, k + "|" + c.a.id + "|" + c.b.id, "What " + Text.lcT(c.a.name) + " Thinks of " + Text.lcT(c.b.name),
                        o >= 5 ? c.a.name + " holds " + c.b.name + " dear." : c.a.name + " " + verb + " " + c.b.name + ".", teller, lie);
            } else if (k.equals("GRUDGE")) {
                List<Grudge> gs = new ArrayList<>();
                for (Grudge g : S.grudges) {
                    if (g.resolved != null || !S.god(g.holder).alive || hiddenEx.contains(g.holder)) continue;
                    boolean ok;
                    if (g.tt.equals("god")) ok = S.god(g.target).alive && !hiddenEx.contains(g.target);
                    else {
                        Group q = S.groups.get(g.target);
                        if (q.dissolved) ok = false;
                        else if (!q.secret) ok = true;
                        else ok = purposeExposed(S, q);
                    }
                    if (ok) gs.add(g);
                }
                if (gs.isEmpty()) continue;
                Grudge gd = R.pick(gs);
                God holder = S.god(gd.holder);
                God teller = chooseTeller(S, R, hiddenEx, List.of(holder.id), List.of(holder.id));
                if (teller == null) continue;
                String tName = gd.tt.equals("god") ? S.name(gd.target) : S.groups.get(gd.target).name;
                StaticLie lie = null;
                String why = "";
                if (gd.items != null && !gd.items.isEmpty()) {
                    List<String> ph = new ArrayList<>();
                    for (Item it : gd.items) ph.add(Text.itemPhrase(it));
                    why = ", for " + tName + " likes " + Text.listPhrase(ph) + ", which " + holder.name + " despises";
                } else if (gd.cause != null) {
                    HistoryEvent e = S.event(gd.cause);
                    if (e != null) why = ", born of the events of the " + Text.ord(e.year) + " year";
                }
                if (teller.has("obfuscator") && gd.tt.equals("god") && R.chance(.6)) {
                    List<God> others = new ArrayList<>();
                    for (God g : visible) if (g.id != holder.id && g.id != gd.target) others.add(g);
                    if (!others.isEmpty()) {
                        God z = new Rng(seed, "sl|" + teller.id + "|" + gd.id).pick(others);
                        tName = z.name;
                        why = "";
                        lie = new StaticLie("SL-" + Text.pad(++slc[0], 3), "SWAP_TARGET",
                                holder.name + " and " + S.name(gd.target) + " both know whom the grudge is truly against", teller.id);
                    }
                }
                String adj = gd.sev >= 8 ? "bitter" : gd.sev >= 5 ? "deep" : "lingering";
                push(out, used, R, k, k + "|" + gd.id, "The Grudge of " + Text.lcT(holder.name),
                        holder.name + " holds a " + adj + " grudge against " + tName + why + ".", teller, lie);
            } else if (k.equals("NATURE")) {
                if (visible.isEmpty()) continue;
                God x = R.pick(visible);
                God teller = chooseTeller(S, R, hiddenEx, List.of(x.id), null);
                if (teller == null) continue;
                List<String> ts = new ArrayList<>(x.traits.keySet());
                StaticLie lie = null;
                if (teller.has("obfuscator") && R.chance(.6)) {
                    Rng lr = new Rng(seed, "sl|" + teller.id + "|" + x.id + "|N");
                    String fake = null;
                    for (String t : lr.shuffle(Content.TRAIT_NAMES)) {
                        if (x.traits.containsKey(t)) continue;
                        boolean clash = false;
                        for (String[] pr : Content.EXCL) {
                            boolean hasT = pr[0].equals(t) || pr[1].equals(t);
                            if (!hasT) continue;
                            for (String u : ts) if (pr[0].equals(u) || pr[1].equals(u)) clash = true;
                        }
                        if (!clash) { fake = t; break; }
                    }
                    if (fake != null) {
                        ts.set(lr.nextInt(0, ts.size() - 1), fake);
                        lie = new StaticLie("SL-" + Text.pad(++slc[0], 3), "SWAP_TRAIT", Text.poss(x.name) + " own deeds", teller.id);
                    }
                }
                List<String> ph = new ArrayList<>();
                for (String t : ts) ph.add(Content.TRAIT_ADJ.getOrDefault(t, t));
                push(out, used, R, k, k + "|" + x.id, "The Nature of " + Text.lcT(x.name),
                        x.name + " is said to be " + Text.listPhrase(ph) + ".", teller, lie);
            } else {
                List<Group> gs = new ArrayList<>();
                for (Group q : S.groups) {
                    if (q.dissolved || q.members.size() < 2) continue;
                    boolean allVis = true;
                    for (int m : q.members) if (hiddenEx.contains(m)) allVis = false;
                    if (!allVis) continue;
                    if (!q.secret || purposeExposed(S, q)) gs.add(q);
                }
                if (gs.isEmpty()) continue;
                Group q = R.pick(gs);
                God teller = chooseTeller(S, R, hiddenEx, q.members, q.members);
                if (teller == null) continue;
                List<Integer> ms = new ArrayList<>(q.members);
                StaticLie lie = null;
                if (teller.has("obfuscator") && !q.members.contains(teller.id) && R.chance(.6)) {
                    List<God> others = new ArrayList<>();
                    for (God g : visible) if (!q.members.contains(g.id)) others.add(g);
                    if (!others.isEmpty()) {
                        Rng lr = new Rng(seed, "sl|" + teller.id + "|" + q.id);
                        int at = lr.nextInt(0, ms.size() - 1);
                        ms.set(at, lr.pick(others).id);
                        lie = new StaticLie("SL-" + Text.pad(++slc[0], 3), "SWAP_MEMBER", "the group's own members know who sits among them",
                                teller.id);
                    }
                }
                String ldr = q.leader != null && q.members.contains(q.leader) ? S.name(q.leader) + " leads it." : "It has no leader.";
                List<String> names = new ArrayList<>();
                for (int m : ms) names.add(S.name(m));
                push(out, used, R, k, k + "|" + q.id, "On " + Text.lcT(q.name), q.name + " is sworn " + Text.principlePhrase(q.principle, S)
                        + ". " + ldr + " Its members are " + Text.listPhrase(names) + ".", teller, lie);
            }
        }
        return out;
    }

    private static boolean purposeExposed(History S, Group q) {
        for (Secret s : S.secrets) if (s.kind.equals("GROUP_PURPOSE") && s.group != null && s.group == q.id) return s.exposed;
        return false;
    }

    private static void push(List<Fragment> out, Set<String> used, Rng R, String kind, String key, String title, String text,
            God teller, StaticLie lie) {
        if (used.contains(key)) return;
        used.add(key);
        Fragment f = new Fragment();
        f.source = "STATIC";
        f.kind = kind;
        f.title = title;
        f.text = Text.cap(text);
        f.teller = teller;
        f.staticLie = lie;
        f.reveals = kind;
        f.att = teller.alive ? "So " + teller.name + " tells it." : "So " + teller.name + ", who is no more, once told it.";
        if (lie != null && R.chance(.6)) f.tell = firstTell(teller);
        out.add(f);
    }
}
