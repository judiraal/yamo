package com.judiraal.yammo.mods.minecraft;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.judiraal.yammo.Yammo;
import com.judiraal.yammo.YammoConfig;
import net.minecraft.FileUtil;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
            //Yammo.LOGGER.debug("storing cached structure {}", resourceLocation);
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

    private void sendSystemMessage(String message) {
        CommandSource target = ServerLifecycleHooks.getCurrentServer();
        if (YammoConfig.displayStructureUpgradeMessage.get() && target instanceof IntegratedServer server)
            target = LocalPlayerAccess.getLocalPlayerUUID().<CommandSource>map(server.getPlayerList()::getPlayer).orElse(target);
        if (target != null)
            target.sendSystemMessage(Component.literal(message));
    }

    public void runScan() {
        if (scanStatus != ScanStatus.WAITING) return;
        synchronized (this) {
            if (scanStatus != ScanStatus.WAITING) return;
            scanStatus = ScanStatus.RUNNING;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("StructureTemplateCache Initialize").setDaemon(true).build());
        var stats = new Stats();
        executor.execute(() -> {
            try {
                Thread.sleep(15000);
            } catch (Exception ignored) {}
            var locations = manager.listTemplates().toList();
            stats.total = locations.size();
            sendSystemMessage(String.format("YAMO: Starting background structure scan of %s structures...", stats.total));
            for (var location: locations) try {
                scanLocation(location, stats);
            } catch (Exception e) {
                Yammo.LOGGER.warn("unable to process structure '{}'", location, e);
            }
            sendSystemMessage("YAMO: Background structure scan complete");
            scanStatus = ScanStatus.DONE;
            executor.shutdown();
        });
    }

    private void scanLocation(ResourceLocation location, Stats stats) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            stats.messageTickCount = 0;
        } else if (server.getTickCount() - stats.messageTickCount > 600) {
            stats.messageTickCount = server.getTickCount();
            sendSystemMessage(String.format("YAMO: Currently scanned %s structures and updated %s, %s%% done",
                    stats.count, stats.saved, (int)((float)stats.count / stats.total * 100)));
        }
        if (((LoaderAccess) manager).msc$cache(location)) stats.saved++;
        stats.count++;
    }

    private static class Stats {
        long count;
        long saved;
        long total;
        int messageTickCount;
    }

    public int getLastVersion() {
        return lastVersion.get();
    }

    public void setLastVersion(int original) {
        lastVersion.set(original);
    }

    public interface LoaderAccess {
        boolean msc$cache(ResourceLocation id);
    }

    private static class LocalPlayerAccess {
        static Optional<UUID> getLocalPlayerUUID() {
            return Optional.ofNullable(Minecraft.getInstance().player).map(LocalPlayer::getUUID);
        }
    }
}
