package com.jamesdpeters.chestsplusplus.migration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * The chunks a world has on disk, read from its region file headers (Paper has no API for this). Entity region files are preferred: only
 * chunks with entities can hold item frames, so most of the world is skipped.
 */
final class RegionFiles {

    /** A chunk's coordinates. */
    record ChunkCoord(int x, int z) {}

    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int HEADER_ENTRIES = 1024;

    private RegionFiles() {}

    static List<ChunkCoord> chunks(World world) {
        File folder = folder(world, "entities");
        if (folder == null) folder = folder(world, "region");
        return folder == null ? List.of() : chunksIn(folder);
    }

    /** Every saved chunk in a folder of region files. */
    static List<ChunkCoord> chunksIn(File folder) {
        List<ChunkCoord> out = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) throw new IllegalStateException("Cannot list region files in " + folder);
        for (File file : files) {
            Matcher name = REGION_NAME.matcher(file.getName());
            if (name.matches()) readHeader(file, Integer.parseInt(name.group(1)), Integer.parseInt(name.group(2)), out);
        }
        return out;
    }

    /** The dimension's {@code kind} folder: the per-dimension layout first, then the older {@code DIM-1}/{@code DIM1} and root layouts. */
    private static @Nullable File folder(World world, String kind) {
        File root = world.getWorldFolder();
        NamespacedKey key = world.getKey();
        List<File> candidates = List.of(new File(root, "dimensions/" + key.getNamespace() + "/" + key.getKey() + "/" + kind),
                new File(root, switch (world.getEnvironment()) {
                    case NETHER -> "DIM-1/" + kind;
                    case THE_END -> "DIM1/" + kind;
                    default -> kind;
                }));
        return candidates.stream().filter(File::isDirectory).findFirst().orElse(null);
    }

    /** The header holds one 4-byte location per chunk; zero means the chunk was never saved. */
    private static void readHeader(File file, int regionX, int regionZ, List<ChunkCoord> out) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] header = in.readNBytes(HEADER_ENTRIES * 4);
            if (header.length < HEADER_ENTRIES * 4) throw new IOException("Incomplete region header: " + file);
            ByteBuffer buffer = ByteBuffer.wrap(header);
            for (int i = 0; i < HEADER_ENTRIES; i++) {
                if (buffer.getInt() != 0) out.add(new ChunkCoord(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
