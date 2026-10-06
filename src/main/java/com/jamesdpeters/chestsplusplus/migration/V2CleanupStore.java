package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.migration.V2CleanupStore.ImportedRow;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.RecordTable;
import com.jamesdpeters.chestsplusplus.persistence.Store;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jspecify.annotations.Nullable;

/** {@link V2Cleanup}: one {@code v2_blocks} row per imported block, flagged while its world cleanup is pending. */
@RequiredArgsConstructor
public final class V2CleanupStore implements Store<BlockPos, ImportedRow> {

    public record ImportedRow(UUID world, int x, int y, int z, boolean pending) {

        BlockPos pos() {
            return new BlockPos(world, x, y, z);
        }
    }

    private static final RecordTable<ImportedRow> BLOCKS = new RecordTable<>(ImportedRow.class, "v2_blocks", "world", "x", "y", "z");

    private final Persistence persistence;
    private final V2Cleanup cleanup;

    public void markDirty(BlockPos pos) {
        persistence.markDirty(this, pos);
    }

    @Override
    public @Nullable ImportedRow snapshot(BlockPos pos) {
        return cleanup.wasImported(pos) ? new ImportedRow(pos.world(), pos.x(), pos.y(), pos.z(), cleanup.contains(pos)) : null;
    }

    @Override
    public void write(Handle handle, List<ImportedRow> rows) {
        BLOCKS.upsert(handle, rows);
    }

    @Override
    public void delete(Handle handle, List<BlockPos> positions) {
        PreparedBatch batch = handle.prepareBatch("DELETE FROM v2_blocks WHERE world = :world AND x = :x AND y = :y AND z = :z");
        for (BlockPos pos : positions) batch.bind("world", pos.world()).bind("x", pos.x()).bind("y", pos.y()).bind("z", pos.z()).add();
        batch.execute();
    }

    @Override
    public void load(Handle handle) {
        List<ImportedRow> rows = BLOCKS.all(handle);
        cleanup.load(rows.stream().map(ImportedRow::pos).toList(), rows.stream().filter(ImportedRow::pending).map(ImportedRow::pos).toList());
    }
}
