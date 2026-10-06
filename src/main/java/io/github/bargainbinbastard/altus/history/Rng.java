package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Deterministic random numbers, bit-for-bit identical to the JavaScript prototype
 * (xmur3 string hash feeding mulberry32). Identical streams let the Java engine be
 * checked line-by-line against the prototype's output.
 */
public final class Rng {
    private int state;

    public Rng(String seed, String salt) {
        this.state = xmur3(seed + "::" + salt);
    }

    private static int xmur3(String str) {
        int h = 1779033703 ^ str.length();
        for (int i = 0; i < str.length(); i++) {
            h = (h ^ str.charAt(i)) * -862048943;
            h = (h << 13) | (h >>> 19);
        }
        h = (h ^ (h >>> 16)) * -2048144789;
        h = (h ^ (h >>> 13)) * -1028477387;
        h ^= h >>> 16;
        return h;
    }

    /** Uniform double in [0, 1). */
    public double f() {
        state = state + 0x6D2B79F5;
        int t = (state ^ (state >>> 15)) * (1 | state);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** Integer in [a, b], inclusive. */
    public int nextInt(int a, int b) {
        return a + (int) Math.floor(f() * (b - a + 1));
    }

    public boolean chance(double p) {
        return f() < p;
    }

    public <T> T pick(List<T> list) {
        return list.get((int) Math.floor(f() * list.size()));
    }

    public <T> List<T> shuffle(List<T> in) {
        List<T> a = new ArrayList<>(in);
        for (int i = a.size() - 1; i > 0; i--) {
            int j = (int) Math.floor(f() * (i + 1));
            T tmp = a.get(i);
            a.set(i, a.get(j));
            a.set(j, tmp);
        }
        return a;
    }

    /** Weighted choice; negative weights count as zero. Returns null if all weights are zero. */
    public <T> T weighted(List<T> list, ToDoubleFunction<T> weight) {
        double tot = 0;
        for (T x : list) tot += Math.max(0, weight.applyAsDouble(x));
        if (tot <= 0) return null;
        double r = f() * tot;
        for (T x : list) {
            r -= Math.max(0, weight.applyAsDouble(x));
            if (r <= 0) return x;
        }
        return list.get(list.size() - 1);
    }
}
