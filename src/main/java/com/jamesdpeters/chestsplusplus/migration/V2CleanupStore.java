package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.migration.V2CleanupStore.ImportedRow;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.RecordTable;
import com.jamesdpeters.chestsplusplus.persistence.RowStore;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** {@link V2Cleanup}: one {@code v2_blocks} row per imported block, flagged while its world cleanup is pending. */
@RequiredArgsConstructor
public final class V2CleanupStore implements RowStore<BlockPos, ImportedRow> {

    public record ImportedRow(UUID world, int x, int y, int z, boolean pending) {

        BlockPos pos() {
            return new BlockPos(world, x, y, z);
        }
    }

    @Getter private final RecordTable<ImportedRow> table = new RecordTable<>(ImportedRow.class, "v2_blocks", "world", "x", "y", "z");

    @Getter private final Persistence persistence;
    private final V2Cleanup cleanup;

    @Override
    public @Nullable ImportedRow snapshot(BlockPos pos) {
        return cleanup.wasImported(pos) ? new ImportedRow(pos.world(), pos.x(), pos.y(), pos.z(), cleanup.contains(pos)) : null;
    }

    @Override
    public void load(Handle handle) {
        List<ImportedRow> rows = table.all(handle);
        cleanup.load(rows.stream().map(ImportedRow::pos).toList(), rows.stream().filter(ImportedRow::pending).map(ImportedRow::pos).toList());
    }
}
