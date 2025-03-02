package com.judiraal.yammo.mixin.minecraft;

import com.google.common.cache.CacheBuilder;
import com.judiraal.yammo.Yammo;
import com.judiraal.yammo.mods.minecraft.StructureTemplateCache;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.DataFixer;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin implements StructureTemplateCache.LoaderAccess {
    @Unique
    private StructureTemplateCache cache = new StructureTemplateCache((StructureTemplateManager)(Object)this);

    @Shadow
    protected abstract Optional<StructureTemplate> tryLoad(ResourceLocation id);

    @Unique
    @Override
    public void msc$cache(ResourceLocation id) {
        if (!cache.existsCached(id)) {
            var result = tryLoad(id);
            if (result.isPresent() && cache.getLastVersion() != StructureTemplateCache.INITIAL_VERSION)
                cache.storeCached(id, result.get());
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void msc$replaceCache(ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer fixerUpper, HolderGetter blockLookup, CallbackInfo ci) {
        try {
            var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            var u = (Unsafe) f.get(null);
            u.putObject(this, u.objectFieldOffset(StructureTemplateManager.class.getDeclaredField("structureRepository")), msc$cachingRepository());
        } catch (Exception e) {
            Yammo.LOGGER.info("unable to optimize StructureTemplateManager.structureRepository");
        }
    }

    @Unique
    private Map<ResourceLocation, Optional<StructureTemplate>> msc$cachingRepository() {
        return CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).<ResourceLocation, Optional<StructureTemplate>>build().asMap();
    }

    @ModifyArg(method = {"get"}, at = @At(value = "INVOKE", target = "java/util/Map.computeIfAbsent (Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"), index = 1)
    private Function<ResourceLocation, Optional<StructureTemplate>> msc$load(Function<ResourceLocation, Optional<StructureTemplate>> mappingFunction) {
        return r -> {
            var result = cache.tryCachedLoad(r);
            if (result.isPresent()) return result;
            result = mappingFunction.apply(r);
            if (result.isPresent() && cache.getLastVersion() != StructureTemplateCache.INITIAL_VERSION)
                cache.storeCached(r, result.get());
            return result;
        };
    }

    @ModifyExpressionValue(method = "readStructure(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;", at = @At(value = "INVOKE", target = "net/minecraft/nbt/NbtUtils.getDataVersion (Lnet/minecraft/nbt/CompoundTag;I)I"))
    private int msc$setVersion(int original) {
        cache.setLastVersion(original);
        return original;
    }
}
