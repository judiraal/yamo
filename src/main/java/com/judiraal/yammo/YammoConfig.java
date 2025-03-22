package com.judiraal.yammo;

import com.judiraal.yammo.mods.minecraft.LazyDataFixerBuilder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.module.ModuleDescriptor;

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
    public static ModConfigSpec.BooleanValue asyncStructureUpgrade;
    public static ModConfigSpec.BooleanValue displayStructureUpgradeMessage;
    public static ModConfigSpec.BooleanValue fixMemoryIssues;

    public static boolean fixColdSweatBlockTemp;
    public static boolean fixCuriosSlowFirstCurio;

    private YammoConfig(final ModConfigSpec.Builder builder) {
        lazyDFU = builder.comment("Whether the DFU should be loaded lazily.")
                .define("lazyDFU", true);
        autoStructureUpgrade = builder.comment("Should automatic upgrading of structures be stored on disk to limit DFU loading in the future.")
                .define("autoStructureUpgrade", true);
        asyncStructureUpgrade = builder.comment("Should the structure scan be done async to the server thread. Might be faster but likely more unstable with less RAM.")
                .define("asyncStructureUpgrade", false);
        displayStructureUpgradeMessage = builder.comment("Send a visible system message to inform the user of the structure scan start and completion.")
                .define("displayStructureUpgradeMessage", true);
        fixMemoryIssues = builder.comment("Fix multiple issues caused by mods caching objects that prevent the correct disposal of dimensions and integrated servers.")
                .define("fixMemoryIssues", true);
        builder.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (!lazyDFU.get()) LazyDataFixerBuilder.forceDFU();
        fixColdSweatBlockTemp = fixMemoryIssues.get() && !isBlockTempFixedByMoreSweat();
        fixCuriosSlowFirstCurio = fixMemoryIssues.get() && !isBlockTempFixedByMoreSweat();
    }

    private static boolean isBlockTempFixedByMoreSweat() {
        ModFileInfo info = FMLLoader.getLoadingModList().getModFileById("moresweatcompat");
        if (info == null) return false;
        try {
            return ModuleDescriptor.Version.parse(info.versionString()).compareTo(ModuleDescriptor.Version.parse("1.0.5")) < 0;
        } catch (Exception e) {
            return false;
        }
    }
}
