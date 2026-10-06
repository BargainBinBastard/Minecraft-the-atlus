package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.List;

import io.github.bargainbinbastard.altus.history.History.God;

/** Comparisons between gods' likes and dislikes. */
public final class Likes {
    private Likes() {}

    /** Items both gods like, in a's order. */
    public static List<Item> shared(God a, God b) {
        List<Item> out = new ArrayList<>();
        for (Item l : a.likes) if (contains(b.likes, l)) out.add(l);
        return out;
    }

    /** Items a likes that b dislikes, in a's order. */
    public static List<Item> conflicts(God a, God b) {
        List<Item> out = new ArrayList<>();
        for (Item l : a.likes) if (contains(b.dislikes, l)) out.add(l);
        return out;
    }

    public static boolean contains(List<Item> list, Item it) {
        for (Item m : list) if (m.sameAs(it)) return true;
        return false;
    }
}
