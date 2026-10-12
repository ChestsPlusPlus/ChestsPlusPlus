package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.persistence.PendingChanges.Flush;
import com.jamesdpeters.chestsplusplus.persistence.PendingChanges.Unit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** Runs one flush on the I/O thread, isolating failures without splitting changes that belong together. */
@RequiredArgsConstructor
final class BatchWriter {

    record Result(Map<PendingChanges.Key, RuntimeException> failed, boolean committed) {}

    private final Handle handle;
    private final Map<PendingChanges.Key, RuntimeException> failed = new LinkedHashMap<>();
    private boolean committed;
    private @Nullable RuntimeException lastError;

    Result write(Flush flush) {
        if (!run(flush.batches())) {
            if (flush.isolate()) isolate(flush.batches(), flush.units());
            else failAll(flush.batches());
        }
        return new Result(Collections.unmodifiableMap(new LinkedHashMap<>(failed)), committed);
    }

    private boolean run(List<WriteBatch<?, ?>> batches) {
        try {
            handle.useTransaction(transaction -> batches.forEach(batch -> batch.apply(transaction)));
            committed = true;
            return true;
        } catch (RuntimeException e) {
            lastError = e;
            return false;
        }
    }

    private void isolate(List<WriteBatch<?, ?>> batches, List<Unit> together) {
        for (Unit unit : together) {
            List<WriteBatch<?, ?>> part = WriteBatch.only(batches, unit.keys()::contains);
            if (!part.isEmpty() && !run(part)) failAll(part);
        }
        WriteBatch.only(batches, key -> together.stream().noneMatch(unit -> unit.keys().contains(key))).forEach(this::isolate);
    }

    private void isolate(WriteBatch<?, ?> batch) {
        if (run(List.of(batch))) return;
        batch.singles().stream().filter(single -> !run(List.of(single))).forEach(single -> failAll(List.of(single)));
    }

    private void failAll(List<WriteBatch<?, ?>> batches) {
        RuntimeException error = Objects.requireNonNull(lastError);
        batches.stream().flatMap(WriteBatch::keys).forEach(key -> failed.put(key, error));
    }
}
