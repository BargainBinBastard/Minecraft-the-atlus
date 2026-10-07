package io.github.bargainbinbastard.altus.lore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The hidden record behind a Tome entry written from a memory. The visible text is ordinary page
 * text; this record is how the game knows the entry is real. If the text is edited away, the
 * record is dropped.
 */
public record TomeRecord(String entryId, String testimonyId, String writer, String text) {
    public static final Codec<TomeRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("entry").forGetter(TomeRecord::entryId),
            Codec.STRING.fieldOf("testimony").forGetter(TomeRecord::testimonyId),
            Codec.STRING.fieldOf("writer").forGetter(TomeRecord::writer),
            Codec.STRING.fieldOf("text").forGetter(TomeRecord::text)).apply(i, TomeRecord::new));

    public static final StreamCodec<ByteBuf, TomeRecord> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TomeRecord::entryId,
            ByteBufCodecs.STRING_UTF8, TomeRecord::testimonyId,
            ByteBufCodecs.STRING_UTF8, TomeRecord::writer,
            ByteBufCodecs.STRING_UTF8, TomeRecord::text,
            TomeRecord::new);
}
