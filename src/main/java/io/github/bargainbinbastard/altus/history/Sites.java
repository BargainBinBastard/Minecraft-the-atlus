package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.History.God;

/**
 * The fixed places on the Mountain where a god's door can stand, and which god holds each.
 * The places never change between worlds; the history decides who sits where.
 */
public final class Sites {
    public enum Region { SUMMIT, SLOPES, HOUSE }

    public static final class Site {
        public final String id;
        /** How lore names the place: "the door ... lies at " + label. */
        public final String label;
        public final Region region;
        public final int x, z;
        /** Index into the god list, or -1 if empty. */
        public int occupant = -1;

        Site(String id, String label, Region region, int x, int z) {
            this.id = id;
            this.label = label;
            this.region = region;
            this.x = x;
            this.z = z;
        }

        public boolean ruined(History h) {
            return occupant >= 0 && !h.god(occupant).alive;
        }
    }

    /** The House stands on the southern slope; its three doors are inside. */
    public static final int HOUSE_X = 0, HOUSE_Z = 118;

    private static Site polar(String id, String label, Region r, double deg, double dist) {
        double a = Math.toRadians(deg);
        return new Site(id, label, r, (int) Math.round(Math.cos(a) * dist), (int) Math.round(Math.sin(a) * dist));
    }

    /** The layout, in the order seats are handed out: most powerful first. */
    static List<Site> layout() {
        List<Site> l = new ArrayList<>();
        l.add(polar("summit_south", "the southern stone of the Mountain's summit", Region.SUMMIT, 60, 9));
        l.add(polar("summit_north", "the northern stone of the Mountain's summit", Region.SUMMIT, 240, 9));
        l.add(polar("eagles_ledge", "Eagle's Ledge, high on the Mountain's north-eastern face", Region.SLOPES, 315, 55));
        l.add(polar("east_cave", "the cave on the Mountain's eastern face", Region.SLOPES, 0, 78));
        l.add(polar("north_cairn", "the cairn on the Mountain's northern slope", Region.SLOPES, 270, 82));
        l.add(polar("west_terrace", "the terrace on the Mountain's western side", Region.SLOPES, 180, 80));
        l.add(polar("weeping_wall", "the Weeping Wall, on the Mountain's north-west", Region.SLOPES, 225, 95));
        l.add(polar("south_stair", "the South Stair, on the Mountain's south-eastern flank", Region.SLOPES, 45, 100));
        l.add(new Site("attic", "the House's locked attic", Region.HOUSE, HOUSE_X + 4, HOUSE_Z - 3));
        l.add(new Site("hearth", "the hearth of the House", Region.HOUSE, HOUSE_X, HOUSE_Z - 3));
        l.add(new Site("cellar", "the House's cellar stair", Region.HOUSE, HOUSE_X - 4, HOUSE_Z - 3));
        return l;
    }

    public final List<Site> all;
    private final Map<Integer, Site> byGod = new HashMap<>();

    private Sites(List<Site> all) {
        this.all = Collections.unmodifiableList(all);
        for (Site s : all) if (s.occupant >= 0) byGod.put(s.occupant, s);
    }

    /**
     * Living gods take seats by power, strongest highest. Dead gods take whatever is left, as ruins.
     * Gods beyond the eleven seats have no door.
     */
    public static Sites assign(History h) {
        List<Site> l = layout();
        List<God> living = new ArrayList<>(), dead = new ArrayList<>();
        for (God g : h.gods) (g.alive ? living : dead).add(g);
        living.sort((a, b) -> a.power != b.power ? Integer.compare(b.power, a.power) : Integer.compare(a.id, b.id));
        dead.sort((a, b) -> Integer.compare(a.id, b.id));
        int i = 0;
        for (God g : living) if (i < l.size()) l.get(i++).occupant = g.id;
        for (God g : dead) if (i < l.size()) l.get(i++).occupant = g.id;
        return new Sites(l);
    }

    public Site of(int god) {
        return byGod.get(god);
    }

    public Site byId(String id) {
        for (Site s : all) if (s.id.equals(id)) return s;
        return null;
    }
}
