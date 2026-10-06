package io.github.bargainbinbastard.altus;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point for The Altus.
 */
@Mod(AltusMod.MODID)
public class AltusMod {
    public static final String MODID = "altus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AltusMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("The Altus stirs.");
    }
}
