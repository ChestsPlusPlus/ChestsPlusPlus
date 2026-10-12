package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.persistence.BatchWriter.Result;
import com.jamesdpeters.chestsplusplus.persistence.WriteBatch.Write;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/** Main-thread dirty tracking, atomic change units and retry budgets. */
@Slf4j(topic = ChestsPlusPlus.NAME)
final class PendingChanges {

    record Key(Store<?, ?> store, Object key) {

        @Override
        public String toString() {
            return store.getClass().getSimpleName() + " " + key;
        }
    }

    record Unit(Set<Key> keys) {

        @Override
        public String toString() {
            return keys.size() == 1 ? keys.iterator().next().toString() : keys.size() + " change(s) made together";
        }
    }

    record Flush(List<WriteBatch<?, ?>> batches, List<Unit> units, boolean isolate) {

        int size() {
            return batches.stream().mapToInt(batch -> batch.writes().size()).sum();
        }
    }

    private final Map<Store<?, ?>, Set<?>> dirty = new LinkedHashMap<>();
    private final Map<Unit, Integer> failures = new HashMap<>();
    private final List<Unit> units = new ArrayList<>();
    private @Nullable Set<Key> recording;
    private boolean failing;

    void register(Store<?, ?> store) {
        if (dirty.putIfAbsent(store, new LinkedHashSet<>()) != null) {
            throw new IllegalStateException(store.getClass().getSimpleName() + " is already registered");
        }
    }

    <K> void markDirty(Store<K, ?> store, K key) {
        keys(store).add(key);
        if (recording != null) recording.add(new Key(store, key));
    }

    boolean isDirty(Store<?, ?> store, Object key) {
        return keys(store).contains(key);
    }

    int size() {
        return dirty.values().stream().mapToInt(Set::size).sum();
    }

    void load(Database database) {
        dirty.keySet().forEach(store -> store.load(database.handle()));
    }

    <T> T together(Supplier<T> changes) {
        Set<Key> changed = new LinkedHashSet<>();
        Set<Key> outer = recording;
        recording = changed;
        T result;
        try {
            result = changes.get();
        } finally {
            recording = outer;
            if (outer != null) outer.addAll(changed);
        }
        if (outer == null && !changed.isEmpty()) remember(changed);
        return result;
    }

    /** Overlapping units share a transaction; keeping them disjoint also makes retries count once. */
    private void remember(Set<Key> changed) {
        Iterator<Unit> iterator = units.iterator();
        while (iterator.hasNext()) {
            Unit unit = iterator.next();
            if (unit.keys().stream().noneMatch(changed::contains)) continue;
            changed.addAll(unit.keys());
            failures.remove(unit);
            iterator.remove();
        }
        units.add(new Unit(Set.copyOf(changed)));
    }

    Flush take() {
        dirty.keySet().forEach(Store::beforeFlush);
        units.forEach(this::pullTogether);
        List<WriteBatch<?, ?>> batches = dirty.keySet().stream().<WriteBatch<?, ?>>map(this::take)
                .filter(batch -> !batch.writes().isEmpty())
                .toList();
        Set<Key> changed = WriteBatch.keys(batches);
        boolean isolate = failing || failures.keySet().stream().anyMatch(unit -> unit.keys().stream().anyMatch(changed::contains));
        return new Flush(batches, List.copyOf(units), isolate);
    }

    @Nullable
    RuntimeException written(Flush flush, Result result) {
        Set<Key> saved = WriteBatch.keys(flush.batches());
        saved.removeAll(result.failed().keySet());
        failures.keySet().removeIf(unit -> saved.containsAll(unit.keys()));
        flush.units().stream().filter(unit -> saved.containsAll(unit.keys()) && unit.keys().stream().noneMatch(this::isDirty)).forEach(units::remove);
        if (result.failed().isEmpty()) {
            failing = false;
            return null;
        }
        RuntimeException error = result.failed().values().iterator().next();
        if (!failing) log.warn("Failed to save {} ChestsPlusPlus change(s); will retry", result.failed().size(), error);
        failing = true;
        retry(flush, result);
        return error;
    }

    Map<String, Integer> unsaved() {
        Map<String, Integer> unsaved = new LinkedHashMap<>();
        dirty.forEach((store, keys) -> {
            if (!keys.isEmpty()) unsaved.merge(store.getClass().getSimpleName(), keys.size(), Integer::sum);
        });
        return unsaved;
    }

    private void retry(Flush flush, Result result) {
        List<Unit> failedUnits = flush.units().stream().filter(unit -> unit.keys().stream().anyMatch(result.failed()::containsKey)).toList();
        result.failed().forEach((key, error) -> {
            if (failedUnits.stream().noneMatch(unit -> unit.keys().contains(key))) retry(new Unit(Set.of(key)), error, result.committed());
        });
        for (Unit unit : failedUnits) {
            retain(unit);
            RuntimeException error = unit.keys().stream().filter(result.failed()::containsKey).map(result.failed()::get).findFirst().orElseThrow();
            retry(unit, error, result.committed());
        }
    }

    /** An earlier flush may have committed and retired this unit while a later flush of it was still in flight. */
    private void retain(Unit unit) {
        if (units.stream().noneMatch(pending -> pending.keys().containsAll(unit.keys()))) remember(new LinkedHashSet<>(unit.keys()));
    }

    private void retry(Unit unit, RuntimeException error, boolean databaseWorks) {
        int failed = databaseWorks ? failures.merge(unit, 1, Integer::sum) : 0;
        if (failed < Persistence.MAX_ATTEMPTS) {
            unit.keys().forEach(key -> dirtyKeys(key).add(key.key()));
            return;
        }
        failures.remove(unit);
        log.error("Gave up saving {} after {} failed attempts; it is saved again when it next changes", unit, failed, error);
    }

    private void pullTogether(Unit unit) {
        if (unit.keys().stream().anyMatch(this::isDirty)) unit.keys().forEach(key -> dirtyKeys(key).add(key.key()));
    }

    private boolean isDirty(Key key) {
        return dirtyKeys(key).contains(key.key());
    }

    private <K, S> WriteBatch<K, S> take(Store<K, S> store) {
        Set<K> dirtyKeys = keys(store);
        List<Write<K, S>> writes = dirtyKeys.stream().map(key -> new Write<>(key, store.snapshot(key))).toList();
        dirtyKeys.clear();
        return new WriteBatch<>(store, writes);
    }

    private Set<Object> dirtyKeys(Key key) {
        return keys(key.store());
    }

    @SuppressWarnings("unchecked")
    private <K> Set<K> keys(Store<?, ?> store) {
        Set<?> keys = dirty.get(store);
        if (keys == null) throw new IllegalStateException(store.getClass().getSimpleName() + " is not registered");
        return (Set<K>) keys;
    }
}
