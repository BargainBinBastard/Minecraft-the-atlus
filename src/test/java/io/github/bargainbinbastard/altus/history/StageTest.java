package io.github.bargainbinbastard.altus.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class StageTest {

    @Test
    void terrainIsTheSameEverywhereAndHasAMountain() {
        assertEquals(Terrain.BASE, Terrain.height(Terrain.CLEARING_X, Terrain.CLEARING_Z), "the Clearing is flat ground");
        assertTrue(Terrain.height(0, 0) > 190, "the summit towers: " + Terrain.height(0, 0));
        assertTrue(Math.abs(Terrain.height(600, 600) - Terrain.BASE) <= 2, "the far Woods are nearly flat");
        assertEquals(-1, Terrain.height(Terrain.EDGE + 5, 0), "past the edge there is nothing");
        // Walkable: neighbouring columns never differ by a cliff on the way from the Clearing to the House.
        for (int z = Terrain.CLEARING_Z; z > Sites.HOUSE_Z; z--)
            assertTrue(Math.abs(Terrain.height(0, z) - Terrain.height(0, z - 1)) <= 2, "steep step at z=" + z);
    }

    @Test
    void seatsGoToTheStrongestAndEveryDoorIsWhereLoreSays() {
        for (int i = 0; i < 80; i++) {
            History h = HistorySimulator.simulate("stage-" + i);
            Sites sites = Sites.assign(h);
            assertEquals(11, sites.all.size());
            Set<Integer> seated = new HashSet<>();
            for (Sites.Site s : sites.all) if (s.occupant >= 0) assertTrue(seated.add(s.occupant), "a god holds two seats");
            int living = (int) h.gods.stream().filter(g -> g.alive).count();
            assertEquals(Math.min(living, 11), sites.all.stream().filter(s -> s.occupant >= 0 && h.god(s.occupant).alive).count());
            Sites.Site top = sites.all.get(0);
            if (top.occupant >= 0)
                for (History.God g : h.gods) if (g.alive) assertTrue(h.god(top.occupant).power >= g.power, "the summit goes to the mightiest");
            for (History.God g : h.gods) {
                Sites.Site s = sites.of(g.id);
                Lore.Testimony loc = Lore.render(h, sites, "LOC:" + g.id);
                if (s == null) continue;
                assertNotNull(loc);
                assertTrue(loc.text.contains(s.label), loc.text);
            }
        }
    }

    @Test
    void everySanctumRendersCompletely() {
        for (int i = 0; i < 80; i++) {
            History h = HistorySimulator.simulate("sanctum-" + i);
            Sites sites = Sites.assign(h);
            for (History.God g : h.gods) {
                if (!g.alive) continue;
                Sanctum s = Sanctum.plan(h, sites, g.id);
                assertTrue(s.murals.size() >= 3, "the gallery is never bare");
                for (String id : s.murals) {
                    Lore.Testimony t = Lore.render(h, sites, id);
                    assertNotNull(t, id);
                    assertFalse(t.text.contains("null"), t.text);
                    assertTrue(t.level <= 2);
                }
                for (String id : s.reliquaries) {
                    Lore.Testimony t = Lore.render(h, sites, id);
                    assertNotNull(t, id);
                    assertFalse(t.text.contains("null"), t.text);
                    assertEquals(3, t.level, id);
                }
            }
        }
    }
}
