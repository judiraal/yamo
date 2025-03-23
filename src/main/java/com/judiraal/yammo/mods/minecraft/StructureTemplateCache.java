package com.judiraal.yammo.mods.minecraft;

import com.google.common.cache.Cache;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

@EventBusSubscriber
public class StructureTemplateCache {
    private static final Path BASE_PATH = FMLPaths.getOrCreateGameRelativePath(Path.of("cache", "structures"));
    public static final Integer INITIAL_VERSION = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    private static StructureTemplateCache INSTANCE;

    private final StructureTemplateManager manager;
    private final ThreadLocal<Integer> lastVersion = ThreadLocal.withInitial(() -> INITIAL_VERSION);

    private enum ScanStatus {WAITING, RUNNING, DONE}
    private ScanStatus scanStatus = ScanStatus.WAITING;
    public Cache<ResourceLocation, Optional<StructureTemplate>> structureCache;

    public StructureTemplateCache(StructureTemplateManager manager) {
        this.manager = manager;
        INSTANCE = this;
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
        if (YammoConfig.displayStructureUpgradeMessage.get() && FMLEnvironment.dist.isClient())
            target = ClientOnly.tryUpgradeTarget(target);
        if (target != null)
            target.sendSystemMessage(Component.literal(message));
    }

    private static class ClientOnly {
        public static CommandSource tryUpgradeTarget(CommandSource target) {
            if (target instanceof IntegratedServer server)
                return LocalPlayerAccess.getLocalPlayerUUID().<CommandSource>map(server.getPlayerList()::getPlayer).orElse(target);
            return target;
        }
    }

    private Stats stats;

    @SubscribeEvent
    public static void runScanOnServer(ServerTickEvent.Post event) {
        if (INSTANCE.structureCache != null && (event.getServer().getTickCount() & 127) == 0) INSTANCE.structureCache.cleanUp();
        if (INSTANCE.scanStatus == ScanStatus.RUNNING && !YammoConfig.asyncStructureUpgrade.get() && (event.getServer().getTickCount() & 1) == 0) {
            if (INSTANCE.stats != null) INSTANCE.stats.update(TimeUnit.NANOSECONDS.toMillis(event.getServer().getAverageTickTimeNanos()));
            INSTANCE.runScanSync(event);
        }
    }

    private void runScan() {
        if (YammoConfig.asyncStructureUpgrade.get()) {
            runScanAsync();
        } else {
            stats = new Stats();
            stats.locations = manager.listTemplates().toList();
            stats.total = stats.locations.size();
            scanStatus = ScanStatus.RUNNING;
        }
    }

    private void runScanSync(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() < 300) return;
        if (stats.count == 0) sendSystemMessage(String.format("YAMO: Starting synchronous background structure scan of %s structures...", stats.total));
        for (var location: stats.locations.subList(stats.count, Math.min(stats.total, stats.count + stats.currentStructureCount))) try {
            scanLocation(location, stats);
            if (Runtime.getRuntime().freeMemory() * 100 / Runtime.getRuntime().maxMemory() < 20) return;
            if (!event.hasTime()) break;
        } catch (Exception e) {
            Yammo.LOGGER.warn("unable to process structure '{}'", location, e);
        }
        if (stats.count >= stats.total) {
            sendSystemMessage("YAMO: Background structure scan complete");
            scanStatus = ScanStatus.DONE;
        }
    }

    public void runScanAsync() {
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
            sendSystemMessage(String.format("YAMO: Starting async background structure scan of %s structures...", stats.total));
            for (var location: locations) try {
                if (scanLocation(location, stats))
                    try {
                        Thread.sleep(20);
                    } catch (Exception ignored) {}
            } catch (Exception e) {
                Yammo.LOGGER.warn("unable to process structure '{}'", location, e);
            }
            sendSystemMessage("YAMO: Background structure scan complete");
            scanStatus = ScanStatus.DONE;
            executor.shutdown();
        });
    }

    private boolean scanLocation(ResourceLocation location, Stats stats) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            stats.messageTickCount = 0;
        } else if (server.getTickCount() - stats.messageTickCount > 600) {
            stats.messageTickCount = server.getTickCount();
            sendSystemMessage(String.format("YAMO: Currently scanned %s structures and updated %s, %s%% done",
                    stats.count, stats.saved, (int)((float)stats.count / stats.total * 100)));
        }
        boolean result = ((LoaderAccess) manager).msc$cache(location);
        if (result) stats.saved++;
        stats.count++;
        return result;
    }

    private static class Stats {
        int count;
        int saved;
        int total;
        int messageTickCount;
        int currentStructureCount = 1;
        List<ResourceLocation> locations;

        public void update(long millis) {
            if (millis < 30) currentStructureCount++;
            if (millis > 60) currentStructureCount--;
            if (currentStructureCount < 1) currentStructureCount = 1;
        }
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
