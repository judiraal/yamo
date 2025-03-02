package com.judiraal.yammo;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Yammo.MOD_ID)
public class Yammo {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "yammo";

    public Yammo(IEventBus modEventBus, ModContainer modContainer) {
    }
}
