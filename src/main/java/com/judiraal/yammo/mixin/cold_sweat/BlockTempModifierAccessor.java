package com.judiraal.yammo.mixin.cold_sweat;

import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(BlockTempModifier.class)
public interface BlockTempModifierAccessor {
    @Accessor
    Map<ChunkPos, ChunkAccess> getChunks();
}
