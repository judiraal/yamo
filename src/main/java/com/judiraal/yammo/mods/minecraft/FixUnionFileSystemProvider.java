package com.judiraal.yammo.mods.minecraft;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.judiraal.yammo.Yammo;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.jarjar.nio.pathfs.PathFileSystem;
import net.neoforged.jarjar.nio.pathfs.PathFileSystemProvider;
import net.neoforged.jarjar.nio.util.Lazy;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class FixUnionFileSystemProvider {
    private static final Class<?> ZFS_CLASS;
    private static final Class<?> BAC_CLASS;
    private static final Class<?> ZIP_SOURCE_CLASS;

    private static final MethodHandle H_ZIP_FILE_SYSTEM_CH;
    private static final MethodHandle H_ZIP_FILE_SYSTEM_SET_CH;
    private static final MethodHandle H_ZIP_FILE_SYSTEM_CEN;
    private static final MethodHandle H_ZIP_FILE_SYSTEM_SET_CEN;
    private static final MethodHandle H_ZIP_SOURCE_CEN;
    private static final MethodHandle H_ZIP_SOURCE_SET_CEN;
    private static final MethodHandle H_ZIP_SOURCE_FILES;
    private static final MethodHandle H_ZIP_SOURCE_KEY_FILE;
    private static final MethodHandle H_FILE_CHANNEL_IMPL_UNINTERRUPTIBLE;
    private static final MethodHandle H_PFS_FILESYSTEMS;
    private static final MethodHandle H_PFS_INNERSYSTEM;
    private static final MethodHandle H_UFS_FILESYSTEMS;
    private static final MethodHandle H_UFS_EMBEDDED_MAP;
    private static final MethodHandle H_EMBEDDED_METADATA_CONSTRUCTOR;
    private static final MethodHandle H_EMBEDDED_METADATA_FS;
    private static final MethodHandle H_BAC_BUFFER;

    private static final Unsafe UNSAFE;

    private static final Path BASE_PATH = FMLPaths.getOrCreateGameRelativePath(Path.of("cache", "mods"));

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

            var lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            var lookup = (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(lookupField), UNSAFE.staticFieldOffset(lookupField));

            BAC_CLASS = Class.forName("jdk.nio.zipfs.ByteArrayChannel");
            ZFS_CLASS = Class.forName("jdk.nio.zipfs.ZipFileSystem");
            H_ZIP_FILE_SYSTEM_CH = lookup.findGetter(ZFS_CLASS, "ch", SeekableByteChannel.class);
            H_ZIP_FILE_SYSTEM_SET_CH = lookup.findSetter(ZFS_CLASS, "ch", SeekableByteChannel.class);
            H_ZIP_FILE_SYSTEM_CEN = lookup.findGetter(ZFS_CLASS, "cen", byte[].class);
            H_ZIP_FILE_SYSTEM_SET_CEN = lookup.findSetter(ZFS_CLASS, "cen", byte[].class);

            ZIP_SOURCE_CLASS = Class.forName("java.util.zip.ZipFile$Source");
            H_ZIP_SOURCE_CEN = lookup.findGetter(ZIP_SOURCE_CLASS, "cen", byte[].class);
            H_ZIP_SOURCE_SET_CEN = lookup.findSetter(ZIP_SOURCE_CLASS, "cen", byte[].class);
            H_ZIP_SOURCE_FILES = lookup.findStaticGetter(ZIP_SOURCE_CLASS, "files", HashMap.class);

            var clazz = Class.forName("java.util.zip.ZipFile$Source$Key");
            H_ZIP_SOURCE_KEY_FILE = lookup.findGetter(clazz, "file", File.class);

            clazz = Class.forName("sun.nio.ch.FileChannelImpl");
            H_FILE_CHANNEL_IMPL_UNINTERRUPTIBLE = lookup.findSpecial(clazz, "setUninterruptible", MethodType.methodType(void.class), clazz);

            clazz = Class.forName("net.neoforged.jarjar.nio.pathfs.PathFileSystemProvider");
            H_PFS_FILESYSTEMS = lookup.findGetter(clazz, "fileSystems", Map.class);

            clazz = Class.forName("net.neoforged.jarjar.nio.pathfs.PathFileSystem");
            H_PFS_INNERSYSTEM = lookup.findGetter(clazz, "innerSystem", Lazy.class);

            clazz = Class.forName("cpw.mods.niofs.union.UnionFileSystemProvider");
            H_UFS_FILESYSTEMS = lookup.findGetter(clazz, "fileSystems", Map.class);

            clazz = Class.forName("cpw.mods.niofs.union.UnionFileSystem");
            H_UFS_EMBEDDED_MAP = lookup.findGetter(clazz, "embeddedFileSystems", Map.class);

            clazz = Class.forName("cpw.mods.niofs.union.UnionFileSystem$EmbeddedFileSystemMetadata");
            H_EMBEDDED_METADATA_CONSTRUCTOR = lookup.findConstructor(clazz, MethodType.methodType(void.class, Path.class, FileSystem.class, SeekableByteChannel.class));
            H_EMBEDDED_METADATA_FS = lookup.findGetter(clazz, "fs", FileSystem.class);

            clazz = Class.forName("jdk.nio.zipfs.ByteArrayChannel");
            H_BAC_BUFFER = lookup.findGetter(clazz, "buf", byte[].class);

            Files.createDirectories(BASE_PATH);
        } catch (IOException | NoSuchFieldException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @SubscribeEvent
    public static void fixUFSP(FMLConstructModEvent event) {
        FileSystemProvider.installedProviders().stream()
                .filter(f -> f instanceof UnionFileSystemProvider)
                .forEach(FixUnionFileSystemProvider::fixUFSP);
        FileSystemProvider.installedProviders().stream()
                .filter(f -> f instanceof PathFileSystemProvider)
                .forEach(FixUnionFileSystemProvider::fixPFSP);
        fixZipFileSource();
        CEN_CACHE.clear();
        CEN_CACHE = null;
    }

    @SuppressWarnings("unchecked")
    private static void fixZipFileSource() {
        try {
            var files = (Map<Object, Object>) H_ZIP_SOURCE_FILES.invoke();
            for (var e: files.entrySet()) {
                var f = (File) H_ZIP_SOURCE_KEY_FILE.invoke(e.getKey());
                if (!f.toString().endsWith(".jar")) continue;
                byte[] current_cen = (byte[]) H_ZIP_SOURCE_CEN.invoke(e.getValue());
                HashCode code = Hashing.murmur3_128().hashBytes(current_cen);
                byte[] stored_cen = CEN_CACHE.get(code);
                if (stored_cen == null)
                    CEN_CACHE.put(code, current_cen);
                else
                    H_ZIP_SOURCE_SET_CEN.invoke(e.getValue(), stored_cen);
            }
        } catch (Throwable t) {
            Yammo.LOGGER.warn("error fixing ZipFileSource", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void fixPFSP(FileSystemProvider fileSystemProvider) {
        try {
            var mapFileSystems = (Map<String, PathFileSystem>) H_PFS_FILESYSTEMS.invoke(fileSystemProvider);

            for (var pfs: mapFileSystems.entrySet()) {
                var innerSystem = ((Lazy<FileSystem>) H_PFS_INNERSYSTEM.invoke(pfs.getValue())).get();
                tryUpdateZipFileSystem(pfs.getKey(), innerSystem);
            }
        } catch (Throwable t) {
            Yammo.LOGGER.warn("error fixing PFSP", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void fixUFSP(FileSystemProvider fileSystemProvider) {
        try {
            var mapFileSystems = (Map<String, UnionFileSystem>) H_UFS_FILESYSTEMS.invoke(fileSystemProvider);

            for (var ufs: mapFileSystems.entrySet()) {
                var mapEmbedded = (Map<Path, Object>) H_UFS_EMBEDDED_MAP.invoke(ufs.getValue());
                for (var e: mapEmbedded.entrySet()) {
                    var fs = (FileSystem) H_EMBEDDED_METADATA_FS.invoke(e.getValue());
                    var sbc = tryUpdateZipFileSystem(ufs.getKey(), fs);
                    if (sbc != null) e.setValue(H_EMBEDDED_METADATA_CONSTRUCTOR.invoke(e.getKey(), fs, sbc));
                }
            }
        } catch (Throwable t) {
            Yammo.LOGGER.warn("error fixing UFSP", t);
        }
    }

    private static Map<HashCode, byte[]> CEN_CACHE = new HashMap<>();

    private static SeekableByteChannel tryUpdateZipFileSystem(String keyString, FileSystem fs) throws Throwable {
        SeekableByteChannel sbc = null;
        if (fs.getClass() == ZFS_CLASS) {
            var ch = (SeekableByteChannel) H_ZIP_FILE_SYSTEM_CH.invoke(fs);
            if (ch.getClass() == BAC_CLASS) {
                var buffer = (byte[]) H_BAC_BUFFER.invoke(ch);
                Yammo.LOGGER.info("saved {}kb for {}", buffer.length / 1000, keyString);
                String fileHash = Hashing.murmur3_128().hashBytes(buffer).toString();
                var filePath = BASE_PATH.resolve(fileHash + ".jar");
                if (!Files.exists(filePath)) Files.write(filePath, buffer);
                sbc = Files.newByteChannel(filePath, StandardOpenOption.READ);
                if (sbc instanceof FileChannel) H_FILE_CHANNEL_IMPL_UNINTERRUPTIBLE.invoke(sbc);
                H_ZIP_FILE_SYSTEM_SET_CH.invoke(fs, sbc);
            }
            byte[] current_cen = (byte[]) H_ZIP_FILE_SYSTEM_CEN.invoke(fs);
            HashCode code = Hashing.murmur3_128().hashBytes(current_cen);
            byte[] stored_cen = CEN_CACHE.get(code);
            if (stored_cen == null)
                CEN_CACHE.put(code, current_cen);
            else
                H_ZIP_FILE_SYSTEM_SET_CEN.invoke(fs, stored_cen);
        }
        return sbc;
    }
}
