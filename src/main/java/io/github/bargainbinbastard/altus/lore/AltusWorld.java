package io.github.bargainbinbastard.altus.lore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.dream.AltusDimension;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Sanctum;
import io.github.bargainbinbastard.altus.history.Sites;
import io.github.bargainbinbastard.altus.history.Terrain;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fixed stage of the Altus and what the history puts on it: the Clearing, the road, the House,
 * the eleven door sites on the Mountain, and each living god's sanctum.
 *
 * <p>The stage is built into the world once (tracked by {@link AltusWorldData}). Where every door,
 * mural and reliquary is, and what it holds, is recomputed from the history on each server start.
 */
public final class AltusWorld {
    private AltusWorld() {}

    public static final int STAGE_VERSION = 3;
    public static final int GROUND_Y = Terrain.BASE + 1;
    public static final BlockPos CLEARING = new BlockPos(Terrain.CLEARING_X, GROUND_Y, Terrain.CLEARING_Z);
    /** Where the four Woods inscriptions stand, by facet. */
    public static final BlockPos[] INSCRIPTIONS = {CLEARING.offset(5, 0, 0), CLEARING.offset(0, 0, 5), CLEARING.offset(-5, 0, 0),
            CLEARING.offset(0, 0, -5)};
    public static final int CLEARING_RADIUS = 9;

    public enum DoorKind { ENTER, EXIT, SEALED }

    public record Door(DoorKind kind, int god) {}

    private static final Map<BlockPos, Door> DOORS = new HashMap<>();
    private static final Map<BlockPos, String> MURALS = new HashMap<>();
    private static final Map<BlockPos, String> RELICS = new HashMap<>();
    private static final Map<BlockPos, Integer> RELIC_KEEPER = new HashMap<>();
    private static final Map<BlockPos, Integer> MURAL_KEEPER = new HashMap<>();
    private static final Map<Integer, BlockPos> SITE_ARRIVAL = new HashMap<>();
    private static final Map<Integer, BlockPos> SANCTUM_ARRIVAL = new HashMap<>();
    private static final Map<BlockPos, Integer> WARDENS = new HashMap<>();

    // ---------------------------------------------------------------- geometry

    private static int[] outward(Sites.Site s) {
        return Math.abs(s.x) >= Math.abs(s.z) ? new int[] {Integer.signum(s.x), 0} : new int[] {0, Integer.signum(s.z)};
    }

    private static int[] perp(int[] o) {
        return new int[] {o[1], o[0]};
    }

    static final int HX0 = -7, HX1 = 7, HZ0 = Sites.HOUSE_Z - 6, HZ1 = Sites.HOUSE_Z + 6;

    static int houseFloorY() {
        return Terrain.height(Sites.HOUSE_X, Sites.HOUSE_Z) + 1;
    }

    /** The lower block of a site's door. */
    public static BlockPos doorBase(Sites.Site s) {
        if (s.region == Sites.Region.HOUSE) {
            int yH = houseFloorY();
            int y = switch (s.id) {
                case "attic" -> yH + 1;
                case "cellar" -> yH - 1;
                default -> yH;
            };
            return new BlockPos(s.x, y, HZ0);
        }
        int[] o = outward(s);
        return new BlockPos(s.x - 3 * o[0], Terrain.height(s.x, s.z) + 1, s.z - 3 * o[1]);
    }

    /** Where a dreamer stands in front of a site's door. */
    static BlockPos siteArrival(Sites.Site s) {
        BlockPos d = doorBase(s);
        if (s.region == Sites.Region.HOUSE) return new BlockPos(d.getX(), d.getY(), HZ0 + 2);
        int[] o = outward(s);
        return new BlockPos(s.x + o[0], d.getY(), s.z + o[1]);
    }

    static BlockPos pocketOrigin(int god) {
        return new BlockPos(30000 + 128 * god, 120, 0);
    }

    private static final int[][] MURAL_SPOTS = {{1, 1, 17}, {5, 1, 21}, {9, 1, 17}, {1, 1, 13}, {9, 1, 13}};
    private static final int[][] RELIC_SPOTS = {{1, 1, 31}, {4, 1, 35}, {6, 1, 35}, {9, 1, 31}, {5, 1, 28}};

    // ---------------------------------------------------------------- setup

    public static void prepare(MinecraftServer server) {
        ServerLevel altus = AltusDimension.get(server);
        if (altus == null) return;
        History h = WorldHistory.get(server);
        Sites sites = WorldHistory.sites(server);
        layout(h, sites);
        AltusWorldData data = AltusWorldData.get(altus);
        if (data.stageVersion < STAGE_VERSION) {
            long t = System.nanoTime();
            build(altus, h, sites);
            data.stageVersion = STAGE_VERSION;
            data.setDirty();
            AltusMod.LOGGER.info("The Altus: built the stage (version {}) in {} ms.", STAGE_VERSION, (System.nanoTime() - t) / 1_000_000);
        }
    }

    /** Works out where everything is, without touching the world. */
    static void layout(History h, Sites sites) {
        DOORS.clear();
        MURALS.clear();
        RELICS.clear();
        RELIC_KEEPER.clear();
        MURAL_KEEPER.clear();
        SITE_ARRIVAL.clear();
        SANCTUM_ARRIVAL.clear();
        WARDENS.clear();
        for (Sites.Site s : sites.all) {
            if (s.occupant < 0) continue;
            int g = s.occupant;
            if (s.region != Sites.Region.HOUSE) WARDENS.put(siteWarden(s), g);
            BlockPos d = doorBase(s);
            DOORS.put(d, new Door(DoorKind.ENTER, g));
            DOORS.put(d.above(), new Door(DoorKind.ENTER, g));
            SITE_ARRIVAL.put(g, siteArrival(s));
            BlockPos o = pocketOrigin(g);
            DOORS.put(o.offset(5, 1, 0), new Door(DoorKind.EXIT, g));
            DOORS.put(o.offset(5, 2, 0), new Door(DoorKind.EXIT, g));
            DOORS.put(o.offset(5, 1, 38), new Door(DoorKind.SEALED, g));
            DOORS.put(o.offset(5, 2, 38), new Door(DoorKind.SEALED, g));
            SANCTUM_ARRIVAL.put(g, o.offset(5, 1, 3));
            WARDENS.put(o.offset(8, 1, 7), g);
            WARDENS.put(o.offset(1, 1, 26), g);
            Sanctum plan = planFor(h, sites, g);
            for (int i = 0; i < plan.murals.size() && i < MURAL_SPOTS.length; i++) {
                BlockPos p = o.offset(MURAL_SPOTS[i][0], MURAL_SPOTS[i][1], MURAL_SPOTS[i][2]);
                MURALS.put(p, plan.murals.get(i));
                MURAL_KEEPER.put(p, g);
            }
            for (int i = 0; i < plan.reliquaries.size() && i < RELIC_SPOTS.length; i++) {
                BlockPos p = o.offset(RELIC_SPOTS[i][0], RELIC_SPOTS[i][1], RELIC_SPOTS[i][2]);
                RELICS.put(p, plan.reliquaries.get(i));
                RELIC_KEEPER.put(p, g);
            }
        }
    }

    static Sanctum planFor(History h, Sites sites, int god) {
        return h.god(god).alive ? Sanctum.plan(h, sites, god) : Sanctum.planRuin(h, sites, god);
    }

    /** A Warden Stone at the outer corner of a mountain site's platform. */
    static BlockPos siteWarden(Sites.Site s) {
        int[] o = outward(s), q = perp(o);
        return new BlockPos(s.x + 3 * o[0] + 3 * q[0], Terrain.height(s.x, s.z) + 1, s.z + 3 * o[1] + 3 * q[1]);
    }

    public static Map<BlockPos, Integer> wardens() {
        return WARDENS;
    }

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        set(l, new BlockPos(x, y, z), s);
    }

    static void build(ServerLevel l, History h, Sites sites) {
        buildClearing(l);
        buildRoad(l);
        buildHouse(l);
        for (Sites.Site s : sites.all) buildSite(l, h, s);
        for (Sites.Site s : sites.all)
            if (s.occupant >= 0) buildSanctum(l, h, sites, s.occupant);
    }

    static void buildClearing(ServerLevel l) {
        int r = CLEARING_RADIUS;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                for (int dy = 0; dy < 32; dy++) {
                    BlockPos p = CLEARING.offset(x, dy, z);
                    if (!l.getBlockState(p).isAir()) set(l, p, Blocks.AIR.defaultBlockState());
                }
        for (int i = 0; i < INSCRIPTIONS.length; i++)
            l.setBlock(INSCRIPTIONS[i], AltusRegistry.INSCRIPTION.get().defaultBlockState().setValue(InscriptionBlock.FACET, i), Block.UPDATE_ALL);
    }

    /** A dirt road from the Clearing to the House, cut through the trees. */
    static void buildRoad(ServerLevel l) {
        for (int z = Terrain.CLEARING_Z - CLEARING_RADIUS; z > HZ1 + 2; z--)
            for (int x = -1; x <= 1; x++) {
                int y = Terrain.height(x, z);
                if (y < 0) continue;
                BlockState top = l.getBlockState(new BlockPos(x, y, z));
                if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT)) set(l, x, y, z, Blocks.DIRT_PATH.defaultBlockState());
                for (int dy = 1; dy <= 12; dy++) {
                    BlockPos p = new BlockPos(x, y + dy, z);
                    if (!l.getBlockState(p).isAir()) set(l, p, Blocks.AIR.defaultBlockState());
                }
            }
    }

    static void buildHouse(ServerLevel l) {
        int yH = houseFloorY();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState(), planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = HX0 - 2; x <= HX1 + 2; x++)
            for (int z = HZ0 - 2; z <= HZ1 + 2; z++) {
                set(l, x, yH - 1, z, bricks);
                int ground = Terrain.height(x, z);
                for (int y = yH - 2; y >= ground && y > yH - 12; y--) set(l, x, y, z, Blocks.STONE.defaultBlockState());
                for (int y = yH; y <= yH + 10; y++) set(l, x, y, z, air);
            }
        for (int x = HX0; x <= HX1; x++)
            for (int z = HZ0; z <= HZ1; z++) {
                boolean edgeX = x == HX0 || x == HX1, edgeZ = z == HZ0 || z == HZ1;
                if (edgeX || edgeZ)
                    for (int y = yH - 2; y <= yH + 4; y++) set(l, x, y, z, edgeX && edgeZ ? log : bricks);
                else set(l, x, yH - 1, z, planks);
                set(l, x, yH + 5, z, planks);
            }
        for (int x = -1; x <= 1; x++)
            for (int y = yH; y <= yH + 2; y++) set(l, x, y, HZ1, air);
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        for (int z : new int[] {HZ0 + 4, HZ0 + 8}) {
            set(l, HX0, yH + 2, z, pane);
            set(l, HX1, yH + 2, z, pane);
        }
        set(l, -4, yH + 2, HZ1, pane);
        set(l, 4, yH + 2, HZ1, pane);
        // The Hearth: fires either side of its door.
        BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false);
        set(l, -2, yH, HZ0 + 1, fire);
        set(l, 2, yH, HZ0 + 1, fire);
        // The Cellar Stair: a pit a step down.
        for (int x = -5; x <= -3; x++)
            for (int z = HZ0 + 1; z <= HZ0 + 2; z++) {
                set(l, x, yH - 1, z, air);
                set(l, x, yH - 2, z, bricks);
            }
        // The Attic: a raised floor a step up.
        for (int x = 3; x <= 5; x++)
            for (int z = HZ0 + 1; z <= HZ0 + 2; z++) set(l, x, yH, z, planks);
        set(l, 0, yH + 4, Sites.HOUSE_Z, Blocks.LANTERN.defaultBlockState());
    }

    static void buildSite(ServerLevel l, History h, Sites.Site s) {
        boolean living = s.occupant >= 0 && h.god(s.occupant).alive;
        boolean ruin = s.occupant >= 0 && !living;
        Palettes.Palette pal = living ? Palettes.of(h.god(s.occupant)) : null;
        BlockState wall = living ? pal.wall() : ruin ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        BlockState accent = living ? pal.accent() : ruin ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        BlockPos d = doorBase(s);

        if (s.region == Sites.Region.HOUSE) {
            int x = d.getX(), z = d.getZ();
            for (int k = -1; k <= 1; k++)
                for (int y = d.getY(); y <= d.getY() + 2; y++) set(l, x + k, y, z, accent);
            set(l, x, d.getY(), z, living || ruin ? door() : wall);
            set(l, x, d.getY() + 1, z, living || ruin ? door() : wall);
            if (ruin) set(l, x + 1, d.getY(), z + 1, Blocks.COBWEB.defaultBlockState());
            return;
        }

        int[] o = outward(s), q = perp(o);
        int hgt = Terrain.height(s.x, s.z);
        BlockState floor = living ? pal.floor() : ruin ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                int x = s.x + a, z = s.z + b;
                set(l, x, hgt, z, floor);
                for (int y = hgt - 1; y > hgt - 8 && l.getBlockState(new BlockPos(x, y, z)).isAir(); y--)
                    set(l, x, y, z, Blocks.STONE.defaultBlockState());
                for (int y = hgt + 1; y <= hgt + 8; y++) set(l, x, y, z, Blocks.AIR.defaultBlockState());
            }
        for (int k = -2; k <= 2; k++)
            for (int y = hgt + 1; y <= hgt + 5; y++) {
                int fx = s.x - 3 * o[0] + k * q[0], fz = s.z - 3 * o[1] + k * q[1];
                set(l, fx, y, fz, wall);
                set(l, fx - o[0], y, fz - o[1], Blocks.STONE.defaultBlockState());
            }
        for (int k = -1; k <= 1; k++)
            for (int y = hgt + 1; y <= hgt + 3; y++) set(l, d.getX() + k * q[0], y, d.getZ() + k * q[1], accent);
        set(l, d, living || ruin ? door() : wall);
        set(l, d.above(), living || ruin ? door() : wall);
        if (ruin) set(l, s.x + 2 * q[0], hgt + 1, s.z + 2 * q[1], Blocks.COBWEB.defaultBlockState());
        if (living || ruin) set(l, siteWarden(s), warden());
    }

    private static BlockState door() {
        return AltusRegistry.ALTUS_DOOR.get().defaultBlockState();
    }

    private static BlockState warden() {
        return AltusRegistry.WARDEN_STONE.get().defaultBlockState();
    }

    static void buildSanctum(ServerLevel l, History h, Sites sites, int god) {
        boolean ruin = !h.god(god).alive;
        Palettes.Palette pal = Palettes.of(h.god(god));
        BlockPos o = pocketOrigin(god);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -2; x <= 12; x++)
            for (int y = 0; y <= 6; y++)
                for (int z = 0; z <= 38; z++) set(l, o.offset(x, y, z), pal.wall());
        int[][] rooms = {{1, 9, 1, 9}, {-1, 11, 11, 23}, {-1, 11, 25, 37}};
        for (int[] r : rooms)
            for (int x = r[0]; x <= r[1]; x++)
                for (int z = r[2]; z <= r[3]; z++) {
                    set(l, o.offset(x, 0, z), pal.floor());
                    for (int y = 1; y <= 5; y++) set(l, o.offset(x, y, z), air);
                }
        for (int x = 4; x <= 6; x++)
            for (int y = 1; y <= 3; y++) {
                set(l, o.offset(x, y, 10), air);
                set(l, o.offset(x, y, 24), air);
            }
        for (int[] c : new int[][] {{-1, 11}, {11, 11}, {-1, 23}, {11, 23}, {-1, 25}, {11, 25}, {-1, 37}, {11, 37}})
            for (int y = 1; y <= 5; y++) set(l, o.offset(c[0], y, c[1]), pal.accent());
        for (int[] c : new int[][] {{5, 5}, {1, 13}, {9, 13}, {1, 21}, {9, 21}, {5, 17}, {1, 27}, {9, 27}, {1, 35}, {9, 35}, {5, 31}})
            set(l, o.offset(c[0], 6, c[1]), pal.light());
        if (pal.decor() != null)
            for (int[] c : new int[][] {{2, 4}, {8, 4}, {0, 12}, {10, 22}, {0, 36}, {10, 36}}) set(l, o.offset(c[0], 1, c[1]), pal.decor());
        for (int y = 1; y <= 2; y++) {
            set(l, o.offset(5, y, 0), door());
            set(l, o.offset(5, y, 38), door());
        }
        if (ruin) decay(l, o, god);
        set(l, o.offset(8, 1, 7), warden());
        set(l, o.offset(1, 1, 26), warden());
        Sanctum plan = planFor(h, sites, god);
        for (int i = 0; i < plan.murals.size() && i < MURAL_SPOTS.length; i++)
            set(l, o.offset(MURAL_SPOTS[i][0], MURAL_SPOTS[i][1], MURAL_SPOTS[i][2]), AltusRegistry.INSCRIPTION.get().defaultBlockState());
        for (int i = 0; i < plan.reliquaries.size() && i < RELIC_SPOTS.length; i++)
            set(l, o.offset(RELIC_SPOTS[i][0], RELIC_SPOTS[i][1], RELIC_SPOTS[i][2]), AltusRegistry.RELIQUARY.get().defaultBlockState());
    }

    /** A dead god's sanctum crumbles: cracked and fallen walls, cobwebs, its lights mostly out. */
    static void decay(ServerLevel l, BlockPos o, int god) {
        java.util.Random r = new java.util.Random(31L * god + 7);
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        for (int x = -2; x <= 12; x++)
            for (int y = 0; y <= 6; y++)
                for (int z = 0; z <= 38; z++) {
                    BlockPos p = o.offset(x, y, z);
                    BlockState st = l.getBlockState(p);
                    if (st.isAir()) {
                        if (y >= 1 && y <= 5 && r.nextInt(30) == 0) set(l, p, Blocks.COBWEB.defaultBlockState());
                        continue;
                    }
                    if (st.is(AltusRegistry.ALTUS_DOOR.get())) continue;
                    boolean shell = x == -2 || x == 12 || z == 0 || z == 38 || y == 6;
                    int roll = r.nextInt(100);
                    if (roll < 22) set(l, p, cracked);
                    else if (roll < 32) set(l, p, mossy);
                    else if (roll < 36 && !shell && y > 0) set(l, p, Blocks.AIR.defaultBlockState());
                    else if (y == 6 && st.getLightEmission() > 0 && roll < 80) set(l, p, cracked);
                }
    }

    // ---------------------------------------------------------------- interaction

    public static String muralAt(BlockPos pos) {
        return MURALS.get(pos);
    }

    public static Door doorAt(BlockPos pos) {
        return DOORS.get(pos);
    }

    public static BlockPos siteArrivalOf(int god) {
        return SITE_ARRIVAL.get(god);
    }

    public static BlockPos sanctumArrivalOf(int god) {
        return SANCTUM_ARRIVAL.get(god);
    }

    public static List<BlockPos> muralsOf(int god) {
        return MURAL_KEEPER.entrySet().stream().filter(e -> e.getValue() == god).map(Map.Entry::getKey).sorted().toList();
    }

    public static List<BlockPos> relicsOf(int god) {
        return RELIC_KEEPER.entrySet().stream().filter(e -> e.getValue() == god).map(Map.Entry::getKey).sorted().toList();
    }

    /** Where a dreamer arrives: at a god's door if they have written down where it is, otherwise the Clearing. */
    public static BlockPos arrival(ServerPlayer sp, int focusGod, ServerLevel altus) {
        if (focusGod >= 0 && Memories.knowledge(sp).written.contains("LOC:" + focusGod)) {
            BlockPos a = SITE_ARRIVAL.get(focusGod);
            if (a != null) {
                altus.getChunk(a);
                return a;
            }
        }
        return AltusDimension.clearing(altus, sp.getRandom());
    }

    public static void useDoor(ServerPlayer sp, BlockPos pos) {
        Door d = DOORS.get(pos);
        ServerLevel altus = AltusDimension.get(sp.server);
        if (d == null || altus == null) {
            say(sp, "The door does not move.");
            return;
        }
        History h = WorldHistory.get(sp.server);
        switch (d.kind()) {
            case ENTER -> {
                if (Memories.knowledge(sp).understandingOf(d.god()) < 1) {
                    say(sp, "The door is shut to you. You do not know whose door this is.");
                    return;
                }
                BlockPos a = SANCTUM_ARRIVAL.get(d.god());
                sp.teleportTo(altus, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0f, 0f);
                String where = h.god(d.god()).alive ? "the sanctum of " : "what is left of the sanctum of ";
                sp.displayClientMessage(Component.literal("You step into " + where + h.name(d.god()) + ".")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
            }
            case EXIT -> {
                BlockPos a = SITE_ARRIVAL.get(d.god());
                sp.teleportTo(altus, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, sp.getYRot(), 0f);
            }
            case SEALED -> say(sp, "This door will not open. Not yet.");
        }
    }

    public static void useReliquary(ServerPlayer sp, BlockPos pos) {
        String id = RELICS.get(pos);
        Integer keeper = RELIC_KEEPER.get(pos);
        if (id == null || keeper == null) {
            say(sp, "The reliquary is empty.");
            return;
        }
        io.github.bargainbinbastard.altus.history.Lore.Testimony t =
                io.github.bargainbinbastard.altus.history.Lore.render(WorldHistory.get(sp.server), WorldHistory.sites(sp.server), id);
        int needed = t == null ? 2 : requiredUnderstanding(t.level);
        if (Memories.knowledge(sp).understandingOf(keeper) < needed) {
            say(sp, needed > 2 ? "The reliquary is sealed tight. You would need to know its keeper far better."
                    : "The reliquary will not open. You do not know its keeper well enough.");
            return;
        }
        Memories.take(sp, id, "relic");
    }

    /** How much a player must have written about a god to open a reliquary holding lore of this level. */
    public static int requiredUnderstanding(int level) {
        return Math.max(2, level - 1);
    }

    private static void say(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
