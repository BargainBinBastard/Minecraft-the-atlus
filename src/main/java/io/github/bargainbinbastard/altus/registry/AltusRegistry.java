package io.github.bargainbinbastard.altus.registry;

import io.github.bargainbinbastard.altus.AltusMod;
import io.github.bargainbinbastard.altus.lore.FadingMemoryEffect;
import io.github.bargainbinbastard.altus.lore.InscriptionBlock;
import io.github.bargainbinbastard.altus.lore.TomeContents;
import io.github.bargainbinbastard.altus.lore.TomeItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything the mod adds to the game's registries. */
public final class AltusRegistry {
    private AltusRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AltusMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AltusMod.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, AltusMod.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AltusMod.MODID);

    public static final DeferredBlock<InscriptionBlock> INSCRIPTION = BLOCKS.register("inscription",
            () -> new InscriptionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
                    .noLootTable().sound(SoundType.STONE)));
    public static final DeferredItem<BlockItem> INSCRIPTION_ITEM = ITEMS.registerSimpleBlockItem("inscription", INSCRIPTION);

    public static final DeferredItem<TomeItem> TOME = ITEMS.register("tome", () -> new TomeItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MobEffect, FadingMemoryEffect> FADING_MEMORY =
            EFFECTS.register("fading_memory", FadingMemoryEffect::new);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TomeContents>> TOME_CONTENTS =
            COMPONENTS.register("tome_contents", () -> DataComponentType.<TomeContents>builder()
                    .persistent(TomeContents.CODEC)
                    .networkSynchronized(TomeContents.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(AltusRegistry::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(TOME.get());
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) event.accept(INSCRIPTION_ITEM.get());
    }
}
