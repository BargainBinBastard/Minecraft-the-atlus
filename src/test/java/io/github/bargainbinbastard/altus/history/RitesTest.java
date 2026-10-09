package io.github.bargainbinbastard.altus.history;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RitesTest {

    /** Every lore id a world could put in a Tome. */
    static List<String> allIds(History h, Sites sites) {
        List<String> ids = new ArrayList<>();
        for (History.God g : h.gods) {
            for (int f = 0; f < Lore.WOODS_FACETS.length; f++) ids.add(Lore.woodsId(f, g.id));
            if (sites.of(g.id) == null) continue;
            Sanctum s = g.alive ? Sanctum.plan(h, sites, g.id) : Sanctum.planRuin(h, sites, g.id);
            ids.addAll(s.murals);
            ids.addAll(s.reliquaries);
            ids.addAll(s.echo);
            ids.addAll(s.inner);
        }
        return ids;
    }

    @Test
    void everyEntryIsARiteAndEveryGodHasAGift() {
        int falseOnes = 0, total = 0;
        for (int i = 0; i < 80; i++) {
            History h = HistorySimulator.simulate("rites-" + i);
            Sites sites = Sites.assign(h);
            for (History.God g : h.gods) assertNotNull(Rites.giftOf(g));
            for (String id : allIds(h, sites)) {
                if (Lore.render(h, sites, id) == null) continue;
                total++;
                assertNotNull(Rites.effectOf(h, sites, id), id);
                List<Integer> named = Rites.namedGods(h, id);
                assertFalse(named.isEmpty(), "a rite names someone to offer to: " + id);
                assertTrue(named.size() <= 3);
                if (Rites.isFalse(h, id)) {
                    falseOnes++;
                    assertTrue(id.startsWith("EV:"), id);
                }
                if (id.startsWith("TR:") || id.startsWith("CF:") || id.startsWith("W:")) assertFalse(Rites.isFalse(h, id), id);
            }
        }
        assertTrue(total > 1000);
        assertTrue(falseOnes > 0, "some written lies should be there to misfire");
    }
}
