package io.github.bargainbinbastard.altus.history;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.History.God;

/**
 * Decides which god a sleeper dreams toward, from the blocks counted around their bed.
 * Each liked block adds a point and each disliked block takes one away. The top living god
 * must reach the threshold; a tie at the top sends the sleeper to the Woods with no focus.
 */
public final class FocusPicker {
    private FocusPicker() {}

    public enum Kind {
        /** No god reached the threshold: an ordinary night's sleep. */
        NONE,
        /** Two or more gods share the top score: dream, but with no focus. */
        TIE,
        /** One god clearly leads. */
        GOD
    }

    public static final class Focus {
        public final Kind kind;
        /** The focus god's id, or -1. */
        public final int god;
        public final int score;
        /** Every living god's score, in god order. */
        public final Map<Integer, Integer> scores;

        Focus(Kind kind, int god, int score, Map<Integer, Integer> scores) {
            this.kind = kind;
            this.god = god;
            this.score = score;
            this.scores = Collections.unmodifiableMap(scores);
        }

        public boolean dreams() {
            return kind != Kind.NONE;
        }
    }

    /**
     * @param counts how many blocks of each likeable block id (for example "copper") were found
     */
    public static Focus pick(History h, Map<String, Integer> counts, int threshold) {
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        int best = Integer.MIN_VALUE, bestId = -1, atBest = 0;
        for (God g : h.gods) {
            if (!g.alive) continue;
            int s = score(g, counts);
            scores.put(g.id, s);
            if (s > best) {
                best = s;
                bestId = g.id;
                atBest = 1;
            } else if (s == best) atBest++;
        }
        if (bestId < 0 || best < threshold) return new Focus(Kind.NONE, -1, best, scores);
        if (atBest > 1) return new Focus(Kind.TIE, -1, best, scores);
        return new Focus(Kind.GOD, bestId, best, scores);
    }

    public static int score(God g, Map<String, Integer> counts) {
        int s = 0;
        for (Item l : g.likes) if (l.cat.equals("block")) s += counts.getOrDefault(l.id, 0);
        for (Item l : g.dislikes) if (l.cat.equals("block")) s -= counts.getOrDefault(l.id, 0);
        return s;
    }
}
