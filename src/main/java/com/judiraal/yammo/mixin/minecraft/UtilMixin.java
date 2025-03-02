package com.judiraal.yammo.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Util.class)
public class UtilMixin {
    @ModifyExpressionValue(method = "fetchChoiceType", at = @At(value = "FIELD", target = "net/minecraft/SharedConstants.CHECK_DATA_FIXER_SCHEMA:Z"))
    private static boolean msc$removeSchemaChecking(boolean original) {
        return false;
    }
}
