package com.judiraal.yammo.mods;

import com.google.common.collect.ImmutableList;
import com.judiraal.yammo.Yammo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.util.List;

public class ConditionalSubscribers {
    private static final Type CONDITIONAL_SUBSCRIBER = Type.getType(ConditionalEventBusSubscriber.class);
    private static final ImmutableList<ModAnnotation.EnumHolder> DEFAULT_SIDES = ImmutableList
            .of(new ModAnnotation.EnumHolder(null, "CLIENT"), new ModAnnotation.EnumHolder(null, "DEDICATED_SERVER"));

    public static void inject(ModContainer modContainer) {
        ModFileInfo info = FMLLoader.getLoadingModList().getModFileById(modContainer.getModId());
        if (info == null) return;
        Module layer = FMLLoader.getGameLayer().findModule(modContainer.getModInfo().getOwningFile().moduleName()).orElseThrow();
        info.getFile().getScanResult().getAnnotations().stream()
                .filter(ConditionalSubscribers::isValid)
                .forEach(annotationData -> inject(modContainer, annotationData, layer));
    }

    private static boolean isValid(ModFileScanData.AnnotationData annotationData) {
        return CONDITIONAL_SUBSCRIBER.equals(annotationData.annotationType()) &&
                inDist(annotationData) &&
                hasDeps(annotationData);
    }

    private static boolean inDist(ModFileScanData.AnnotationData annotationData) {
        @SuppressWarnings("unchecked")
        List<ModAnnotation.EnumHolder> sidesValue = (List<ModAnnotation.EnumHolder>)annotationData.annotationData().getOrDefault("value", DEFAULT_SIDES);
        return sidesValue.stream().anyMatch(eh -> FMLEnvironment.dist == Dist.valueOf(eh.value()));
    }

    private static boolean hasDeps(ModFileScanData.AnnotationData annotationData) {
        @SuppressWarnings("unchecked")
        List<String> dependencies = (List<String>)annotationData.annotationData().getOrDefault("dependencies", ImmutableList.of());
        return dependencies.stream().allMatch(dep -> FMLLoader.getLoadingModList().getModFileById(dep) != null);
    }

    private static void inject(ModContainer modContainer, ModFileScanData.AnnotationData annotationData, Module layer) {
        try {
            Class<?> clazz = Class.forName(annotationData.clazz().getClassName(), true, layer.getClassLoader());
            NeoForge.EVENT_BUS.register(clazz);
            IEventBus modBus = modContainer.getEventBus();
            for (Class<?> subClass:clazz.getDeclaredClasses()) {
                if ("ClientEvents".equals(subClass.getSimpleName()) && FMLEnvironment.dist == Dist.CLIENT)
                    NeoForge.EVENT_BUS.register(subClass);
                if (modBus != null && "ModEvents".equals(subClass.getSimpleName()))
                    modBus.register(subClass);
                if (modBus != null && "ClientModEvents".equals(subClass.getSimpleName()) && FMLEnvironment.dist == Dist.CLIENT)
                    modBus.register(subClass);
            }
        } catch (Throwable t) {
            Yammo.LOGGER.warn("exception during conditional eventbus subscription of {}", annotationData.targetType().name(), t);
        }
    }
}
