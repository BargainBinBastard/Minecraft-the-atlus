package io.github.bargainbinbastard.altus.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.bargainbinbastard.altus.history.History.God;

class FocusPickerTest {

    private static String firstLikedBlock(God g) {
        for (Item l : g.likes) if (l.cat.equals("block")) return l.id;
        throw new IllegalStateException("every god likes at least one block");
    }

    @Test
    void emptyRoomMeansOrdinarySleep() {
        History h = HistorySimulator.simulate("focus-1");
        FocusPicker.Focus f = FocusPicker.pick(h, Map.of(), 4);
        assertEquals(FocusPicker.Kind.NONE, f.kind);
        assertFalse(f.dreams());
    }

    @Test
    void aRoomFullOfOneGodsBlocksFindsAGod() {
        int found = 0;
        for (int i = 0; i < 40; i++) {
            History h = HistorySimulator.simulate("focus-" + i);
            for (God g : h.gods) {
                if (!g.alive) continue;
                Map<String, Integer> counts = new HashMap<>();
                for (Item l : g.likes) if (l.cat.equals("block")) counts.put(l.id, 20);
                FocusPicker.Focus f = FocusPicker.pick(h, counts, 4);
                assertTrue(f.dreams(), "20 of each liked block should always reach the threshold");
                if (f.kind == FocusPicker.Kind.GOD && f.god == g.id) found++;
            }
        }
        assertTrue(found > 0, "a room built for a god should usually focus on that god");
    }

    @Test
    void dislikedBlocksSubtract() {
        History h = HistorySimulator.simulate("focus-2");
        God g = h.gods.get(0);
        Map<String, Integer> counts = new HashMap<>();
        counts.put(firstLikedBlock(g), 6);
        for (Item l : g.dislikes) if (l.cat.equals("block")) counts.put(l.id, 6);
        int s = FocusPicker.score(g, counts);
        assertTrue(s <= 0, "six liked minus at least six disliked should be zero or less, got " + s);
    }

    @Test
    void tiesGoToTheWoods() {
        for (int i = 0; i < 60; i++) {
            History h = HistorySimulator.simulate("tie-" + i);
            if (h.gods.size() < 2) continue;
            God a = h.gods.get(0), b = h.gods.get(1);
            if (!a.alive || !b.alive) continue;
            String la = firstLikedBlock(a), lb = firstLikedBlock(b);
            if (la.equals(lb)) continue;
            boolean aLikesB = false, bLikesA = false, aHatesB = false, bHatesA = false;
            for (Item l : a.likes) if (l.id.equals(lb)) aLikesB = true;
            for (Item l : b.likes) if (l.id.equals(la)) bLikesA = true;
            for (Item l : a.dislikes) if (l.id.equals(lb)) aHatesB = true;
            for (Item l : b.dislikes) if (l.id.equals(la)) bHatesA = true;
            if (aLikesB || bLikesA || aHatesB || bHatesA) continue;
            Map<String, Integer> counts = Map.of(la, 10, lb, 10);
            FocusPicker.Focus f = FocusPicker.pick(h, counts, 4);
            if (f.scores.get(a.id) == 10 && f.scores.get(b.id) == 10 && f.score == 10) {
                assertEquals(FocusPicker.Kind.TIE, f.kind);
                return;
            }
        }
    }
}
