package io.github.bargainbinbastard.altus;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings, stored per world in serverconfig/altus-server.toml. */
public final class AltusConfig {
    private AltusConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue DREAM_SECONDS;
    public static final ModConfigSpec.IntValue SCAN_RADIUS;
    public static final ModConfigSpec.IntValue SCAN_THRESHOLD;
    public static final ModConfigSpec.IntValue FADE_MINUTES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("dreams");
        DREAM_SECONDS = b.comment("How long a visit to the Altus lasts, in seconds.")
                .defineInRange("dreamSeconds", 600, 30, 7200);
        SCAN_RADIUS = b.comment("How far from the bed to count blocks when deciding which god a sleeper dreams toward.")
                .defineInRange("scanRadius", 8, 2, 16);
        SCAN_THRESHOLD = b.comment("The score a god needs before a sleeper dreams at all. Liked blocks add one, disliked blocks subtract one.")
                .defineInRange("scanThreshold", 4, 1, 1000);
        b.pop();
        b.push("memories");
        FADE_MINUTES = b.comment("How many minutes a memory lasts after waking before it fades, unless written in a Tome.")
                .defineInRange("fadeMinutes", 45, 1, 1440);
        b.pop();
        SPEC = b.build();
    }
}
