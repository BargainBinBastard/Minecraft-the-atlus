package io.github.bargainbinbastard.altus.lore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.bargainbinbastard.altus.AltusConfig;
import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.dream.AltusSession;
import io.github.bargainbinbastard.altus.dream.Dreams;
import io.github.bargainbinbastard.altus.dream.SleepScan;
import io.github.bargainbinbastard.altus.history.FocusPicker;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.HistoryReport;
import io.github.bargainbinbastard.altus.history.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Operator commands for inspecting the generated history. They spoil the mystery, so they
 * require permission level 2.
 */
public final class AltusCommands {
    private AltusCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("altus")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("history").executes(ctx -> summary(ctx.getSource())))
                .then(Commands.literal("dump").executes(ctx -> dump(ctx.getSource())))
                .then(Commands.literal("dream")
                        .executes(ctx -> dream(ctx.getSource(), AltusConfig.DREAM_SECONDS.get()))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 7200))
                                .executes(ctx -> dream(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("wake").executes(ctx -> wake(ctx.getSource())))
                .then(Commands.literal("scan").executes(ctx -> scan(ctx.getSource())))
                .then(Commands.literal("sites").executes(ctx -> sites(ctx.getSource())))
                .then(Commands.literal("visit").then(Commands.argument("god", IntegerArgumentType.integer(0, 1000))
                        .executes(ctx -> visit(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "god"))))));
    }

    /** Lists the Mountain's door sites and who holds each. */
    private static int sites(CommandSourceStack src) {
        History h = WorldHistory.get(src.getServer());
        io.github.bargainbinbastard.altus.history.Sites sites = WorldHistory.sites(src.getServer());
        for (io.github.bargainbinbastard.altus.history.Sites.Site s : sites.all) {
            String who = s.occupant < 0 ? "empty" : "#" + s.occupant + " " + h.name(s.occupant)
                    + (h.god(s.occupant).alive ? ", power " + h.god(s.occupant).power : " (dead: a ruin)");
            net.minecraft.core.BlockPos d = AltusWorld.doorBase(s);
            String line = Text.cap(s.label) + ": " + who + "  [" + d.getX() + " " + d.getY() + " " + d.getZ() + "]";
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** While dreaming, jumps to a god's door. */
    private static int visit(CommandSourceStack src, int god) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        if (!Dreams.session(sp).active) {
            src.sendFailure(Component.literal("You must be dreaming first: /altus dream"));
            return 0;
        }
        net.minecraft.core.BlockPos a = AltusWorld.siteArrivalOf(god);
        if (a == null) {
            src.sendFailure(Component.literal("That god has no door."));
            return 0;
        }
        sp.teleportTo(sp.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, sp.getYRot(), 0f);
        return 1;
    }

    /** Enters the Altus at once, as if asleep here. The blocks around you still pick the focus god. */
    private static int dream(CommandSourceStack src, int seconds) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        if (Dreams.session(sp).active) {
            src.sendFailure(Component.literal("You are already dreaming."));
            return 0;
        }
        History h = WorldHistory.get(src.getServer());
        Map<String, Integer> counts = SleepScan.count(sp.level(), sp.blockPosition(), AltusConfig.SCAN_RADIUS.get());
        FocusPicker.Focus focus = FocusPicker.pick(h, counts, AltusConfig.SCAN_THRESHOLD.get());
        boolean ok = Dreams.begin(sp, focus.kind == FocusPicker.Kind.GOD ? focus.god : -1, seconds * 20, sp.getX(), sp.getY(), sp.getZ(),
                Dreams.intro(h, focus, counts));
        if (!ok) src.sendFailure(Component.literal("Could not enter the Altus; see the server log."));
        return ok ? 1 : 0;
    }

    private static int wake(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        AltusSession s = Dreams.session(sp);
        if (!s.active) {
            src.sendFailure(Component.literal("You are not dreaming."));
            return 0;
        }
        Dreams.end(sp, "command");
        return 1;
    }

    /** Shows how the blocks around you score for each god, and who a sleeper here would dream toward. */
    private static int scan(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        History h = WorldHistory.get(src.getServer());
        Map<String, Integer> counts = SleepScan.count(sp.level(), sp.blockPosition(), AltusConfig.SCAN_RADIUS.get());
        FocusPicker.Focus f = FocusPicker.pick(h, counts, AltusConfig.SCAN_THRESHOLD.get());
        List<String> lines = new ArrayList<>();
        lines.add("Blocks counted: " + (counts.isEmpty() ? "none" : counts.toString()));
        f.scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(4)
                .forEach(e -> lines.add("  " + Text.cap(h.name(e.getKey())) + ": " + e.getValue()));
        String verdict = switch (f.kind) {
            case NONE -> "Ordinary sleep: no god reaches " + AltusConfig.SCAN_THRESHOLD.get() + ".";
            case TIE -> "A tie: the sleeper dreams of the Woods with no focus.";
            case GOD -> "The sleeper dreams toward " + h.name(f.god) + ".";
        };
        lines.add(verdict);
        for (String l : lines) src.sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    private static int summary(CommandSourceStack src) {
        History h = WorldHistory.get(src.getServer());
        long alive = h.gods.stream().filter(g -> g.alive).count();
        List<String> lines = new ArrayList<>();
        lines.add("The Altus: " + h.gods.size() + " gods (" + alive + " alive), " + h.events.size() + " events, "
                + h.groups.size() + " groups, " + h.secrets.size() + " secrets, " + h.lies.size() + " lies.");
        for (History.God g : h.gods)
            lines.add("  #" + g.id + " " + Text.cap(g.name) + (g.alive ? ", power " + g.power : ", slain in " + Text.year(g.deathYear)));
        if (!h.deicides.isEmpty()) lines.add("Deicides: " + h.deicides.size());
        lines.add("Use /altus dump for the full history.");
        for (String l : lines) src.sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    private static int dump(CommandSourceStack src) {
        Path file = writeReport(src.getServer());
        if (file == null) {
            src.sendFailure(Component.literal("Could not write the history; see the server log."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Wrote the full history to " + file.toAbsolutePath()), false);
        return 1;
    }

    /** Writes the full report to {@code <world>/altus/history.txt}. Returns the path, or null on failure. */
    public static Path writeReport(MinecraftServer server) {
        History h = WorldHistory.get(server);
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("altus");
        Path file = dir.resolve("history.txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, HistoryReport.render(h), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            AltusMod.LOGGER.error("Could not write the Altus history", e);
            return null;
        }
    }
}
