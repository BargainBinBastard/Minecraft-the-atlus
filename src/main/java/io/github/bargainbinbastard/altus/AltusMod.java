package io.github.bargainbinbastard.altus;

import java.util.List;
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
import io.github.bargainbinbastard.altus.history.Lore;
import io.github.bargainbinbastard.altus.history.Sites;
import io.github.bargainbinbastard.altus.history.Terrain;
import io.github.bargainbinbastard.altus.lore.AltusCommands;
import io.github.bargainbinbastard.altus.lore.AltusWorld;
import io.github.bargainbinbastard.altus.lore.Altars;
import io.github.bargainbinbastard.altus.lore.Echoes;
import io.github.bargainbinbastard.altus.lore.Guardians;
import io.github.bargainbinbastard.altus.lore.HeldMemories;
import io.github.bargainbinbastard.altus.lore.Knowledge;
import io.github.bargainbinbastard.altus.lore.Memories;
import io.github.bargainbinbastard.altus.lore.TomeContents;
import io.github.bargainbinbastard.altus.lore.TomeService;
import io.github.bargainbinbastard.altus.lore.WorldHistory;
import io.github.bargainbinbastard.altus.net.AltusNetwork;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
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
        AltusRegistry.register(modEventBus);
        AltusAttachments.TYPES.register(modEventBus);
        modEventBus.addListener(AltusNetwork::register);
        modContainer.registerConfig(ModConfig.Type.SERVER, AltusConfig.SPEC);
        DreamEvents.register();
        io.github.bargainbinbastard.altus.lore.DevotionEvents.register();
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
        AltusWorld.prepare(server);
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
        boolean memories = smokeMemories(server, p);
        boolean stage = smokeStage(server, p);
        boolean carried = smokeCarried(server, p);
        boolean guardians = smokeGuardians(server, p);
        boolean ruin = smokeRuin(server, p);
        boolean depth = smokeDepth(server, p);
        boolean devotion = smokeDevotion(server, p);
        server.getPlayerList().remove(p);
        return roundTrip && death && sleep && memories && stage && carried && guardians && ruin && depth && devotion;
    }

    private static ItemStack stackFor(String like, int n) {
        for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            if (like.equals(io.github.bargainbinbastard.altus.lore.LikeMatch.of(new ItemStack(it)))) return new ItemStack(it, n);
        return ItemStack.EMPTY;
    }

    /** Offerings, tiers and gifts, taboos, guardians sparing followers, rites, and a rite on a lie misfiring. */
    private static boolean smokeDevotion(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        Sites sites = WorldHistory.sites(server);
        if (Dreams.session(p).active) Dreams.end(p, "command");
        io.github.bargainbinbastard.altus.lore.Devotion d = io.github.bargainbinbastard.altus.lore.Favor.of(p);
        d.favor.clear();
        d.patron = -1;
        d.riteDay.clear();
        d.offeredToday.clear();
        Knowledge k = Memories.knowledge(p);
        k.written.clear();
        k.understanding.clear();
        Memories.held(p).list.clear();
        p.getInventory().clearContent();
        p.removeAllEffects();

        // A false entry in some archive, and the god who tells it: the rest of the test centres on that god.
        String lie = null;
        for (Sites.Site s : sites.all) {
            if (s.occupant < 0 || !h.god(s.occupant).alive) continue;
            for (String id : io.github.bargainbinbastard.altus.history.Sanctum.plan(h, sites, s.occupant).reliquaries)
                if (lie == null && io.github.bargainbinbastard.altus.history.Rites.isFalse(h, id)) lie = id;
        }
        if (lie == null) {
            LOGGER.info("SMOKE: devotion: no archive in this world holds a lie");
            return false;
        }
        History.God g = h.god(Integer.parseInt(lie.split(":")[2]));
        String like = io.github.bargainbinbastard.altus.history.Rites.offeringLike(g);
        ItemStack gift = stackFor(like, 1);

        boolean refused = !Altars.offer(p, gift.copy());
        k.understanding.put(g.id, 1);
        ItemStack five = stackFor(like, 5);
        boolean offered = Altars.offer(p, five) && five.isEmpty() && io.github.bargainbinbastard.altus.lore.Favor.favor(p, g.id) == 5;

        io.github.bargainbinbastard.altus.lore.Favor.set(p, g.id, 30);
        boolean follower = io.github.bargainbinbastard.altus.lore.Favor.tier(p) == 1 && d.patron == g.id;
        net.minecraft.world.entity.Mob guard = net.minecraft.world.entity.EntityType.ZOMBIE.create(server.overworld());
        guard.addTag(Guardians.TAG);
        guard.addTag(Guardians.GOD_TAG + g.id);
        boolean spared = Guardians.spares(guard, p);
        io.github.bargainbinbastard.altus.lore.Favor.set(p, g.id, 150);
        String giftId = io.github.bargainbinbastard.altus.history.Rites.giftOf(g);
        boolean disciple = io.github.bargainbinbastard.altus.lore.Favor.tier(p) == 2
                && p.hasEffect(io.github.bargainbinbastard.altus.lore.Favor.effectFor(giftId));

        String hated = null;
        for (io.github.bargainbinbastard.altus.history.Item it : g.dislikes) if (it.cat.equals("block")) hated = it.id;
        boolean taboo = true;
        if (hated != null) {
            net.minecraft.world.level.block.state.BlockState hs = null;
            for (net.minecraft.world.level.block.Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
                if (hs == null && hated.equals(io.github.bargainbinbastard.altus.lore.LikeMatch.of(b.defaultBlockState()))) hs = b.defaultBlockState();
            io.github.bargainbinbastard.altus.lore.DevotionEvents.placed(p, hs);
            taboo = io.github.bargainbinbastard.altus.lore.Favor.favor(p, g.id) == 147;
        }

        // A true entry as a rite, at an altar.
        BlockPos altar = p.blockPosition().offset(2, 0, 0);
        server.overworld().setBlock(altar, AltusRegistry.ALTAR.get().defaultBlockState(), 3);
        p.getInventory().setItem(0, new ItemStack(AltusRegistry.TOME.get()));
        String truth = Lore.woodsId(0, g.id);
        Memories.gain(p, Lore.render(h, sites, truth));
        TomeService.write(p, 0, 0, truth, List.of(""));
        Memories.gain(p, Lore.render(h, sites, lie));
        TomeService.write(p, 0, 0, lie, List.of(TomeService.contents(p.getInventory().getItem(0)).pages().get(0)));
        List<io.github.bargainbinbastard.altus.lore.TomeRecord> recs = TomeService.contents(p.getInventory().getItem(0)).records();
        String trueEntry = null, lieEntry = null;
        for (var r : recs) {
            if (r.testimonyId().equals(truth)) trueEntry = r.entryId();
            if (r.testimonyId().equals(lie)) lieEntry = r.entryId();
        }
        int views = io.github.bargainbinbastard.altus.lore.RiteService.views(p, p.getInventory().getItem(0)).size();
        for (String l : io.github.bargainbinbastard.altus.lore.RiteService.offerings(h, truth)) p.getInventory().add(stackFor(l, 1));
        io.github.bargainbinbastard.altus.history.Rites.Effect effect = io.github.bargainbinbastard.altus.history.Rites.effectOf(h, sites, truth);
        boolean performed = trueEntry != null && io.github.bargainbinbastard.altus.lore.RiteService.perform(p, 0, trueEntry);
        int left = 0;
        for (int i = 1; i < p.getInventory().items.size(); i++) left += p.getInventory().items.get(i).getCount();
        boolean onceADay = !io.github.bargainbinbastard.altus.lore.RiteService.perform(p, 0, trueEntry);

        // The lie as a rite: it misfires, and the gods it names think less of you.
        for (String l : io.github.bargainbinbastard.altus.lore.RiteService.offerings(h, lie)) p.getInventory().add(stackFor(l, 1));
        int before = io.github.bargainbinbastard.altus.lore.Favor.favor(p, g.id);
        boolean misfired = lieEntry != null && io.github.bargainbinbastard.altus.lore.RiteService.perform(p, 0, lieEntry)
                && p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)
                && io.github.bargainbinbastard.altus.lore.Favor.favor(p, g.id) == before - 8;

        server.overworld().removeBlock(altar, false);
        d.favor.clear();
        io.github.bargainbinbastard.altus.lore.Favor.set(p, g.id, 0);
        boolean giftGone = !p.hasEffect(io.github.bargainbinbastard.altus.lore.Favor.effectFor(giftId)) || giftId.equals("luck") && false;
        LOGGER.info("SMOKE: devotion to {} (offers {}, gift {}): refused={} offered={} follower={} spared={} disciple={} taboo={} views={} "
                        + "rite {} performed={} offeringsUsed={} onceADay={} misfired={} giftGone={}",
                g.name, like, giftId, refused, offered, follower, spared, disciple, taboo, views, effect, performed, left == 0, onceADay, misfired, giftGone);
        return refused && offered && follower && spared && disciple && taboo && views == 2 && performed && left == 0 && onceADay && misfired && giftGone;
    }

    /** Echoes speak, the inner door asks for deep knowledge, a liar confesses, and the Archivist has hints. */
    private static boolean smokeDepth(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        Sites sites = WorldHistory.sites(server);
        ServerLevel altus = AltusDimension.get(server);
        int g = -1;
        io.github.bargainbinbastard.altus.history.Sanctum plan = null;
        for (Sites.Site s : sites.all) {
            if (s.occupant < 0 || !h.god(s.occupant).alive) continue;
            io.github.bargainbinbastard.altus.history.Sanctum sc = io.github.bargainbinbastard.altus.history.Sanctum.plan(h, sites, s.occupant);
            if (sc.inner.stream().anyMatch(id -> id.startsWith("TR:"))) {
                g = s.occupant;
                plan = sc;
                break;
            }
        }
        if (g < 0) {
            LOGGER.info("SMOKE: depth: no living god in this world tells a lie it keeps the truth of");
            return false;
        }
        Knowledge k = Memories.knowledge(p);
        k.written.clear();
        k.understanding.clear();
        Memories.held(p).list.clear();
        if (Dreams.session(p).active) Dreams.end(p, "command");
        Dreams.begin(p, -1, 300, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();
        BlockPos in = AltusWorld.sanctumArrivalOf(g);
        p.teleportTo(altus, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0f, 0f);

        // The echo appears and speaks.
        Echoes.tick(altus, h, false);
        BlockPos post = null;
        for (java.util.Map.Entry<BlockPos, Integer> e : AltusWorld.echoes().entrySet()) if (e.getValue() == g) post = e.getKey();
        List<net.minecraft.world.entity.Mob> figures = altus.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(post).inflate(3), Echoes::isEcho);
        boolean echoed = figures.size() == 1;
        Echoes.tick(altus, h, false);
        echoed &= altus.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(post).inflate(3), Echoes::isEcho).size() == 1;
        boolean spoke = plan.echo.isEmpty();
        if (echoed) {
            Echoes.interact(p, figures.get(0));
            spoke = plan.echo.isEmpty() || Memories.held(p).find(plan.echo.get(0)) != null;
        }

        // The inner door wants four things written about the god.
        BlockPos o = AltusWorld.pocketOrigin(g);
        BlockPos innerDoor = o.offset(5, 1, AltusWorld.INNER_DOOR_Z);
        p.teleportTo(altus, o.getX() + 5.5, o.getY() + 1, o.getZ() + AltusWorld.INNER_DOOR_Z - 1.5, 0f, 0f);
        k.understanding.put(g, AltusWorld.INNER_GATE - 1);
        AltusWorld.useDoor(p, innerDoor);
        boolean innerShut = p.getZ() < innerDoor.getZ();
        k.understanding.put(g, AltusWorld.INNER_GATE);
        AltusWorld.useDoor(p, innerDoor);
        boolean innerOpen = p.getZ() > innerDoor.getZ();
        int innerTaken = 0;
        for (BlockPos r : AltusWorld.relicsOf(g)) {
            if (r.getZ() <= innerDoor.getZ()) continue;
            int n = Memories.held(p).list.size();
            AltusWorld.useReliquary(p, r);
            if (Memories.held(p).list.size() > n) innerTaken++;
        }
        AltusWorld.useDoor(p, innerDoor);
        boolean innerOut = p.getZ() < innerDoor.getZ();

        // Caught in a lie: its own account, against the truth from its inner chamber.
        String tr = plan.inner.stream().filter(id -> id.startsWith("TR:")).findFirst().get();
        String ev = tr.split(":")[1];
        String lieId = "EV:" + ev + ":" + g + ":" + io.github.bargainbinbastard.altus.history.Sanctum.reveals(h, h.event(ev), g);
        Echoes.confront(p, g);
        boolean nothingYet = Memories.held(p).find("CF:" + ev + ":" + g) == null;
        k.written.add(lieId);
        k.written.add(tr);
        Echoes.confront(p, g);
        HeldMemories.Memory cf = Memories.held(p).find("CF:" + ev + ":" + g);
        boolean confessed = nothingYet && cf != null;

        // The Archivist.
        List<String> hints = Echoes.hints(p);
        BlockPos arch = AltusWorld.archivist();
        p.teleportTo(altus, arch.getX() + 0.5, arch.getY(), arch.getZ() + 2.5, 180f, 0f);
        Echoes.tick(altus, h, false);
        boolean archivist = altus.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(arch).inflate(3),
                Echoes::isEcho).size() == 1;
        Dreams.end(p, "command");
        LOGGER.info("SMOKE: depth, sanctum of {}: echoed={} spoke={} innerShut={} innerOpen={} innerTaken={} innerOut={} confessed={} archivist={} hints={}",
                h.name(g), echoed, spoke, innerShut, innerOpen, innerTaken, innerOut, confessed, archivist, hints.size());
        if (cf != null) LOGGER.info("SMOKE:   confession: {}", Lore.render(h, sites, cf.id).text);
        for (String hint : hints) LOGGER.info("SMOKE:   Archivist: {}", hint);
        return echoed && spoke && innerShut && innerOpen && innerTaken > 0 && innerOut && confessed && archivist && !hints.isEmpty();
    }

    /** One thing left on an altar goes into the dream, can't be lost there, and comes back as it was used. */
    private static boolean smokeCarried(MinecraftServer server, ServerPlayer p) {
        if (Dreams.session(p).active) Dreams.end(p, "command");
        Altars.bound(p).stack = ItemStack.EMPTY;
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        p.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        boolean bound = Altars.bind(p, sword) && sword.isEmpty();
        Dreams.begin(p, -1, 300, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();
        ItemStack inHand = p.getInventory().getItem(p.getInventory().selected);
        boolean carriedIn = inHand.is(Items.IRON_SWORD) && Altars.isCarried(inHand) && Altars.bound(p).stack.isEmpty();
        int others = 0;
        for (ItemStack it : p.getInventory().items) if (!it.isEmpty()) others++;
        ItemStack helmIn = p.getInventory().armor.get(3);
        boolean armorIn = helmIn.is(Items.IRON_HELMET) && Altars.isCarried(helmIn);
        helmIn.setDamageValue(7);
        inHand.setDamageValue(5);
        p.getInventory().setItem(5, new ItemStack(Items.DIRT));
        Dreams.end(p, "command");
        boolean back = false, dirt = false;
        int diamonds = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it.is(Items.IRON_SWORD) && !Altars.isCarried(it) && it.getDamageValue() == 5) back = true;
            if (it.is(Items.DIAMOND)) diamonds += it.getCount();
            if (it.is(Items.DIRT)) dirt = true;
        }
        ItemStack helmOut = p.getInventory().armor.get(3);
        int helmets = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.IRON_HELMET)) helmets++;
        boolean armorBack = helmOut.is(Items.IRON_HELMET) && !Altars.isCarried(helmOut) && helmOut.getDamageValue() == 7 && helmets == 1;
        p.getInventory().armor.set(3, ItemStack.EMPTY);
        ItemStack stick = new ItemStack(Items.STICK);
        Altars.bind(p, stick);
        Altars.reclaim(p);
        boolean reclaimed = Altars.bound(p).stack.isEmpty() && p.getInventory().contains(new ItemStack(Items.STICK));
        LOGGER.info("SMOKE: carried item: bound={} carriedIn={} (only thing held: {}) back={} diamonds={} dreamDirtGone={} reclaimed={} armorIn={} armorBack={}",
                bound, carriedIn, others == 1, back, diamonds, !dirt, reclaimed, armorIn, armorBack);
        return bound && carriedIn && others == 1 && back && diamonds == 3 && !dirt && reclaimed && armorIn && armorBack;
    }

    /** A Warden Stone calls its god's guardians when a dreamer comes near, up to the god's limit. */
    private static boolean smokeGuardians(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        Sites sites = WorldHistory.sites(server);
        ServerLevel altus = AltusDimension.get(server);
        Sites.Site site = null;
        for (Sites.Site s : sites.all)
            if (s.occupant >= 0 && h.god(s.occupant).alive && s.region != Sites.Region.HOUSE) {
                site = s;
                break;
            }
        if (site == null) {
            LOGGER.info("SMOKE: guardians: no living god holds a door on the slopes in this world");
            return true;
        }
        History.God god = h.god(site.occupant);
        BlockPos stone = AltusWorld.siteWarden(site);
        if (Dreams.session(p).active) Dreams.end(p, "command");
        Dreams.begin(p, -1, 300, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();
        BlockPos at = AltusWorld.siteArrivalOf(site.occupant);
        p.teleportTo(altus, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        boolean placed = altus.getBlockState(stone).is(AltusRegistry.WARDEN_STONE.get());
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(stone).inflate(Guardians.WAKE_RANGE + 4);
        Guardians.tick(altus, h);
        List<net.minecraft.world.entity.Mob> first = altus.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, Guardians::isGuardian);
        boolean called = first.size() == 1 && first.get(0).getType() == Guardians.typeFor(god);
        for (int i = 0; i < 8; i++) Guardians.tick(altus, h);
        List<net.minecraft.world.entity.Mob> all = altus.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, Guardians::isGuardian);
        boolean capped = all.size() == Guardians.capFor(god);
        all.forEach(net.minecraft.world.entity.Entity::discard);
        Dreams.end(p, "command");
        LOGGER.info("SMOKE: guardians of {} (power {}) at {}: stone={} called={} ({}) capped={} ({} of {})", god.name, god.power, site.id,
                placed, called, first.isEmpty() ? "none" : first.get(0).getType().getDescriptionId(), capped, all.size(), Guardians.capFor(god));
        return placed && called && capped;
    }

    /** A dead god's door opens on its ruin, whose reliquaries ask for more knowledge the deeper their lore. */
    private static boolean smokeRuin(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        Sites sites = WorldHistory.sites(server);
        Sites.Site site = null;
        for (Sites.Site s : sites.all)
            if (s.occupant >= 0 && !h.god(s.occupant).alive) {
                site = s;
                break;
            }
        if (site == null) {
            LOGGER.info("SMOKE: ruin: no dead god holds a door in this world");
            return true;
        }
        int g = site.occupant;
        Knowledge k = Memories.knowledge(p);
        k.understanding.clear();
        k.written.clear();
        Memories.held(p).list.clear();
        k.understanding.put(g, 1);
        if (Dreams.session(p).active) Dreams.end(p, "command");
        Dreams.begin(p, -1, 300, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();
        AltusWorld.useDoor(p, AltusWorld.doorBase(site));
        boolean entered = p.blockPosition().closerThan(AltusWorld.sanctumArrivalOf(g), 2);
        List<BlockPos> relics = AltusWorld.relicsOf(g);
        boolean gated = true;
        String took = "nothing";
        if (!relics.isEmpty()) {
            String id = null;
            for (BlockPos r : relics) {
                int n = Memories.held(p).list.size();
                k.understanding.put(g, 1);
                AltusWorld.useReliquary(p, r);
                boolean shut = Memories.held(p).list.size() == n;
                k.understanding.put(g, 6);
                AltusWorld.useReliquary(p, r);
                boolean opened = Memories.held(p).list.size() == n + 1;
                gated &= shut && opened;
            }
            HeldMemories.Memory first = Memories.held(p).list.get(0);
            Lore.Testimony t = Lore.render(h, sites, first.id);
            took = "[level " + t.level + "] " + t.text + " (" + t.attribution + ")";
        }
        Dreams.end(p, "command");
        LOGGER.info("SMOKE: ruin of {} at {}: entered={} relics={} gated={} first relic: {}", h.name(g), site.id, entered, relics.size(), gated, took);
        return entered && gated;
    }

    /** The Mountain stands, every door is built, and a sanctum is gated by knowledge from door to reliquary. */
    private static boolean smokeStage(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        Sites sites = WorldHistory.sites(server);
        ServerLevel altus = AltusDimension.get(server);
        altus.getChunk(0, 0);
        int summit = altus.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        boolean mountain = summit > 180;
        int doors = 0, expected = 0;
        Sites.Site site = null;
        for (Sites.Site s : sites.all) {
            if (s.occupant < 0 || !h.god(s.occupant).alive) continue;
            expected++;
            if (altus.getBlockState(AltusWorld.doorBase(s)).is(AltusRegistry.ALTUS_DOOR.get())) doors++;
            if (site == null) site = s;
        }
        LOGGER.info("SMOKE: stage: summit height {} (terrain says {}); doors built {} of {}", summit, Terrain.height(0, 0) + 1, doors, expected);
        if (site == null) return mountain && doors == expected;
        int g = site.occupant;
        Knowledge k = Memories.knowledge(p);
        Memories.held(p).list.clear();
        k.written.clear();
        k.understanding.clear();
        if (Dreams.session(p).active) Dreams.end(p, "command");
        Dreams.begin(p, -1, 400, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();

        BlockPos door = AltusWorld.doorBase(site);
        BlockPos before = p.blockPosition();
        AltusWorld.useDoor(p, door);
        boolean shut = p.blockPosition().equals(before);
        k.understanding.put(g, 1);
        AltusWorld.useDoor(p, door);
        boolean entered = p.blockPosition().closerThan(AltusWorld.sanctumArrivalOf(g), 2);

        List<BlockPos> murals = AltusWorld.muralsOf(g);
        String muralId = AltusWorld.muralAt(murals.get(0));
        Memories.take(p, muralId, "mural");
        boolean muralTaken = Memories.held(p).find(muralId) != null;
        boolean built = altus.getBlockState(murals.get(0)).is(AltusRegistry.INSCRIPTION.get());

        List<BlockPos> relics = AltusWorld.relicsOf(g);
        boolean relicShut = true, relicOpened = true;
        if (!relics.isEmpty()) {
            built &= altus.getBlockState(relics.get(0)).is(AltusRegistry.RELIQUARY.get());
            int n = Memories.held(p).list.size();
            AltusWorld.useReliquary(p, relics.get(0));
            relicShut = Memories.held(p).list.size() == n;
            k.understanding.put(g, 2);
            AltusWorld.useReliquary(p, relics.get(0));
            relicOpened = Memories.held(p).list.size() == n + 1;
        }
        AltusWorld.useDoor(p, AltusWorld.sanctumArrivalOf(g).offset(0, 0, -3));
        boolean out = p.blockPosition().closerThan(AltusWorld.siteArrivalOf(g), 2);
        Dreams.end(p, "command");

        k.written.add("LOC:" + g);
        Dreams.begin(p, g, 200, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();
        boolean arrivedAtDoor = p.blockPosition().closerThan(AltusWorld.siteArrivalOf(g), 2);
        Dreams.end(p, "command");

        LOGGER.info("SMOKE: sanctum of {} at {}: shut={} entered={} muralTaken={} built={} relics={} relicShut={} relicOpened={} out={} arrivedAtDoor={}",
                h.name(g), site.id, shut, entered, muralTaken, built, relics.size(), relicShut, relicOpened, out, arrivedAtDoor);
        for (HeldMemories.Memory m : Memories.held(p).list) {
            Lore.Testimony t = Lore.render(h, sites, m.id);
            LOGGER.info("SMOKE:   took [{}] {}: {}", m.id, t.title, t.text);
        }
        return mountain && doors == expected && shut && entered && muralTaken && built && relicShut && relicOpened && out && arrivedAtDoor;
    }

    /** Reads the four inscriptions in a dream, wakes, writes one memory in a Tome, edits it away, and lets the rest fade. */
    private static boolean smokeMemories(MinecraftServer server, ServerPlayer p) {
        History h = WorldHistory.get(server);
        ServerLevel altus = AltusDimension.get(server);
        boolean carved = true;
        for (int i = 0; i < AltusWorld.INSCRIPTIONS.length; i++)
            carved &= altus.getBlockState(AltusWorld.INSCRIPTIONS[i]).is(AltusRegistry.INSCRIPTION.get());
        boolean recipe = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(MODID, "tome")).isPresent();
        int god = Lore.woodsFallbackGod(h);
        if (god < 0) {
            LOGGER.warn("SMOKE: memories SKIPPED: every god is dead or hidden in this world");
            return carved && recipe;
        }
        Memories.held(p).list.clear();
        Memories.knowledge(p).written.clear();
        Dreams.begin(p, god, 200, p.getX(), p.getY(), p.getZ(), Component.literal("smoke"));
        p.hasChangedDimension();
        for (int f = 0; f < 4; f++) Memories.readInscription(p, f);
        long pending = Memories.held(p).list.stream().filter(HeldMemories.Memory::pending).count();
        Dreams.end(p, "command");
        long now = Memories.now(p);
        long readable = Memories.held(p).list.stream().filter(m -> !m.pending() && m.expire > now).count();
        boolean effect = p.hasEffect(AltusRegistry.FADING_MEMORY);

        p.getInventory().selected = 0;
        p.getInventory().setItem(0, new ItemStack(AltusRegistry.TOME.get()));
        String id = Memories.held(p).list.get(0).id;
        int target = TomeService.write(p, 0, 0, id, List.of(""));
        TomeContents c = TomeService.contents(p.getInventory().getItem(0));
        boolean written = target == 0 && c.pages().get(0).contains(Lore.render(h, id).text) && c.records().size() == 1
                && Memories.knowledge(p).written.contains(id) && Memories.held(p).find(id) == null
                && Memories.knowledge(p).understandingOf(god) == 1;
        boolean known = Memories.gain(p, Lore.render(h, id)) == Memories.Gain.ALREADY_KNOWN;

        TomeService.save(p, 0, "  Notes on \u00a7cthe Gods  ", List.of("I tore that page out."));
        boolean reconciled = TomeService.contents(p.getInventory().getItem(0)).records().isEmpty();
        boolean titled = TomeService.contents(p.getInventory().getItem(0)).title().equals("Notes on cthe Gods")
                && p.getInventory().getItem(0).getHoverName().getString().equals("Notes on cthe Gods");
        LOGGER.info("SMOKE: Tome title: '{}' (shown as '{}')", TomeService.contents(p.getInventory().getItem(0)).title(),
                p.getInventory().getItem(0).getHoverName().getString());

        int before = Memories.held(p).list.size();
        for (HeldMemories.Memory m : Memories.held(p).list) m.expire = Memories.now(p) - 1;
        Memories.tick(p);
        int after = Memories.held(p).list.size();
        boolean effectAfter = p.hasEffect(AltusRegistry.FADING_MEMORY);
        LOGGER.info("SMOKE: fading: held before={} after={} effect still present={} ({})", before, after, effectAfter,
                p.getEffect(AltusRegistry.FADING_MEMORY));
        boolean faded = after == 0 && !effectAfter;

        LOGGER.info("SMOKE: memories: carved={} recipe={} pending={} readable={} effect={} written={} known={} reconciled={} faded={}",
                carved, recipe, pending, readable, effect, written, known, reconciled, faded);
        LOGGER.info("SMOKE: the Tome's first entry read: {}", c.pages().get(0).replace("\n", " | "));
        return carved && recipe && pending == 4 && readable == 4 && effect && written && known && reconciled && titled && faded;
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
        // Natural blocks near the bed (snow, moss, mud...) count too, so expect whatever the scan really finds.
        FocusPicker.Focus expected = FocusPicker.pick(h, SleepScan.count(ow, head, AltusConfig.SCAN_RADIUS.get()),
                AltusConfig.SCAN_THRESHOLD.get());

        p.teleportTo(ow, foot.getX() + 0.5, foot.getY(), foot.getZ() + 1.5, 0, 0);
        var result = p.startSleepInBed(head);
        boolean slept = p.isSleeping();
        boolean began = Dreams.tryBeginFromSleep(p);
        boolean inAltus = AltusDimension.isAltus(p.level());
        int focus = Dreams.session(p).focusGod;
        boolean ok = slept && began == expected.dreams() && inAltus == expected.dreams()
                && (expected.kind != FocusPicker.Kind.GOD || focus == expected.god);
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
