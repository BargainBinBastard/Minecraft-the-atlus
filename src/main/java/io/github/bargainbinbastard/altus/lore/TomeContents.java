package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What's written in a Tome: its title, its pages, and the hidden records of entries written from memories. */
public record TomeContents(String title, List<String> pages, List<TomeRecord> records) {
    public static final int MAX_PAGES = 100;
    public static final int MAX_PAGE_CHARS = 1024;
    public static final int MAX_TITLE = 32;
    public static final TomeContents EMPTY = new TomeContents("", List.of(""), List.of());

    public TomeContents {
        title = title == null ? "" : title;
        pages = List.copyOf(pages);
        records = List.copyOf(records);
    }

    public static final Codec<TomeContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("title", "").forGetter(TomeContents::title),
            Codec.STRING.listOf().fieldOf("pages").forGetter(TomeContents::pages),
            TomeRecord.CODEC.listOf().fieldOf("records").forGetter(TomeContents::records)).apply(i, TomeContents::new));

    public static final StreamCodec<ByteBuf, TomeContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TomeContents::title,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TomeContents::pages,
            TomeRecord.STREAM_CODEC.apply(ByteBufCodecs.list()), TomeContents::records,
            TomeContents::new);

    /** A title the player typed: no formatting codes, no line breaks, not too long. */
    public static String sanitizeTitle(String in) {
        if (in == null) return "";
        String t = in.replace("\u00a7", "").replaceAll("[\\r\\n\\t]", " ").trim();
        return t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE) : t;
    }

    /** Clamps page count and length, so a client can't stuff a Tome with unlimited text. */
    public static List<String> sanitize(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null)
            for (String p : in) {
                if (out.size() >= MAX_PAGES) break;
                String s = p == null ? "" : p;
                out.add(s.length() > MAX_PAGE_CHARS ? s.substring(0, MAX_PAGE_CHARS) : s);
            }
        if (out.isEmpty()) out.add("");
        return out;
    }

    /** Keeps only the records whose text still appears, unedited, somewhere in the pages. */
    public static List<TomeRecord> reconcile(List<TomeRecord> records, List<String> pages) {
        String all = String.join("\n", pages);
        List<TomeRecord> out = new ArrayList<>();
        for (TomeRecord r : records) if (all.contains(r.text())) out.add(r);
        return out;
    }
}
