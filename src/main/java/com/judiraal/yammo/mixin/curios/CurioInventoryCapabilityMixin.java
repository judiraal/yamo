package com.judiraal.yammo.mixin.curios;

import com.judiraal.yammo.YammoConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(CurioInventoryCapability.class)
public class CurioInventoryCapabilityMixin {
    @Final
    @Shadow
    LivingEntity livingEntity;

    @Unique
    private Map<Item, Optional<SlotResult>> firstCurioMap;

    @Unique
    private long firstCurioGameTime;

    @Inject(method = "findFirstCurio(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    public void findFirstCurio(Item item, CallbackInfoReturnable<Optional<SlotResult>> cir) {
        if (!YammoConfig.fixCuriosSlowFirstCurio) return;
        if (firstCurioMap == null) firstCurioMap = new HashMap<>(4);
        long currentGameTime = this.livingEntity.level().getGameTime();
        if (currentGameTime != firstCurioGameTime) {
            firstCurioGameTime = currentGameTime;
            firstCurioMap.clear();
        }
        cir.setReturnValue(firstCurioMap.computeIfAbsent(item,
                i -> ((CurioInventoryCapability)(Object)this).findFirstCurio(
                        s -> s.getItem() == i, "")));
    }
}
