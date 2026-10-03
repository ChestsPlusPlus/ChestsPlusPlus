package com.jamesdpeters.chestsplusplus.core;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * An immutable block position in a world. {@link #packed()} is a per-world {@code long} key (x and z 26 bits, y 12
 * bits, all two's complement) and {@link #chunkKey()} identifies the containing chunk.
 */
public record BlockPos(UUID world, int x, int y, int z) {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;

    public static final int MIN_XZ = -(1 << (XZ_BITS - 1));
    public static final int MAX_XZ = (1 << (XZ_BITS - 1)) - 1;
    public static final int MIN_Y = -(1 << (Y_BITS - 1));
    public static final int MAX_Y = (1 << (Y_BITS - 1)) - 1;

    public BlockPos {
        if (x < MIN_XZ || x > MAX_XZ || z < MIN_XZ || z > MAX_XZ || y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("Position out of range: " + x + "," + y + "," + z);
        }
    }

    public static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalArgumentException("Location has no world");
        return new BlockPos(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Reverses {@link #packed()}. */
    public static BlockPos unpack(UUID world, long packed) {
        int x = (int) (packed >> X_SHIFT);
        int z = (int) ((packed << (64 - X_SHIFT)) >> (64 - XZ_BITS));
        int y = (int) ((packed << (64 - Y_BITS)) >> (64 - Y_BITS));
        return new BlockPos(world, x, y, z);
    }

    public long packed() {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public long chunkKey() {
        return chunkKey(chunkX(), chunkZ());
    }

    /** Same layout as Paper's {@code Chunk#getChunkKey()}. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(world, x + dx, y + dy, z + dz);
    }

    public @Nullable Block block() {
        World w = org.bukkit.Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    public @Nullable Location center() {
        World w = org.bukkit.Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean isLoaded() {
        World w = org.bukkit.Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded(chunkX(), chunkZ());
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
