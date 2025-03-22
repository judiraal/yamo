package com.judiraal.yammo.mixin.cold_sweat;

import com.momosoftworks.coldsweat.util.entity.DummyPlayer;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(WorldHelper.class)
public interface WorldHelperAccessor {
    @Accessor("TEMPERATURE_CHECKS")
    @Nonnull
    static Map<ResourceKey<Level>, List<WorldHelper.TempSnapshot>> getTemperatureChecks() {
        return new HashMap<>();
    }

    @Accessor("DUMMIES")
    static Map<ResourceLocation, DummyPlayer> getDummies() {
        return new HashMap<>();
    }
}
