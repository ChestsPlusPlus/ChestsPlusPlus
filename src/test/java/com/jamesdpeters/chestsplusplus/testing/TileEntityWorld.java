package com.jamesdpeters.chestsplusplus.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.world.ChunkCoordinate;
import org.mockbukkit.mockbukkit.world.ChunkMock;
import org.mockbukkit.mockbukkit.world.Coordinate;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** MockBukkit's chunks don't enumerate tile entities; this world enumerates the blocks created by the test. */
public final class TileEntityWorld extends WorldMock {

    private final List<BlockMock> blocks = new ArrayList<>();

    @Override
    public BlockMock createBlock(Coordinate coordinate) {
        BlockMock block = super.createBlock(coordinate);
        blocks.add(block);
        return block;
    }

    @Override
    public ChunkMock getChunkAt(ChunkCoordinate coordinate) {
        super.getChunkAt(coordinate);
        return new TileChunk(coordinate.getX(), coordinate.getZ());
    }

    @Override
    public Chunk[] getLoadedChunks() {
        return Arrays.stream(super.getLoadedChunks()).map(chunk -> getChunkAt(chunk.getX(), chunk.getZ())).toArray(Chunk[]::new);
    }

    private final class TileChunk extends ChunkMock {

        private TileChunk(int x, int z) {
            super(TileEntityWorld.this, x, z);
        }

        @Override
        public Collection<BlockState> getTileEntities(Predicate<? super Block> filter, boolean useSnapshot) {
            return blocks.stream()
                    .filter(block -> block.getX() >> 4 == getX() && block.getZ() >> 4 == getZ())
                    .filter(filter)
                    .map(block -> block.getState(useSnapshot))
                    .filter(TileState.class::isInstance)
                    .toList();
        }
    }
}
