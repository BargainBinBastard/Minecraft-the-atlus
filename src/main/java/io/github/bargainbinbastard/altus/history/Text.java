package io.github.bargainbinbastard.altus.history;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.bargainbinbastard.altus.history.History.Claimed;
import io.github.bargainbinbastard.altus.history.History.HistoryEvent;
import io.github.bargainbinbastard.altus.history.History.Lie;
import io.github.bargainbinbastard.altus.history.History.Principle;

/** Turns history records into prose. Mirrors the prototype's templates exactly. */
public final class Text {
    private Text() {}

    private static final String[] ORD = {"th", "st", "nd", "rd"};

    public static String ord(int n) {
        int v = n % 100;
        int i = (v - 20) % 10;
        String s = null;
        if (i >= 0 && i < 4) s = ORD[i];
        if (s == null && v >= 0 && v < 4) s = ORD[v];
        if (s == null) s = ORD[0];
        return n + s;
    }

    public static String year(int y) {
        return "Y" + pad(y, 3);
    }

    public static String pad(int n, int width) {
        String s = Integer.toString(n);
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < width; i++) b.append('0');
        return b.append(s).toString();
    }

    public static String cap(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static String lc(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    public static String poss(String s) {
        return s.endsWith("s") ? s + "'" : s + "'s";
    }

    /** "the X" inside a title. */
    public static String lcT(String s) {
        return s.startsWith("The ") ? "the " + s.substring(4) : s;
    }

    public static String listPhrase(List<String> a) {
        if (a.size() <= 1) return String.join("", a);
        if (a.size() == 2) return a.get(0) + " and " + a.get(1);
        return String.join(", ", a.subList(0, a.size() - 1)) + ", and " + a.get(a.size() - 1);
    }

    public static String itemPhrase(Item l) {
        if (l.cat.equals("action")) return Content.ACTION_NAMES.getOrDefault(l.id, l.id);
        return l.label;
    }

    public static String actPhrase(Item item) {
        if (item.cat.equals("block")) return "raised a shrine of " + item.label;
        if (item.cat.equals("entity")) return "loosed " + item.label + " upon the world";
        return item.verb;
    }

    /** JavaScript Number.prototype.toFixed for non-negative values. */
    public static String toFixed(double x, int digits) {
        return new BigDecimal(x).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }

    /** Print a score the way the prototype does: integers without a decimal point. */
    public static String num(double d) {
        if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
        if (d == Math.rint(d)) return Long.toString((long) d);
        return Double.toString(d);
    }

    public static String principlePhrase(Principle p, History S) {
        switch (p.kind) {
            case "OPPOSE": return "to oppose " + S.name(p.target);
            case "KILL": return "to kill " + S.name(p.target);
            case "AVENGE": return p.known ? "to avenge " + S.name(p.dead) + " upon " + S.names(p.killers)
                    : "to find and punish the slayers of " + S.name(p.dead);
            case "GUARD": return "to guard a hidden truth";
            case "PROTECT": return "to protect " + S.name(p.protect);
            case "RECORD": return "to keep the true record of all that happens";
            case "VEIL": return "to veil the Altus from mortal eyes";
            default: return "";
        }
    }

    public static String renderEvent(HistoryEvent ev, String reveals, Lie lie, History S) {
        return cap(render0(ev, reveals, lie, S));
    }

    private static String render0(HistoryEvent ev, String reveals, Lie lie, History S) {
        Claimed c = lie != null ? lie.claimed : Claimed.none();
        int yr = (lie != null && c.year != null && c.year != 0) ? c.year : ev.year;
        String when = "WHEN".equals(reveals) ? "In the " + ord(yr) + " year, " : "";
        List<Integer> act = c.hasActor ? (c.actor == null ? null : List.of(c.actor)) : ev.actors;
        String A = act == null ? "an unseen hand" : S.names(act);
        switch (ev.type) {
            case "glory_birth":
                return w(when, S.name(ev.actors.get(0)) + " came from glory, and made the world.");
            case "creation": {
                String why;
                String r = ev.data.reason == null ? "" : ev.data.reason;
                switch (r) {
                    case "progenitor": why = "in the first days"; break;
                    case "champion": why = "to be its champion against "
                            + (ev.data.target != null ? S.name(ev.data.target) : "its enemies"); break;
                    case "companion": why = "so that it would not be alone"; break;
                    case "keeper": why = "to keep what it had hidden"; break;
                    case "servant": why = "to serve its ambitions"; break;
                    default: why = "";
                }
                return w(when, S.name(ev.actors.get(0)) + " made " + S.name(ev.targets.get(0)) + " " + why + ".");
            }
            case "corpse_birth":
                return w(when, S.name(ev.actors.get(0)) + " rose from the corpse of " + S.name(ev.targets.get(0)) + ".");
            case "nowhere_birth":
                return w(when, S.name(ev.actors.get(0)) + " came from nowhere, and none know why.");
            case "offense": {
                String V = S.name(ev.targets.get(0)), ap = actPhrase(ev.data.item);
                if ("WHY".equals(reveals)) return cap(A) + " " + ap + ", though " + V + " despises it. Why? "
                        + cap(c.cause != null ? c.cause : A + " cared nothing for what " + V + " holds sacred") + ".";
                if ("WHO".equals(reveals) && act != null) return "It was " + A + " who " + ap + ", a thing " + V + " despises.";
                return w(when, A + " " + ap + ", a thing " + V + " despises.");
            }
            case "strike": {
                String Tn = S.name(ev.targets.get(0));
                String gt = (ev.data.group != null && !c.hasActor) ? ", acting as " + S.groups.get(ev.data.group).name + "," : "";
                boolean ok = Boolean.TRUE.equals(ev.data.success);
                if ("WHY".equals(reveals) && c.cause != null) return cap(A) + " struck " + Tn + ", because " + c.cause + ".";
                if ("WHO".equals(reveals) && act != null) return "It was " + A + gt + " who " + (ok ? "wounded" : "struck at") + " " + Tn + ".";
                return w(when, A + gt + " " + (ok ? "struck " + Tn + " and wounded them" : "struck at " + Tn + " and failed") + ".");
            }
            case "kill_attempt":
                return w(when, A + " tried to kill " + S.name(ev.targets.get(0)) + ", and failed.");
            case "deicide": {
                List<Integer> k = act;
                if (k != null && lie != null && "OMIT_ALLIES".equals(lie.distortion)) k = List.of(ev.actors.get(0));
                String who = k != null ? S.names(k) : "an unseen hand", Tn = S.name(ev.targets.get(0));
                if ("OUTCOME".equals(reveals)) return "After " + who + " struck, " + Tn + " was no more, and all that " + Tn + " loved went unclaimed.";
                return w(when, who + " slew " + Tn + ", and " + Tn + " is no more.");
            }
            case "war_start": {
                if ("WHY".equals(reveals)) {
                    List<Integer> all = new ArrayList<>(ev.actors);
                    all.addAll(ev.targets);
                    List<Integer> rest = all.size() > 2 ? all.subList(2, all.size()) : List.of();
                    String others = S.names(rest);
                    if (others.isEmpty()) others = "none";
                    return (S.name(ev.actors.get(0)) + " made war on " + S.name(ev.targets.get(0)) + " out of old hatred, and "
                            + others + " took sides.").replace(", and none took sides", "");
                }
                return w(when, "war came: " + S.names(ev.actors) + " against " + S.names(ev.targets) + ".");
            }
            case "war_end": {
                List<Integer> all = new ArrayList<>(ev.actors);
                all.addAll(ev.targets);
                if (lie != null && "INVERT_OUTCOME".equals(lie.distortion) && "stalemate".equals(c.outcome))
                    return w(when, "the war of " + S.names(all) + " ended with neither side holding the field.");
                if (lie != null && "INVERT_OUTCOME".equals(lie.distortion) && "victory".equals(c.outcome)) {
                    List<Integer> other = ev.actors.contains(c.winner) ? ev.targets : ev.actors;
                    return w(when, S.name(c.winner) + " won the war against " + S.names(other) + ".");
                }
                if (lie != null && "OMIT_ALLIES".equals(lie.distortion))
                    return w(when, S.name(ev.actors.get(0)) + " alone broke " + S.names(ev.targets) + ".");
                if ("stalemate".equals(ev.data.outcome))
                    return w(when, "the war of " + S.names(all) + " ended with neither side holding the field.");
                return w(when, S.names(ev.actors) + " broke " + S.names(ev.targets)
                        + (Boolean.TRUE.equals(ev.data.decisive) ? " utterly" : "") + ".");
            }
            case "forgive":
                return w(when, S.name(ev.actors.get(0)) + " forgave " + S.name(ev.targets.get(0)) + ".");
            case "seclusion":
                return "WHEN".equals(reveals)
                        ? "In the " + ord(yr) + " year, " + S.name(ev.actors.get(0)) + " withdrew " + ev.data.place
                                + " and was not seen for " + ev.data.dur + " years."
                        : S.name(ev.actors.get(0)) + " withdrew " + ev.data.place + ".";
            case "accusation":
                return w(when, S.name(ev.actors.get(0)) + " accused " + S.name(ev.targets.get(0)) + " of plotting against it.");
            case "group_found": {
                History.Group gr = S.groups.get(ev.data.group);
                if ("WHY".equals(reveals)) return S.names(ev.actors) + " founded " + gr.name + ", sworn " + principlePhrase(gr.principle, S) + ".";
                return w(when, S.names(ev.actors) + " founded " + gr.name
                        + (ev.data.leader != null ? ", with " + S.name(ev.data.leader) + " at its head" : ", and named no leader") + ".");
            }
            case "group_join":
                return w(when, S.name(ev.actors.get(0)) + " joined " + S.groups.get(ev.data.group).name + ".");
            case "group_expel":
                return w(when, S.groups.get(ev.data.group).name + " cast out " + S.name(ev.targets.get(0)) + ".");
            case "defect":
                return w(when, S.name(ev.actors.get(0)) + " walked away from " + S.groups.get(ev.data.group).name + ".");
            case "splinter":
                return w(when, S.names(ev.actors) + " broke from " + S.groups.get(ev.data.group).name
                        + (ev.data.newGroup != null ? " and founded " + S.groups.get(ev.data.newGroup).name : "") + ".");
            case "betrayal":
                return w(when, S.name(ev.actors.get(0)) + " betrayed " + S.groups.get(ev.data.group).name
                        + (Boolean.TRUE.equals(ev.data.leaked) ? " and laid bare its secrets" : "") + ".");
            case "hall_built":
                return w(when, S.groups.get(ev.data.group).name + " raised a hall in the Altus.");
            case "exposure": {
                History.Secret s = S.secret(ev.data.secret);
                String who = ev.data.group != null ? S.groups.get(ev.data.group).name : S.names(ev.actors);
                return w(when, who + " uncovered a hidden thing: " + lc(s.text));
            }
            case "correction": {
                Lie l = S.lie(ev.data.lie);
                HistoryEvent e = S.event(l.eventId);
                String who = ev.data.group != null ? S.groups.get(ev.data.group).name : S.names(ev.actors);
                return w(when, who + " proved that " + poss(S.name(l.teller)) + " account of the " + ord(e.year) + " year was false.");
            }
            case "location":
                return "The door to " + poss(S.name(ev.actors.get(0))) + " sanctum lies "
                        + (c.place != null ? c.place : ev.data.place) + ".";
            default:
                return "";
        }
    }

    private static String w(String when, String s) {
        return when.isEmpty() ? cap(s) : when + s;
    }

    /** Gods a fragment names as taking part, under the teller's version. */
    public static List<Integer> claimedParticipants(HistoryEvent ev, Lie lie) {
        List<Integer> p = new ArrayList<>(ev.actors);
        if (!ev.type.equals("location")) p.addAll(ev.targets);
        if (lie == null) return p;
        Claimed c = lie.claimed;
        switch (lie.distortion) {
            case "SWAP_ACTOR": {
                List<Integer> q = new ArrayList<>();
                q.add(c.actor);
                q.addAll(ev.targets);
                return q;
            }
            case "OMIT_ACTOR": return new ArrayList<>(ev.targets);
            case "OMIT_ALLIES": {
                List<Integer> q = new ArrayList<>();
                q.add(ev.actors.get(0));
                q.addAll(ev.targets);
                return q;
            }
            default: return p;
        }
    }

    public static List<Integer> distinct(List<Integer> in) {
        return new ArrayList<>(new LinkedHashSet<>(in));
    }

    public static String eventTitle(HistoryEvent ev, History S) {
        switch (ev.type) {
            case "glory_birth": return "The Making of the World";
            case "creation": return "The Making of " + lcT(S.name(ev.targets.get(0)));
            case "corpse_birth": return "The Rising of " + lcT(S.name(ev.actors.get(0)));
            case "nowhere_birth": return "The Coming of " + lcT(S.name(ev.actors.get(0)));
            case "offense": return "An Offense Against " + lcT(S.name(ev.targets.get(0)));
            case "strike": return "A Blow Against " + lcT(S.name(ev.targets.get(0)));
            case "kill_attempt": return "An Attempt on the Life of " + lcT(S.name(ev.targets.get(0)));
            case "deicide": return "The Death of " + lcT(S.name(ev.targets.get(0)));
            case "war_start": return "The War of " + lcT(S.name(ev.actors.get(0))) + " and " + lcT(S.name(ev.targets.get(0)));
            case "war_end": return "The End of the War of " + lcT(S.name(ev.actors.get(0))) + " and " + lcT(S.name(ev.targets.get(0)));
            case "forgive": return "The Forgiveness of " + lcT(S.name(ev.targets.get(0)));
            case "seclusion": return "The Withdrawal of " + lcT(S.name(ev.actors.get(0)));
            case "accusation": return "An Accusation Against " + lcT(S.name(ev.targets.get(0)));
            case "group_found": return "The Founding of " + lcT(S.groups.get(ev.data.group).name);
            case "group_join": return "The Joining of " + lcT(S.groups.get(ev.data.group).name);
            case "group_expel": return "The Casting Out of " + lcT(S.name(ev.targets.get(0)));
            case "defect": return "A Departure from " + lcT(S.groups.get(ev.data.group).name);
            case "splinter": return "The Breaking of " + lcT(S.groups.get(ev.data.group).name);
            case "betrayal": return "The Betrayal of " + lcT(S.groups.get(ev.data.group).name);
            case "hall_built": return "The Hall of " + lcT(S.groups.get(ev.data.group).name);
            case "exposure": return "A Thing Laid Bare";
            case "correction": return "A Falsehood Undone";
            case "location": return "The Door to the Sanctum of " + lcT(S.name(ev.actors.get(0)));
            default: return "A Fragment of the Past";
        }
    }
}
