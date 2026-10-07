package io.github.bargainbinbastard.altus.lore;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** A marker effect: its timer shows how long the player's longest-lasting memory has left. */
public class FadingMemoryEffect extends MobEffect {
    public FadingMemoryEffect() {
        super(MobEffectCategory.NEUTRAL, 0x9b8fc7);
    }
}
