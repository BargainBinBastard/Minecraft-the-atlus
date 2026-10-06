package io.github.bargainbinbastard.altus;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.lore.AltusCommands;
import io.github.bargainbinbastard.altus.lore.WorldHistory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Entry point for The Altus.
 */
@Mod(AltusMod.MODID)
public class AltusMod {
    public static final String MODID = "altus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AltusMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(AltusMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(AltusMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(AltusMod::onServerStopped);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        AltusCommands.register(event.getDispatcher());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        long start = System.nanoTime();
        History h = WorldHistory.get(event.getServer());
        LOGGER.info("The Altus: generated the history of this world in {} ms: {} gods, {} events, {} groups, {} secrets.",
                (System.nanoTime() - start) / 1_000_000, h.gods.size(), h.events.size(), h.groups.size(), h.secrets.size());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        WorldHistory.clear();
    }
}
