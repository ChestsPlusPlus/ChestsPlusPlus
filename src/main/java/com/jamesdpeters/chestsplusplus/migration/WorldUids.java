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
import org.bukkit.Server;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * v2 stored worlds by name; v3 stores them by UUID. A loaded world answers directly, otherwise the world folder's {@code uid.dat} does,
 * which covers worlds a plugin such as Multiverse loads after us.
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
        World world = server.getWorld(name);
        if (world != null) return world.getUID();
        return fromUidFile(new File(new File(worldContainer.get(), name), "uid.dat"));
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
