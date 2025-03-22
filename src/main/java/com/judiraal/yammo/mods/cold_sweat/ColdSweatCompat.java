package com.judiraal.yammo.mods.cold_sweat;

import com.judiraal.yammo.YammoConfig;
import com.judiraal.yammo.mixin.cold_sweat.BlockTempModifierAccessor;
import com.judiraal.yammo.mixin.cold_sweat.WorldHelperAccessor;
import com.judiraal.yammo.mods.ConditionalEventBusSubscriber;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@ConditionalEventBusSubscriber(dependencies = {"cold_sweat"})
public class ColdSweatCompat {
    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        if (YammoConfig.fixColdSweatBlockTemp) {
            EntityTempManager.SERVER_CAP_CACHE.clear();
            EntityTempManager.CLIENT_CAP_CACHE.clear();
            WorldHelperAccessor.getTemperatureChecks().clear();
            WorldHelperAccessor.getDummies().clear();
        }
    }

    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientLevelUnload(LevelEvent.Unload event) {
            fixClientCachedChunks();
        }

        @SubscribeEvent
        public static void onClientLevelLoad(LevelEvent.Load event) {
            fixClientCachedChunks();
        }

        private static void fixClientCachedChunks() {
            if (!YammoConfig.fixColdSweatBlockTemp) return;
            EntityTempManager.CLIENT_CAP_CACHE.values()
                    .forEach(cap -> cap.getModifiers(Temperature.Trait.WORLD)
                            .forEach(m -> {
                                if (m instanceof BlockTempModifier blockTempModifier) fixBlockTempModifier(blockTempModifier);
                            }));
        }

        private static void fixBlockTempModifier(BlockTempModifier blockTempModifier) {
            var chunks = ((BlockTempModifierAccessor)blockTempModifier).getChunks();
            chunks.clear();
        }
    }
}
