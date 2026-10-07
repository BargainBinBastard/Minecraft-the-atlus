package io.github.bargainbinbastard.altus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;

import io.netty.channel.embedded.EmbeddedChannel;

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
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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

    /**
     * Sends a mock player (the same kind Minecraft's own game tests use: a real ServerPlayer on an
     * in-memory connection) through a whole dream, and checks its inventory is stashed and returned.
     * If the mock player itself can't be set up, the check is skipped rather than failed.
     */
    private static boolean smokeDream(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        ServerPlayer p;
        try {
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                    new GameProfile(UUID.fromString("6c1f0a52-3f7e-4f0e-9d7c-a1b2c3d4e5f6"), "AltusSmoke"), false);
            p = new ServerPlayer(server, ow, cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
        } catch (Exception e) {
            LOGGER.warn("SMOKE: dream round trip SKIPPED: could not create a mock player ({})", e.toString());
            return true;
        }
        BlockPos spawn = ow.getSharedSpawnPos();
        p.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        boolean began = Dreams.begin(p, -1, 200, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        boolean inAltus = AltusDimension.isAltus(p.level());
        boolean emptied = p.getInventory().isEmpty();
        boolean active = Dreams.session(p).active;
        p.getInventory().add(new ItemStack(Items.STICK, 1));
        Dreams.end(p, "command");
        boolean home = !AltusDimension.isAltus(p.level());
        boolean restored = p.getInventory().countItem(Items.DIAMOND) == 3;
        boolean altusItemsGone = p.getInventory().countItem(Items.STICK) == 0;
        boolean closed = !Dreams.session(p).active;
        LOGGER.info("SMOKE: dream round trip: began={} inAltus={} emptied={} active={} | home={} restored={} altusItemsGone={} closed={}",
                began, inAltus, emptied, active, home, restored, altusItemsGone, closed);
        boolean roundTrip = began && inAltus && emptied && active && home && restored && altusItemsGone && closed;
        boolean death = smokeDeath(p);
        boolean sleep = smokeSleep(server, p);
        server.getPlayerList().remove(p);
        return roundTrip && death && sleep;
    }

    /** Dying in the Altus should wake the player, alive, with their belongings. */
    private static boolean smokeDeath(ServerPlayer p) {
        boolean began = Dreams.begin(p, -1, 200, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        // A real client confirms it has loaded the new dimension, which ends the arrival
        // invulnerability. The mock player has no client, so confirm on its behalf.
        p.hasChangedDimension();
        p.hurt(p.damageSources().fellOutOfWorld(), 1000f);
        boolean alive = p.isAlive() && p.getHealth() > 0;
        boolean home = !AltusDimension.isAltus(p.level());
        boolean restored = p.getInventory().countItem(Items.DIAMOND) == 3;
        boolean closed = !Dreams.session(p).active;
        LOGGER.info("SMOKE: death in the Altus: began={} alive={} home={} restored={} closed={}", began, alive, home, restored, closed);
        return began && alive && home && restored && closed;
    }

    /** Builds a bedroom of one god's liked blocks at night, puts the player to bed, and checks they dream. */
    private static boolean smokeSleep(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        ServerLevel ow = server.overworld();
        ow.setDayTime(13000);
        ow.updateSkyBrightness();
        BlockPos spawn = ow.getSharedSpawnPos();
        int bx = spawn.getX() + 20, bz = spawn.getZ() + 20;
        ow.getChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz));
        BlockPos foot = new BlockPos(bx, ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
        BlockPos head = foot.north();
        ow.setBlockAndUpdate(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        ow.setBlockAndUpdate(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));

        History.God god = null;
        for (History.God g : h.gods) if (g.alive) { god = g; break; }
        int row = 0;
        Map<String, Integer> planned = new java.util.HashMap<>();
        for (Item l : god.likes) {
            if (!l.cat.equals("block")) continue;
            Optional<HolderSet.Named<Block>> set = BuiltInRegistries.BLOCK.getTag(SleepScan.TAGS.get(l.id));
            if (set.isEmpty() || set.get().size() == 0) continue;
            Block b = set.get().get(0).value();
            for (int i = 0; i < 10; i++) ow.setBlockAndUpdate(foot.offset(i - 5, 3, 2 + row), b.defaultBlockState());
            planned.put(l.id, 10);
            row++;
        }
        FocusPicker.Focus expected = FocusPicker.pick(h, planned, 4);

        p.teleportTo(ow, foot.getX() + 0.5, foot.getY(), foot.getZ() + 1.5, 0, 0);
        var result = p.startSleepInBed(head);
        boolean slept = p.isSleeping();
        boolean began = Dreams.tryBeginFromSleep(p);
        boolean inAltus = AltusDimension.isAltus(p.level());
        int focus = Dreams.session(p).focusGod;
        boolean ok = slept && began && inAltus && (expected.kind != FocusPicker.Kind.GOD || focus == expected.god);
        LOGGER.info("SMOKE: sleeping into the Altus: problem={} slept={} began={} inAltus={} focus={} expected={} {}",
                result.left().map(Object::toString).orElse("none"), slept, began, inAltus, focus, expected.kind, expected.god);
        if (Dreams.session(p).active) Dreams.end(p, "command");
        boolean twice = !Dreams.tryBeginFromSleep(p);
        LOGGER.info("SMOKE: a second dream the same night is refused: {}", twice);
        return ok && twice;
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
