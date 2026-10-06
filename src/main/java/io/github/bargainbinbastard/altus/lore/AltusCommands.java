package io.github.bargainbinbastard.altus.lore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.HistoryReport;
import io.github.bargainbinbastard.altus.history.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
                .then(Commands.literal("dump").executes(ctx -> dump(ctx.getSource()))));
    }

    private static int summary(CommandSourceStack src) {
        History h = WorldHistory.get(src.getServer());
        long alive = h.gods.stream().filter(g -> g.alive).count();
        List<String> lines = new ArrayList<>();
        lines.add("The Altus: " + h.gods.size() + " gods (" + alive + " alive), " + h.events.size() + " events, "
                + h.groups.size() + " groups, " + h.secrets.size() + " secrets, " + h.lies.size() + " lies.");
        for (History.God g : h.gods)
            lines.add("  " + Text.cap(g.name) + (g.alive ? ", power " + g.power : ", slain in " + Text.year(g.deathYear)));
        if (!h.deicides.isEmpty()) lines.add("Deicides: " + h.deicides.size());
        lines.add("Use /altus dump for the full history.");
        for (String l : lines) src.sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    private static int dump(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        History h = WorldHistory.get(server);
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("altus");
        Path file = dir.resolve("history.txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, HistoryReport.render(h), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AltusMod.LOGGER.error("Could not write the Altus history", e);
            src.sendFailure(Component.literal("Could not write the history: " + e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Wrote the full history to " + file.toAbsolutePath()), false);
        return 1;
    }
}
