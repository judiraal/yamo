package com.judiraal.yammo;

import com.judiraal.yammo.mods.minecraft.LazyDataFixerBuilder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

@EventBusSubscriber(modid = Yammo.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class YammoConfig {
    public static final YammoConfig CONFIG;
    static final ModConfigSpec SPEC;

    static {
        Pair<YammoConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(YammoConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static ModConfigSpec.BooleanValue lazyDFU;
    public static ModConfigSpec.BooleanValue autoStructureUpgrade;
    public static ModConfigSpec.BooleanValue displayStructureUpgradeMessage;

    private YammoConfig(final ModConfigSpec.Builder builder) {
        lazyDFU = builder.comment("Whether the DFU should be loaded lazily.")
                .define("lazyDFU", true);
        autoStructureUpgrade = builder.comment("Should automatic upgrading of structures be stored on disk to limit DFU loading in the future.")
                .define("autoStructureUpgrade", true);
        displayStructureUpgradeMessage = builder.comment("Send a visible system message to inform the user of the structure scan start and completion.")
                .define("displayStructureUpgradeMessage", true);

        builder.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (!lazyDFU.get()) LazyDataFixerBuilder.forceDFU();
    }
}
