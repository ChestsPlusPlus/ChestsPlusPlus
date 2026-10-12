package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.persistence.PendingChanges.Key;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;

/** Snapshots captured on the main thread, with only copied data passed to the I/O thread. */
record WriteBatch<K, S>(Store<K, S> store, List<Write<K, S>> writes) {

    record Write<K, S>(K key, @Nullable S snapshot) {}

    Stream<Key> keys() {
        return writes.stream().map(write -> new Key(store, write.key()));
    }

    WriteBatch<K, S> only(Predicate<Key> keep) {
        return new WriteBatch<>(store, writes.stream().filter(write -> keep.test(new Key(store, write.key()))).toList());
    }

    List<WriteBatch<K, S>> singles() {
        return writes.stream().map(write -> new WriteBatch<>(store, List.of(write))).toList();
    }

    void apply(Handle handle) {
        List<K> deletes = writes.stream().filter(write -> write.snapshot() == null).map(Write::key).toList();
        List<S> snapshots = writes.stream().map(Write::snapshot).filter(Objects::nonNull).toList();
        if (!deletes.isEmpty()) store.delete(handle, deletes);
        if (!snapshots.isEmpty()) store.write(handle, snapshots);
    }

    static List<WriteBatch<?, ?>> only(List<WriteBatch<?, ?>> batches, Predicate<Key> keep) {
        return batches.stream().<WriteBatch<?, ?>>map(batch -> batch.only(keep)).filter(batch -> !batch.writes().isEmpty()).toList();
    }

    static Set<Key> keys(List<WriteBatch<?, ?>> batches) {
        return batches.stream().flatMap(WriteBatch::keys).collect(Collectors.toSet());
    }
}
