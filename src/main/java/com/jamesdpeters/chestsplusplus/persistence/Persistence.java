package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.JdbiException;
import org.jspecify.annotations.Nullable;

/**
 * Write-behind persistence. The in-memory model is authoritative: stores mark keys dirty, and a flush snapshots them on the main thread
 * and writes them in one transaction on a single I/O thread. Batches run in order, so a key that changes while its batch is being written
 * is simply saved again by a later flush. A failed batch marks its keys dirty again, and the retry snapshots their current state.
 * <p>
 * A retry that fails again is split up, one transaction per store and then per key, so a key that can never be written doesn't block the
 * rest. Such a key is given up on after {@link #MAX_ATTEMPTS} failures, until it next changes. A failure only counts against a key when
 * something else committed, so a database outage retries everything instead of giving up on it.
 * {@link #markDirty}, {@link #flush} and {@link #load} are main-thread only.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class Persistence {

    static final int MAX_ATTEMPTS = 3;

    private final Database database;
    /** Runs a task on the server thread; used for write completions. */
    private final Consumer<Runnable> mainThread;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ChestsPlusPlus-persistence-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Store<?, ?>, Set<?>> dirty = new LinkedHashMap<>();
    private final Map<StoreKey, Integer> failures = new HashMap<>();
    private int inFlight;
    private boolean failing;

    public void register(Store<?, ?> store) {
        dirty.put(store, new LinkedHashSet<>());
    }

    /** One hash-set add. */
    public <K> void markDirty(Store<K, ?> store, K key) {
        keys(store).add(key);
    }

    public boolean isDirty(Store<?, ?> store, Object key) {
        return keys(store).contains(key);
    }

    /** Dirty keys plus keys in batches still being written. */
    public int pending() {
        return inFlight + dirty.values().stream().mapToInt(Set::size).sum();
    }

    /** Runs each store's load, in registration order, before anything is flushed. */
    public void load() {
        dirty.keySet().forEach(store -> store.load(database.handle()));
    }

    /** Writes every dirty key. The future completes on the main thread once they are committed, or fails if any of them failed. */
    public CompletableFuture<Void> flush() {
        dirty.keySet().forEach(Store::beforeFlush);
        List<Batch<?, ?>> batches = dirty.keySet().stream().<Batch<?, ?>>map(this::take).filter(batch -> !batch.writes().isEmpty()).toList();
        int keys = batches.stream().mapToInt(batch -> batch.writes().size()).sum();
        if (keys == 0) return CompletableFuture.completedFuture(null);
        inFlight += keys;
        boolean isolate = failing || batches.stream().flatMap(Batch::keys).anyMatch(failures::containsKey);
        CompletableFuture<Void> done = new CompletableFuture<>();
        io.execute(() -> {
            Attempts attempts = write(batches, isolate);
            mainThread.accept(() -> written(batches, keys, attempts, done));
        });
        return done;
    }

    /** Flushes, waits for the I/O thread to finish, and closes the database. A failed final write is logged. */
    public void close() {
        flush();
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) log.warn("ChestsPlusPlus persistence did not finish saving within 30s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            database.close();
        } catch (JdbiException e) {
            log.warn("Closing the ChestsPlusPlus database failed", e);
        }
    }

    private Attempts write(List<Batch<?, ?>> batches, boolean isolate) {
        Attempts attempts = new Attempts();
        if (attempts.run(batches)) return attempts;
        if (isolate) batches.forEach(attempts::isolate);
        else attempts.failAll(batches);
        return attempts;
    }

    private void written(List<Batch<?, ?>> batches, int keys, Attempts attempts, CompletableFuture<Void> done) {
        inFlight -= keys;
        batches.stream().flatMap(Batch::keys).filter(key -> !attempts.failed.containsKey(key)).forEach(failures::remove);
        if (attempts.failed.isEmpty()) {
            failing = false;
            done.complete(null);
            return;
        }
        RuntimeException error = attempts.failed.values().iterator().next();
        if (!failing) log.warn("Failed to save {} ChestsPlusPlus change(s); will retry", attempts.failed.size(), error);
        failing = true;
        attempts.failed.forEach((key, keyError) -> retryOrGiveUp(key, keyError, attempts.committed));
        done.completeExceptionally(error);
    }

    private void retryOrGiveUp(StoreKey key, RuntimeException error, boolean databaseWorks) {
        int failed = databaseWorks ? failures.merge(key, 1, Integer::sum) : 0;
        if (failed < MAX_ATTEMPTS) {
            this.<Object>keys(key.store()).add(key.key());
            return;
        }
        log.error("Gave up saving {} {} after {} failed attempts; it is saved again when it next changes", key.store().getClass().getSimpleName(),
                key.key(), failed, error);
    }

    private <K, S> Batch<K, S> take(Store<K, S> store) {
        Set<K> dirtyKeys = keys(store);
        List<Write<K, S>> writes = dirtyKeys.stream().map(key -> new Write<>(key, store.snapshot(key))).toList();
        dirtyKeys.clear();
        return new Batch<>(store, writes);
    }

    @SuppressWarnings("unchecked")
    private <K> Set<K> keys(Store<?, ?> store) {
        Set<?> keys = dirty.get(store);
        if (keys == null) throw new IllegalStateException(store.getClass().getSimpleName() + " is not registered");
        return (Set<K>) keys;
    }

    /** The transactions one flush tries on the I/O thread, and the keys that failed. */
    private final class Attempts {

        final Map<StoreKey, RuntimeException> failed = new LinkedHashMap<>();
        boolean committed;
        @Nullable RuntimeException lastError;

        boolean run(List<Batch<?, ?>> batches) {
            try {
                database.handle().useTransaction(handle -> batches.forEach(batch -> batch.apply(handle)));
                committed = true;
                return true;
            } catch (RuntimeException e) {
                lastError = e;
                return false;
            }
        }

        void isolate(Batch<?, ?> batch) {
            if (run(List.of(batch))) return;
            batch.singles().stream().filter(single -> !run(List.of(single))).forEach(single -> failAll(List.of(single)));
        }

        void failAll(List<Batch<?, ?>> batches) {
            RuntimeException error = Objects.requireNonNull(lastError);
            batches.stream().flatMap(Batch::keys).forEach(key -> failed.put(key, error));
        }
    }

    private record StoreKey(Store<?, ?> store, Object key) {}

    private record Write<K, S>(K key, @Nullable S snapshot) {}

    private record Batch<K, S>(Store<K, S> store, List<Write<K, S>> writes) {

        Stream<StoreKey> keys() {
            return writes.stream().map(write -> new StoreKey(store, write.key()));
        }

        List<Batch<K, S>> singles() {
            return writes.stream().map(write -> new Batch<>(store, List.of(write))).toList();
        }

        void apply(Handle handle) {
            List<K> deletes = writes.stream().filter(write -> write.snapshot() == null).map(Write::key).toList();
            List<S> snapshots = writes.stream().map(Write::snapshot).filter(Objects::nonNull).toList();
            if (!deletes.isEmpty()) store.delete(handle, deletes);
            if (!snapshots.isEmpty()) store.write(handle, snapshots);
        }
    }
}
