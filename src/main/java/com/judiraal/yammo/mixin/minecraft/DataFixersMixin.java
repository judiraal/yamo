package com.judiraal.yammo.mixin.minecraft;

import com.judiraal.yammo.YammoConfig;
import com.judiraal.yammo.mods.minecraft.LazyDataFixerBuilder;
import com.mojang.datafixers.DataFixerBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataFixers.class)
public class DataFixersMixin {
    @Shadow
    private static void addFixers(DataFixerBuilder builder) {
    }

    @Inject(method = "createFixerUpper", at = @At("HEAD"), cancellable = true)
    private static void yammo$replaceDataFixer(CallbackInfoReturnable<DataFixerBuilder.Result> cir) {
        cir.setReturnValue(new LazyDataFixerBuilder(() -> {
            DataFixerBuilder datafixerbuilder = new DataFixerBuilder(SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            addFixers(datafixerbuilder);
            DataFixerBuilder.Result result = datafixerbuilder.build();
            result.optimize(DataFixTypes.TYPES_FOR_LEVEL_LIST, Runnable::run);
            return datafixerbuilder.build();
        }).build());
    }
}
