package io.github.bargainbinbastard.altus.history;

/** Something a god can like or dislike: a block, an entity type, or an action. */
public final class Item {
    public final String cat;
    public final String id;
    public final String label;
    public final String verb;

    public Item(String cat, String id, String label, String verb) {
        this.cat = cat;
        this.id = id;
        this.label = label;
        this.verb = verb;
    }

    public String key() {
        return cat + ":" + id;
    }

    public boolean sameAs(Item o) {
        return key().equals(o.key());
    }

    /** Short label used in logs: action ids, otherwise display names. */
    public String lbl() {
        return cat.equals("action") ? id : label;
    }

    @Override
    public String toString() {
        return key();
    }
}
