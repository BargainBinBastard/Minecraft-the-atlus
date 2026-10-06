package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The complete generated history of one world: gods, events, groups, grudges, secrets and lies,
 * plus the developer log. Produced by {@link HistorySimulator}.
 */
public final class History {
    public final String seed;
    public final int years;
    public final List<God> gods = new ArrayList<>();
    public final List<HistoryEvent> events = new ArrayList<>();
    public final List<Lie> lies = new ArrayList<>();
    public final List<LogLine> log = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<Grudge> grudges = new ArrayList<>();
    public final List<War> wars = new ArrayList<>();
    public final List<Secret> secrets = new ArrayList<>();
    /** Official stories: key is eventId + "|" + tellerId. */
    public final Map<String, Lie> story = new HashMap<>();
    public final List<Region> regions = new ArrayList<>();
    public final List<String> tensions = new ArrayList<>();
    public final List<HistoryEvent> deicides = new ArrayList<>();

    final Map<String, Double> opinions = new HashMap<>();
    final Map<String, HistoryEvent> eventById = new HashMap<>();
    final Map<String, Secret> secretById = new HashMap<>();
    final Map<String, Lie> lieById = new HashMap<>();

    History(String seed, int years) {
        this.seed = seed;
        this.years = years;
    }

    public God god(int id) {
        return gods.get(id);
    }

    public String name(int id) {
        return gods.get(id).name;
    }

    public String names(List<Integer> ids) {
        List<String> a = new ArrayList<>();
        for (int id : ids) a.add(name(id));
        if (a.size() <= 1) return String.join("", a);
        if (a.size() == 2) return a.get(0) + " and " + a.get(1);
        return String.join(", ", a.subList(0, a.size() - 1)) + ", and " + a.get(a.size() - 1);
    }

    public HistoryEvent event(String id) {
        return eventById.get(id);
    }

    public Secret secret(String id) {
        return secretById.get(id);
    }

    public Lie lie(String id) {
        return lieById.get(id);
    }

    public Lie storyOf(String eventId, int teller) {
        return story.get(eventId + "|" + teller);
    }

    /** Baseline opinion of a toward b, from shared and conflicting likes and kinship. */
    public double base(int a, int b) {
        God A = gods.get(a), B = gods.get(b);
        double v = 2 * Likes.shared(A, B).size() - 2.5 * Likes.conflicts(B, A).size();
        if ((A.creator != null && A.creator == b) || (B.creator != null && B.creator == a)) v += 3;
        if (A.sourceCorpse != null && A.sourceCorpse == b) v += 2;
        return clamp(Math.round(v), -8, 8);
    }

    public double getOp(int a, int b) {
        String k = a + "|" + b;
        Double v = opinions.get(k);
        if (v == null) {
            v = base(a, b);
            opinions.put(k, v);
        }
        return v;
    }

    void setOp(int a, int b, double v) {
        opinions.put(a + "|" + b, Math.max(-10, Math.min(10, v)));
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ model

    public static final class God {
        public final int id;
        public final String name;
        public final String origin;
        public final Integer creator;
        public final Integer sourceCorpse;
        public final LinkedHashMap<String, Integer> traits;
        public final List<Item> likes;
        public final List<Item> dislikes;
        public int power;
        public final int startPower;
        public final int born;
        public boolean alive = true;
        public Integer deathYear;
        public List<Integer> killers = new ArrayList<>();
        public int secludedUntil = -1;
        public final List<Seclusion> seclusions = new ArrayList<>();
        public int progenitor;
        Set<String> answered;

        God(int id, String name, String origin, Integer creator, Integer sourceCorpse,
                LinkedHashMap<String, Integer> traits, List<Item> likes, List<Item> dislikes, int power, int born) {
            this.id = id;
            this.name = name;
            this.origin = origin;
            this.creator = creator;
            this.sourceCorpse = sourceCorpse;
            this.traits = traits;
            this.likes = likes;
            this.dislikes = dislikes;
            this.power = power;
            this.startPower = power;
            this.born = born;
        }

        public int trait(String t) {
            Integer v = traits.get(t);
            return v == null ? 0 : v;
        }

        public boolean has(String t) {
            return trait(t) != 0;
        }
    }

    public static final class Seclusion {
        public final int start, end;
        public final String ev;

        Seclusion(int start, int end, String ev) {
            this.start = start;
            this.end = end;
            this.ev = ev;
        }
    }

    public static final class HistoryEvent {
        public final String id;
        public final String type;
        public final int year;
        public final List<Integer> actors;
        public final List<Integer> targets;
        public final EventData data;
        public String secretRef;

        HistoryEvent(String id, String type, int year, List<Integer> actors, List<Integer> targets, EventData data) {
            this.id = id;
            this.type = type;
            this.year = year;
            this.actors = actors;
            this.targets = targets;
            this.data = data == null ? new EventData() : data;
        }
    }

    /** Per-type event details. Unused fields stay null. */
    public static final class EventData {
        public Item item;
        public Integer target;
        public String reason;
        public String place;
        public Integer dur;
        public Integer group;
        public Integer leader;
        public Boolean success;
        public String outcome;
        public String war;
        public Boolean decisive;
        public List<Integer> killers;
        public Integer victimPower;
        public String secret;
        public String lie;
        public String grudge;
        public Integer newGroup;
        public Boolean leaked;
        public String region;
    }

    public static final class Grudge {
        public final String id;
        public final int holder;
        /** "god" or "group". */
        public final String tt;
        public final int target;
        public int sev;
        public final String cause;
        public final int year;
        public String resolved;
        public final Set<Integer> forgiven = new HashSet<>();
        public String reason;
        public List<Item> items;

        Grudge(String id, int holder, String tt, int target, int sev, String cause, int year) {
            this.id = id;
            this.holder = holder;
            this.tt = tt;
            this.target = target;
            this.sev = sev;
            this.cause = cause;
            this.year = year;
        }
    }

    public static final class Principle {
        public final String kind;
        public Integer target;
        public Integer dead;
        public List<Integer> killers;
        public boolean known;
        public String secretId;
        public Integer protect;

        public Principle(String kind) {
            this.kind = kind;
        }

        Principle copy() {
            Principle p = new Principle(kind);
            p.target = target;
            p.dead = dead;
            p.killers = killers;
            p.known = known;
            p.secretId = secretId;
            p.protect = protect;
            return p;
        }
    }

    public static final class Group {
        public final int id;
        public final String name;
        public final Principle principle;
        public final boolean secret;
        public Integer leader;
        public List<Integer> members;
        public final int founded;
        public boolean dissolved;
        public String dissolvedWhy;
        public Integer dissolvedYear;
        public boolean hall;
        public final Map<Integer, Integer> loyalty = new HashMap<>();
        public Integer parent;

        Group(int id, String name, Principle principle, boolean secret, List<Integer> members, int founded) {
            this.id = id;
            this.name = name;
            this.principle = principle;
            this.secret = secret;
            this.members = members;
            this.founded = founded;
        }
    }

    public static final class Secret {
        public final String id;
        public final String kind;
        public final int about;
        public final String text;
        public final LinkedHashSet<Integer> keepers;
        public final LinkedHashSet<Integer> knowers;
        public final int importance;
        public boolean exposed;
        public Integer exposer;
        public final String eventRef;
        public final Integer group;
        public final LinkedHashSet<Integer> threats = new LinkedHashSet<>();
        public final int year;

        Secret(String id, String kind, int about, String text, LinkedHashSet<Integer> keepers,
                LinkedHashSet<Integer> knowers, int importance, String eventRef, Integer group, int year) {
            this.id = id;
            this.kind = kind;
            this.about = about;
            this.text = text;
            this.keepers = keepers;
            this.knowers = knowers;
            this.importance = importance;
            this.eventRef = eventRef;
            this.group = group;
            this.year = year;
        }
    }

    /** What a lie claims instead of the truth. */
    public static final class Claimed {
        public boolean hasActor;
        public Integer actor;
        public String cause;
        public String outcome;
        public Integer winner;
        public Integer year;
        public String place;

        public static Claimed actor(Integer actor) {
            Claimed c = new Claimed();
            c.hasActor = true;
            c.actor = actor;
            return c;
        }

        public static Claimed cause(String cause) {
            Claimed c = new Claimed();
            c.cause = cause;
            return c;
        }

        public static Claimed outcome(String outcome, Integer winner) {
            Claimed c = new Claimed();
            c.outcome = outcome;
            c.winner = winner;
            return c;
        }

        public static Claimed year(int year) {
            Claimed c = new Claimed();
            c.year = year;
            return c;
        }

        public static Claimed place(String place) {
            Claimed c = new Claimed();
            c.place = place;
            return c;
        }

        public static Claimed none() {
            return new Claimed();
        }
    }

    public static final class Lie {
        public final String id;
        public final String eventId;
        public final int teller;
        public final List<Integer> tellers;
        public final String motive;
        public final String distortion;
        public final Claimed claimed;
        public final Integer targetGod;
        public final String contra;
        public final Integer coordinated;
        public boolean exposed;
        public final int year;

        Lie(String id, String eventId, List<Integer> tellers, String motive, String distortion, Claimed claimed,
                Integer targetGod, String contra, Integer coordinated, int year) {
            this.id = id;
            this.eventId = eventId;
            this.teller = tellers.get(0);
            this.tellers = tellers;
            this.motive = motive;
            this.distortion = distortion;
            this.claimed = claimed;
            this.targetGod = targetGod;
            this.contra = contra;
            this.coordinated = coordinated;
            this.year = year;
        }
    }

    public static final class War {
        public final String eventId;
        public final List<Integer> sideA;
        public final List<Integer> sideB;
        public final int start;
        public int endYear;
        public boolean over;

        War(String eventId, List<Integer> sideA, List<Integer> sideB, int start, int endYear) {
            this.eventId = eventId;
            this.sideA = sideA;
            this.sideB = sideB;
            this.start = start;
            this.endYear = endYear;
        }
    }

    public static final class LogLine {
        public final int year;
        public final String tag;
        public final String text;
        public final String cls;
        public final String cat;
        public final int indent;

        LogLine(int year, String tag, String text, String cls, String cat, int indent) {
            this.year = year;
            this.tag = tag;
            this.text = text;
            this.cls = cls;
            this.cat = cat;
            this.indent = indent;
        }
    }

    public static final class Region {
        public final String name;
        public final String status;

        Region(String name, String status) {
            this.name = name;
            this.status = status;
        }
    }
}
