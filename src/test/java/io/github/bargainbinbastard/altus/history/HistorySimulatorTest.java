package io.github.bargainbinbastard.altus.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.Group;
import io.github.bargainbinbastard.altus.history.History.Lie;
import io.github.bargainbinbastard.altus.history.History.LogLine;

class HistorySimulatorTest {

    /** Hashes taken from the JavaScript prototype. A mismatch means the engine's behavior changed. */
    private static final String[][] GOLDEN = {
            {"golden-0", "6263cef7f74711c67a7d27da7691bf1d1b9cac4a8aa6c0a48add9065e88e4913"},
            {"golden-1", "6212f1fe64312b6658f3844099ba3612dfe15b9a7da530d68a5be25850466f00"},
            {"golden-2", "e6bc6a192b0e6382b0680e6fee6825e9ef89d9e085e5d2d34ed2d61e5ec32270"},
            {"altus-31", "e10cada144d7b64f7e0b27974be35ac4c65315fef9899dfb1365e05f18de4a0d"},
            {"altus-296", "50e51a4a24d5d00c237126a584ab7359f3b80ed2929d7e1de8277c44bab67a4f"},
    };

    private static String hash(String seed) throws Exception {
        History s = HistorySimulator.simulate(seed);
        List<String> lines = new ArrayList<>();
        for (LogLine l : s.log) lines.add(l.year + "|" + l.tag + "|" + l.cls + "|" + l.cat + "|" + l.indent + "|" + l.text);
        for (Fragments.Fragment f : Fragments.generate(s)) lines.add(f.no + "|" + f.title + "|" + f.text);
        byte[] d = MessageDigest.getInstance("SHA-256").digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d);
    }

    @Test
    void matchesPrototypeExactly() throws Exception {
        for (String[] g : GOLDEN) assertEquals(g[1], hash(g[0]), "history for seed " + g[0] + " drifted from the prototype");
    }

    @Test
    void sameSeedSameHistory() throws Exception {
        assertEquals(hash("determinism"), hash("determinism"));
    }

    @Test
    void invariantsHoldAcrossManySeeds() {
        int worldsWithDeicide = 0;
        for (int i = 0; i < 150; i++) {
            History s = HistorySimulator.simulate("inv-" + i);
            assertFalse(s.gods.isEmpty());
            assertEquals("glory", s.gods.get(0).origin, "the first god is always the god-from-glory");
            assertEquals(1, s.gods.stream().filter(g -> g.origin.equals("glory")).count());
            for (God g : s.gods) {
                assertTrue(g.traits.size() >= 1 && g.traits.size() <= 3, "1 to 3 traits");
                assertFalse(g.traits.containsKey("historian") && g.traits.containsKey("obfuscator"));
                assertTrue(g.power >= 1);
            }
            for (Lie l : s.lies)
                for (int t : l.tellers) assertFalse(s.god(t).has("historian"), "historians never lie");
            for (Group gr : s.groups)
                for (int m : gr.members) assertNotNull(gr.loyalty.get(m), "every member has a loyalty score");
            for (LogLine l : s.log) {
                assertFalse(l.text.contains("null"), "no null leaked into the log: " + l.text);
                assertFalse(l.text.contains("NaN"), l.text);
            }
            for (Fragments.Fragment f : Fragments.generate(s)) {
                assertNotNull(f.title);
                assertFalse(f.title.isBlank());
                assertFalse(f.text.contains("null"), f.text);
            }
            if (!s.deicides.isEmpty()) worldsWithDeicide++;
        }
        assertTrue(worldsWithDeicide > 5 && worldsWithDeicide < 75, "deicide should be uncommon: " + worldsWithDeicide + "/150");
    }
}
