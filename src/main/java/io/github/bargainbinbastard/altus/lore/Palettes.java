package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Turns a god's liked blocks into building materials for its door and sanctum. */
public final class Palettes {
    private Palettes() {}

    public record Palette(BlockState wall, BlockState floor, BlockState accent, BlockState light, BlockState decor) {}

    static Block wallOf(String id) {
        switch (id) {
            case "deepslate": return Blocks.DEEPSLATE_BRICKS;
            case "amethyst": return Blocks.AMETHYST_BLOCK;
            case "kelp": return Blocks.DRIED_KELP_BLOCK;
            case "wheat": return Blocks.HAY_BLOCK;
            case "glass": return Blocks.TINTED_GLASS;
            case "bone_block": return Blocks.BONE_BLOCK;
            case "copper": return Blocks.WAXED_CUT_COPPER;
            case "moss": return Blocks.MOSS_BLOCK;
            case "obsidian": return Blocks.OBSIDIAN;
            case "coral": return Blocks.DEAD_BRAIN_CORAL_BLOCK;
            case "honeycomb": return Blocks.HONEYCOMB_BLOCK;
            case "snow": return Blocks.SNOW_BLOCK;
            case "iron_block": return Blocks.IRON_BLOCK;
            case "sculk": return Blocks.SCULK;
            case "mud": return Blocks.MUD_BRICKS;
            case "prismarine": return Blocks.PRISMARINE_BRICKS;
            default: return null;
        }
    }

    static Block floorOf(String id) {
        switch (id) {
            case "deepslate": return Blocks.POLISHED_DEEPSLATE;
            case "amethyst": return Blocks.AMETHYST_BLOCK;
            case "kelp": return Blocks.DRIED_KELP_BLOCK;
            case "wheat": return Blocks.HAY_BLOCK;
            case "glass": return Blocks.GLASS;
            case "bone_block": return Blocks.BONE_BLOCK;
            case "copper": return Blocks.WAXED_COPPER_BLOCK;
            case "moss": return Blocks.MOSS_BLOCK;
            case "obsidian": return Blocks.CRYING_OBSIDIAN;
            case "coral": return Blocks.DEAD_TUBE_CORAL_BLOCK;
            case "honeycomb": return Blocks.HONEYCOMB_BLOCK;
            case "snow": return Blocks.SNOW_BLOCK;
            case "iron_block": return Blocks.IRON_BLOCK;
            case "sculk": return Blocks.SCULK;
            case "mud": return Blocks.PACKED_MUD;
            case "prismarine": return Blocks.DARK_PRISMARINE;
            default: return null;
        }
    }

    public static Palette of(History.God g) {
        Block wall = null, floor = null, accent = null;
        boolean prismarine = false, obsidian = false, sculk = false;
        BlockState decor = null;
        for (Item l : g.likes) {
            if (!l.cat.equals("block")) continue;
            Block w = wallOf(l.id), f = floorOf(l.id);
            if (w != null) {
                if (wall == null) wall = w;
                else if (floor == null) floor = f;
                else if (accent == null) accent = w;
            }
            switch (l.id) {
                case "prismarine" -> prismarine = true;
                case "obsidian" -> obsidian = true;
                case "sculk" -> sculk = true;
                case "cobweb" -> decor = Blocks.COBWEB.defaultBlockState();
                case "candle" -> decor = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
                case "amethyst" -> { if (decor == null) decor = Blocks.AMETHYST_CLUSTER.defaultBlockState(); }
                default -> { }
            }
        }
        if (wall == null) wall = Blocks.STONE_BRICKS;
        if (floor == null) floor = firstFloor(g, wall);
        if (accent == null) accent = Blocks.CHISELED_STONE_BRICKS;
        Block light = prismarine ? Blocks.SEA_LANTERN : obsidian ? Blocks.CRYING_OBSIDIAN : sculk ? Blocks.SCULK_CATALYST : Blocks.GLOWSTONE;
        return new Palette(wall.defaultBlockState(), floor.defaultBlockState(), accent.defaultBlockState(), light.defaultBlockState(), decor);
    }

    private static Block firstFloor(History.God g, Block wall) {
        for (Item l : g.likes) if (l.cat.equals("block") && floorOf(l.id) != null) return floorOf(l.id);
        return wall == Blocks.STONE_BRICKS ? Blocks.POLISHED_ANDESITE : wall;
    }
}
