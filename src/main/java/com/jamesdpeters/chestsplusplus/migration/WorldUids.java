package com.jamesdpeters.chestsplusplus.migration;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * v2 stored worlds by name; v3 stores them by UUID. A loaded world answers directly, otherwise the world folder's {@code uid.dat} does,
 * which covers worlds a plugin such as Multiverse loads after us. Files saved by Paper 26.1.2 or later name the world by key instead; one that
 * isn't loaded yet stays unresolved until it is.
 */
public final class WorldUids {

    private final Server server;
    /** Read only for worlds that aren't loaded. */
    private final Supplier<File> worldContainer;
    /** Only worlds that were found: one missing now may be loaded before the next import. */
    private final Map<String, UUID> cache = new HashMap<>();

    public WorldUids(Server server, Supplier<File> worldContainer) {
        this.server = server;
        this.worldContainer = worldContainer;
    }

    public @Nullable UUID resolve(String name) {
        UUID cached = cache.get(name);
        if (cached != null) return cached;
        UUID found = lookup(name);
        if (found != null) cache.put(name, found);
        return found;
    }

    private @Nullable UUID lookup(String name) {
        NamespacedKey key = key(name);
        if (key != null) {
            World world = server.getWorld(key);
            return world == null ? null : world.getUID();
        }
        World world = server.getWorld(name);
        if (world != null) return world.getUID();
        return fromUidFile(new File(new File(worldContainer.get(), name), "uid.dat"));
    }

    /** The key a v2 location names its world by, or null for a world name. {@code fromString} alone would read a name as {@code minecraft:<name>}. */
    static @Nullable NamespacedKey key(String world) {
        return world.indexOf(':') < 0 ? null : NamespacedKey.fromString(world);
    }

    static @Nullable UUID fromUidFile(File file) {
        if (!file.isFile()) return null;
        try (InputStream in = Files.newInputStream(file.toPath()); DataInputStream data = new DataInputStream(in)) {
            return new UUID(data.readLong(), data.readLong());
        } catch (IOException e) {
            return null;
        }
    }
}
