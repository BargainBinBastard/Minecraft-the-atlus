package io.github.bargainbinbastard.altus.lore;

import java.util.List;
import java.util.Map;

import io.github.bargainbinbastard.altus.history.Content;
import io.github.bargainbinbastard.altus.history.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Which of the gods' likes a block, item or creature counts as. */
public final class LikeMatch {
    private LikeMatch() {}

    private static final Map<net.minecraft.world.item.Item, String> ITEMS = Map.ofEntries(
            Map.entry(Items.WHEAT, "wheat"), Map.entry(Items.HONEYCOMB, "honeycomb"), Map.entry(Items.KELP, "kelp"),
            Map.entry(Items.DRIED_KELP, "kelp"), Map.entry(Items.BONE, "bone_block"), Map.entry(Items.BONE_MEAL, "bone_block"),
            Map.entry(Items.AMETHYST_SHARD, "amethyst"), Map.entry(Items.COPPER_INGOT, "copper"), Map.entry(Items.RAW_COPPER, "copper"),
            Map.entry(Items.IRON_INGOT, "iron_block"), Map.entry(Items.RAW_IRON, "iron_block"), Map.entry(Items.SNOWBALL, "snow"),
            Map.entry(Items.PRISMARINE_SHARD, "prismarine"), Map.entry(Items.PRISMARINE_CRYSTALS, "prismarine"),
            Map.entry(Items.STRING, "cobweb"), Map.entry(Items.ECHO_SHARD, "sculk"));

    private static final Map<EntityType<?>, String> ENTITIES = Map.ofEntries(
            Map.entry(EntityType.WOLF, "wolf"), Map.entry(EntityType.BEE, "bee"), Map.entry(EntityType.DROWNED, "drowned"),
            Map.entry(EntityType.ENDERMAN, "enderman"), Map.entry(EntityType.SKELETON, "skeleton"), Map.entry(EntityType.FOX, "fox"),
            Map.entry(EntityType.AXOLOTL, "axolotl"), Map.entry(EntityType.SPIDER, "spider"), Map.entry(EntityType.PHANTOM, "phantom"),
            Map.entry(EntityType.SHEEP, "sheep"), Map.entry(EntityType.BAT, "bat"), Map.entry(EntityType.WITCH, "witch"));

    private static TagKey<Block> tag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("altus", "likeable/" + id));
    }

    /** The block like a block state belongs to, or null. */
    public static String of(BlockState state) {
        for (Item l : Content.BLOCKS) if (state.is(tag(l.id))) return l.id;
        return null;
    }

    /** The like an item counts as when offered, or null. */
    public static String of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String direct = ITEMS.get(stack.getItem());
        if (direct != null) return direct;
        if (stack.getItem() instanceof BlockItem bi) return of(bi.getBlock().defaultBlockState());
        return null;
    }

    public static String of(EntityType<?> type) {
        return ENTITIES.get(type);
    }

    /** The display name of a like, e.g. "Copper" or "the Drowned". */
    public static String name(String id) {
        for (List<Item> l : List.of(Content.BLOCKS, Content.ENTITIES))
            for (Item i : l) if (i.id.equals(id)) return i.name;
        return id;
    }
}
