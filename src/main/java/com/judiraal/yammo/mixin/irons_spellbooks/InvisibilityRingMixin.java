package com.judiraal.yammo.mixin.irons_spellbooks;

import com.judiraal.yammo.YammoConfig;
import io.redspace.ironsspellbooks.item.curios.InvisibiltyRing;
import io.redspace.ironsspellbooks.item.curios.SimpleDescriptiveCurio;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(InvisibiltyRing.class)
public abstract class InvisibilityRingMixin extends SimpleDescriptiveCurio {
    public InvisibilityRingMixin(Properties properties) {
        super(properties);
    }

    @Unique
    private long clientLastGameTime;
    private boolean playerIsEquipped;

    @Override
    public boolean isEquippedBy(@Nullable LivingEntity entity) {
        if (!YammoConfig.fixCuriosSlowFirstCurio) return super.isEquippedBy(entity);
        if (entity instanceof LocalPlayer) {
            long currentGameTime = entity.level().getGameTime();
            if (currentGameTime == clientLastGameTime) return playerIsEquipped;
            clientLastGameTime = currentGameTime;
            return playerIsEquipped = super.isEquippedBy(entity);
        }
        return super.isEquippedBy(entity);
    }
}
