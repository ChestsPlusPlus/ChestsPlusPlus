package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.migration.RegionFiles.ChunkCoord;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class RegionFilesTest {

    @TempDir Path dir;

    /** A region file whose header marks the given local chunk indexes (x + z * 32) as saved. */
    private void region(String name, int... saved) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4096 * 2);
        for (int index : saved) header.putInt(index * 4, 2 << 8 | 1);
        Files.write(dir.resolve(name), header.array());
    }

    @Test
    void listsTheChunksEachRegionHeaderMarksAsSaved() throws IOException {
        region("r.0.0.mca", 0, 33);
        region("r.-1.2.mca", 1023);
        Files.write(dir.resolve("r.5.5.mca"), new byte[10]);
        Files.writeString(dir.resolve("notes.txt"), "not a region");

        assertThat(RegionFiles.chunksIn(dir.toFile())).containsExactlyInAnyOrder(new ChunkCoord(0, 0), new ChunkCoord(1, 1), new ChunkCoord(-1, 95));
    }
}
