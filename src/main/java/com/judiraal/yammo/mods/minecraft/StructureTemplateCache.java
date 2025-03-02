package com.judiraal.yammo.mods.minecraft;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.judiraal.yammo.Yammo;
import net.minecraft.FileUtil;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class StructureTemplateCache {
    private static final Path BASE_PATH = FMLPaths.getOrCreateGameRelativePath(Path.of("cache", "structures"));
    public static final Integer INITIAL_VERSION = SharedConstants.getCurrentVersion().getDataVersion().getVersion();

    private final StructureTemplateManager manager;
    private final ThreadLocal<Integer> lastVersion = ThreadLocal.withInitial(() -> INITIAL_VERSION);

    private enum ScanStatus {WAITING, RUNNING, DONE}
    private ScanStatus scanStatus = ScanStatus.WAITING;

    public StructureTemplateCache(StructureTemplateManager manager) {
        this.manager = manager;
    }

    private Path resourceToPath(ResourceLocation resourceLocation) {
        return FileUtil.createPathToResource(BASE_PATH.resolve(resourceLocation.getNamespace()), resourceLocation.getPath(), ".nbt");
    }

    public boolean existsCached(ResourceLocation resourceLocation) {
        try {
            return Files.exists(resourceToPath(resourceLocation));
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<StructureTemplate> tryCachedLoad(ResourceLocation resourceLocation) {
        try {
            Path path = resourceToPath(resourceLocation);
            if (Files.exists(path)) {
                try (InputStream inputstream = new FileInputStream(path.toFile())) {
                    CompoundTag compoundtag = NbtIo.readCompressed(inputstream, NbtAccounter.unlimitedHeap());
                    return Optional.of(manager.readStructure(compoundtag));
                }
            }
        } catch (Exception e) {
            Yammo.LOGGER.warn("error loading structure template from cache for {}", resourceLocation, e);
        }
        return Optional.empty();
    }

    public boolean storeCached(ResourceLocation resourceLocation, StructureTemplate structureTemplate) {
        try {
            if (scanStatus == ScanStatus.WAITING) runScan();
            Yammo.LOGGER.debug("storing cached structure {}", resourceLocation);
            Path path = resourceToPath(resourceLocation);
            Files.createDirectories(path.getParent());
            CompoundTag compoundtag = structureTemplate.save(new CompoundTag());
            try (OutputStream outputstream = new FileOutputStream(path.toFile())) {
                NbtIo.writeCompressed(compoundtag, outputstream);
            }
            return true;
        } catch (Exception e) {
            Yammo.LOGGER.warn("error saving structure template to cache for {}", resourceLocation, e);
        }
        return false;
    }

    public void runScan() {
        if (scanStatus != ScanStatus.WAITING) return;
        synchronized (this) {
            if (scanStatus != ScanStatus.WAITING) return;
            scanStatus = ScanStatus.RUNNING;
        }
        Executor executor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("StructureTemplateCache Initialize").setDaemon(true).setPriority(1).build()
        );
        executor.execute(() -> {
            manager.listTemplates().forEach(((LoaderAccess)manager)::msc$cache);
            scanStatus = ScanStatus.DONE;
        });
    }

    public int getLastVersion() {
        return lastVersion.get();
    }

    public void setLastVersion(int original) {
        lastVersion.set(original);
    }

    public interface LoaderAccess {
        void msc$cache(ResourceLocation id);
    }
}
