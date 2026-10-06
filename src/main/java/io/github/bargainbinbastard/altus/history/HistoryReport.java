package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.History.God;
import io.github.bargainbinbastard.altus.history.History.Group;
import io.github.bargainbinbastard.altus.history.History.LogLine;
import io.github.bargainbinbastard.altus.history.History.Region;
import io.github.bargainbinbastard.altus.history.History.Secret;

/** A readable, spoiler-filled report of a world's history. For developers and server operators. */
public final class HistoryReport {
    private HistoryReport() {}

    public static String render(History s) {
        StringBuilder b = new StringBuilder();
        b.append("THE ALTUS: history for seed ").append(s.seed).append("\n\n");

        b.append("== PANTHEON ==\n");
        for (God g : s.gods) {
            b.append(Text.cap(g.name)).append(g.alive ? "" : " (dead, slain in " + Text.year(g.deathYear) + ")").append('\n');
            String origin = Content.ORIGIN_LABEL.get(g.origin);
            if (g.creator != null) origin += ", made by " + s.name(g.creator);
            if (g.sourceCorpse != null) origin += ", risen from " + s.name(g.sourceCorpse);
            b.append("  ").append(origin).append("; born ").append(Text.year(g.born)).append("; power ").append(g.startPower)
                    .append(" -> ").append(g.power).append('\n');
            List<String> ts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : g.traits.entrySet()) ts.add(e.getKey() + " " + e.getValue());
            b.append("  traits: ").append(String.join(", ", ts)).append('\n');
            b.append("  likes: ").append(items(g.likes)).append('\n');
            b.append("  dislikes: ").append(items(g.dislikes)).append('\n');
        }

        b.append("\n== GROUPS ==\n");
        if (s.groups.isEmpty()) b.append("none\n");
        for (Group gr : s.groups) {
            b.append(Text.cap(gr.name)).append(": sworn ").append(Text.principlePhrase(gr.principle, s)).append("; ")
                    .append(gr.secret ? "secret" : "public").append("; founded ").append(Text.year(gr.founded));
            if (gr.dissolved) b.append("; dissolved ").append(Text.year(gr.dissolvedYear)).append(" (").append(gr.dissolvedWhy).append(")");
            else b.append("; members ").append(s.names(gr.members));
            b.append('\n');
        }

        b.append("\n== SECRETS ==\n");
        if (s.secrets.isEmpty()) b.append("none\n");
        for (Secret sec : s.secrets)
            b.append(sec.id).append(' ').append(sec.kind).append(sec.exposed ? " [exposed] " : " [kept] ").append(sec.text).append('\n');

        b.append("\n== HISTORY ==\n");
        for (LogLine l : s.log) {
            String yr = l.indent > 0 ? "    " : Text.year(l.year);
            b.append(yr).append(' ').append(String.format("%-11s", l.tag)).append(' ').append("  ".repeat(l.indent)).append(l.text).append('\n');
        }

        b.append("\n== ALTUS REGIONS ==\n");
        for (Region r : s.regions) b.append(r.name).append(": ").append(r.status).append('\n');
        b.append("\n== OPEN TENSIONS ==\n");
        for (String t : s.tensions) b.append(Text.cap(t)).append('\n');

        b.append("\n== SAMPLE LORE FRAGMENTS ==\n");
        for (Fragments.Fragment f : Fragments.generate(s)) {
            b.append("\n[").append(f.source.toLowerCase()).append(f.isFalse() ? ", FALSE" : "").append("] ").append(f.title).append('\n');
            b.append("  ").append(f.text).append('\n');
            if (f.tell != null) b.append("  ").append(f.tell).append('\n');
            b.append("  ").append(f.att).append('\n');
        }
        return b.toString();
    }

    private static String items(List<Item> list) {
        List<String> out = new ArrayList<>();
        for (Item i : list) out.add(Text.itemPhrase(i));
        return String.join(", ", out);
    }
}
