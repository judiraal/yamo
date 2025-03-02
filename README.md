# Yet Another Memory Optimization (YAMO)

YAMO is an attempt to further optimize memory usage. It
has two main features that can cut memory in (large) modpacks. The methods
employed are somewhat hacky, so no guarantees that this works everywhere.

To fully experience what YAMO can do to free up memory you should create a new
world with your pack, fly around a bit in creative, wait for a minute and then
close and restart the game (for an explanation, see below).

### In-memory JAR files

Mods are distributed as JAR files. The JAR loading mechanism that is part of
NeoForge (and Forge) loads some inner JAR resources by keeping the entire file
in memory. Most mods don't use this inner JAR mechanism, but some do. This can
add up to hundreds of MBs in a large modpack.

The solution in YAMO is to store the in-memory JAR on disk in the 'cache/mods'
subdirectory. Changes in the original JARs should be detected and the cache dir
should be updated correctly. Deleting the cache dir is also fine, since it will
be rebuild on the next run.

A further improvement to the JAR loading is in the in-memory index of the
underlying ZIP structure. The JARs may be loaded multiple times from different
places (might depend on JDK). When this is detected the ZIP index is deduped
(which can be a couple of MBs for larger mods, e.g. MineColonies).

### Attempt to load the DFU (DataFixerUpper) only when required

The DFU is responsible for upgrading NBT from older game versions to the
current version on-the-fly. This is required when you load an old world where
chunks, including blocks and entities, require updating. It is a complex
rule-based process which requires 100Mb+ of RAM to store the rules efficiently
so that the updating itself is fast. It has been much improved in terms of
loading speed as well, so the need to load it lazily is smaller. To save memory
this mod still attempts to load it only when required.

Because most mods that contain structures have NBT data from older versions it
turns out that the DFU is required very quickly when you have some of those mods
in your pack. When YAMO detects an old NBT structure it loads the DFU
dynamically and then attempts to write all updated NBT structure data to the
'cache/structures' subdirectory. This process can take up to a minute and
happens mostly in the background. The result is that all structures in your
modpack are now available in the most recent NBT version and the DFU would not
be required on the next run.

