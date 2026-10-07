package io.github.bargainbinbastard.altus.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoreTest {

    @Test
    void everyWoodsInscriptionRendersForEveryGod() {
        for (int i = 0; i < 60; i++) {
            History h = HistorySimulator.simulate("lore-" + i);
            for (History.God g : h.gods) {
                for (int f = 0; f < Lore.WOODS_FACETS.length; f++) {
                    String id = Lore.woodsId(f, g.id);
                    Lore.Testimony t = Lore.render(h, id);
                    assertNotNull(t, id + " in world lore-" + i);
                    assertEquals(id, t.id);
                    assertEquals(g.id, t.subject);
                    assertFalse(t.title.isBlank());
                    assertTrue(Character.isUpperCase(t.text.charAt(0)), t.text);
                    assertFalse(t.text.contains("null"), t.text);
                }
            }
        }
    }

    @Test
    void nonsenseIdsRenderNothing() {
        History h = HistorySimulator.simulate("lore-x");
        assertNull(Lore.render(h, "W:LIKES:999"));
        assertNull(Lore.render(h, "W:SMELLS:0"));
        assertNull(Lore.render(h, "garbage"));
    }

    @Test
    void fallbackGodIsVisibleAndAlive() {
        for (int i = 0; i < 60; i++) {
            History h = HistorySimulator.simulate("lore-fb-" + i);
            int g = Lore.woodsFallbackGod(h);
            if (g < 0) continue;
            assertTrue(h.god(g).alive);
            assertFalse(Lore.hidden(h, g));
        }
    }
}
