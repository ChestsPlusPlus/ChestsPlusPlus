package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.JdbiException;

/**
 * Write-behind persistence. The in-memory model is authoritative: stores mark keys dirty, and a flush snapshots them on the main thread
 * and writes them in one transaction on a single I/O thread. Batches run in order, so a key that changes while its batch is being written
 * is simply saved again by a later flush. A failed batch marks its keys dirty again, and the retry snapshots their current state.
 * {@link #markDirty}, {@link #flush} and {@link #load} are main-thread only.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class Persistence {

    private final Database database;
    /** Runs a task on the server thread; used for write completions. */
    private final Consumer<Runnable> mainThread;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ChestsPlusPlus-persistence-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Store<?, ?>, Set<?>> dirty = new LinkedHashMap<>();
    private int inFlight;

    public void register(Store<?, ?> store) {
        dirty.put(store, new LinkedHashSet<>());
    }

    /** One hash-set add: cheap enough for the hopper hot path. */
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

    public void flush() {
        List<Batch<?, ?>> batches = dirty.keySet().stream().<Batch<?, ?>>map(this::take).filter(batch -> !batch.keys().isEmpty()).toList();
        int keys = batches.stream().mapToInt(batch -> batch.keys().size()).sum();
        if (keys == 0) return;
        inFlight += keys;
        CompletableFuture.runAsync(() -> database.handle().useTransaction(handle -> batches.forEach(batch -> batch.apply(handle))), io)
                .whenComplete((ok, error) -> {
                    if (error != null) log.error("Failed to save {} ChestsPlusPlus change(s); will retry", keys, error);
                    mainThread.accept(() -> written(batches, keys, error != null));
                });
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

    private void written(List<Batch<?, ?>> batches, int keys, boolean failed) {
        inFlight -= keys;
        if (failed) batches.forEach(this::requeue);
    }

    private <K> void requeue(Batch<K, ?> batch) {
        keys(batch.store()).addAll(batch.keys());
    }

    private <K, S> Batch<K, S> take(Store<K, S> store) {
        Set<K> dirtyKeys = keys(store);
        List<K> keys = List.copyOf(dirtyKeys);
        dirtyKeys.clear();
        List<S> snapshots = new ArrayList<>();
        List<K> deletes = new ArrayList<>();
        for (K key : keys) {
            S snapshot = store.snapshot(key);
            if (snapshot == null) deletes.add(key);
            else snapshots.add(snapshot);
        }
        return new Batch<>(store, keys, snapshots, deletes);
    }

    @SuppressWarnings("unchecked")
    private <K> Set<K> keys(Store<K, ?> store) {
        Set<?> keys = dirty.get(store);
        if (keys == null) throw new IllegalStateException(store.getClass().getSimpleName() + " is not registered");
        return (Set<K>) keys;
    }

    private record Batch<K, S>(Store<K, S> store, List<K> keys, List<S> snapshots, List<K> deletes) {

        void apply(Handle handle) {
            if (!deletes.isEmpty()) store.delete(handle, deletes);
            if (!snapshots.isEmpty()) store.write(handle, snapshots);
        }
    }
}
