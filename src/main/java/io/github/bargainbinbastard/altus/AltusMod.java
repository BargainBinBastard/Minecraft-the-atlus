package io.github.bargainbinbastard.altus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import io.github.bargainbinbastard.altus.dream.AltusAttachments;
import io.github.bargainbinbastard.altus.dream.AltusDimension;
import io.github.bargainbinbastard.altus.dream.DreamEvents;
import io.github.bargainbinbastard.altus.dream.Dreams;
import io.github.bargainbinbastard.altus.dream.SleepScan;
import io.github.bargainbinbastard.altus.history.FocusPicker;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Item;
import io.github.bargainbinbastard.altus.lore.AltusCommands;
import io.github.bargainbinbastard.altus.lore.WorldHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
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
        AltusAttachments.TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, AltusConfig.SPEC);
        DreamEvents.register();
        NeoForge.EVENT_BUS.addListener(AltusMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(AltusMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(AltusMod::onServerStopped);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        AltusCommands.register(event.getDispatcher());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        long start = System.nanoTime();
        History h = WorldHistory.get(server);
        LOGGER.info("The Altus: generated the history of this world in {} ms: {} gods, {} events, {} groups, {} secrets.",
                (System.nanoTime() - start) / 1_000_000, h.gods.size(), h.events.size(), h.groups.size(), h.secrets.size());
        if (Boolean.getBoolean("altus.smokeTest")) smokeTest(server, h);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        WorldHistory.clear();
    }

    /** Run by CI on a dedicated server: checks the mod's pieces work in a real game, then stops. */
    private static void smokeTest(MinecraftServer server, History h) {
        boolean ok = true;
        try {
            ServerLevel altus = AltusDimension.get(server);
            if (altus == null) {
                LOGGER.error("SMOKE: the Altus dimension is missing");
                ok = false;
            } else {
                BlockPos spot = AltusDimension.clearing(altus, RandomSource.create(1));
                LOGGER.info("SMOKE: the Altus loaded; a Clearing spot is {} on {}", spot, altus.getBlockState(spot.below()));
                if (spot.getY() < 60) {
                    LOGGER.error("SMOKE: the Clearing is below the expected ground level");
                    ok = false;
                }
            }
            ok &= smokeScan(server, h);
            ok &= smokeDream(server);
            ok &= AltusCommands.writeReport(server) != null;
        } catch (Exception e) {
            LOGGER.error("SMOKE: exception", e);
            ok = false;
        }
        LOGGER.info(ok ? "ALTUS SMOKE TEST PASSED" : "ALTUS SMOKE TEST FAILED");
        server.halt(false);
    }

    /** Sends a fake player through a whole dream and checks its inventory is stashed and returned. */
    private static boolean smokeDream(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        FakePlayer fp = FakePlayerFactory.get(ow, new GameProfile(UUID.fromString("6c1f0a52-3f7e-4f0e-9d7c-a1b2c3d4e5f6"), "AltusSmoke"));
        BlockPos spawn = ow.getSharedSpawnPos();
        fp.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        fp.getInventory().clearContent();
        fp.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        boolean began = Dreams.begin(fp, -1, 200, fp.getX(), fp.getY(), fp.getZ(), Component.literal("smoke"));
        boolean inAltus = AltusDimension.isAltus(fp.level());
        boolean emptied = fp.getInventory().isEmpty();
        boolean active = Dreams.session(fp).active;
        Dreams.end(fp, "command");
        boolean home = !AltusDimension.isAltus(fp.level());
        boolean restored = fp.getInventory().countItem(Items.DIAMOND) == 3;
        boolean closed = !Dreams.session(fp).active;
        LOGGER.info("SMOKE: dream round trip: began={} inAltus={} emptied={} active={} | home={} restored={} closed={}",
                began, inAltus, emptied, active, home, restored, closed);
        return began && inAltus && emptied && active && home && restored && closed;
    }

    /** Places a god's liked block near spawn and checks the bed scan counts it and picks a focus. */
    private static boolean smokeScan(MinecraftServer server, History h) {
        History.God god = null;
        for (History.God g : h.gods) if (g.alive) { god = g; break; }
        if (god == null) return true;
        String id = null;
        for (Item l : god.likes) if (l.cat.equals("block")) { id = l.id; break; }
        TagKey<Block> tag = SleepScan.TAGS.get(id);
        Optional<HolderSet.Named<Block>> set = BuiltInRegistries.BLOCK.getTag(tag);
        if (set.isEmpty() || set.get().size() == 0) {
            LOGGER.error("SMOKE: block tag {} is empty", tag.location());
            return false;
        }
        int emptyTags = 0;
        for (Map.Entry<String, TagKey<Block>> e : SleepScan.TAGS.entrySet()) {
            Optional<HolderSet.Named<Block>> t = BuiltInRegistries.BLOCK.getTag(e.getValue());
            int n = t.map(HolderSet::size).orElse(0);
            if (n == 0) {
                LOGGER.error("SMOKE: block tag {} is empty", e.getValue().location());
                emptyTags++;
            }
        }
        Block block = set.get().get(0).value();
        ServerLevel ow = server.overworld();
        BlockPos c = ow.getSharedSpawnPos().above(100);
        for (int i = 0; i < 6; i++) ow.setBlockAndUpdate(c.offset(i - 3, 0, 2), block.defaultBlockState());
        Map<String, Integer> counts = SleepScan.count(ow, c, 8);
        int n = counts.getOrDefault(id, 0);
        FocusPicker.Focus f = FocusPicker.pick(h, counts, 4);
        LOGGER.info("SMOKE: placed 6 {} for {}; the scan counted {}; focus {} {}", block, god.name, n, f.kind,
                f.god >= 0 ? h.name(f.god) : "-");
        return n >= 6 && emptyTags == 0;
    }
}
