package com.jamesdpeters.chestsplusplus.persistence;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.persistence.BatchWriter.Result;
import com.jamesdpeters.chestsplusplus.persistence.PendingChanges.Flush;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.JdbiException;

/** Write-behind persistence: main-thread snapshots, ordered I/O transactions and main-thread completions. */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class Persistence {

    static final int MAX_ATTEMPTS = 3;
    private static final int CLOSE_ROUNDS = 3;
    private static final int CLOSE_TIMEOUT_SECONDS = 30;

    private final Database database;
    /** The scheduler can drop tasks during disable; close drains the completion queue itself. */
    private final Consumer<Runnable> mainThread;
    private final PendingChanges changes = new PendingChanges();
    private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ChestsPlusPlus-persistence-io");
        thread.setDaemon(true);
        return thread;
    });
    private int inFlight;

    public <S extends Store<?, ?>> S register(S store) {
        changes.register(store);
        return store;
    }

    /** Main thread: one hash-set add outside together. */
    public <K> void markDirty(Store<K, ?> store, K key) {
        changes.markDirty(store, key);
    }

    /**
     * Saves the keys marked by changes as one transaction, including retries and later edits until they commit. Nested or overlapping
     * changes share a unit. This groups persistence only: if changes throws, it leaves model edits in place and saves their keys independently.
     */
    public <T> T together(Supplier<T> changes) {
        return this.changes.together(changes);
    }

    public void together(Runnable changes) {
        together(() -> {
            changes.run();
            return null;
        });
    }

    public boolean isDirty(Store<?, ?> store, Object key) {
        return changes.isDirty(store, key);
    }

    /** Dirty keys plus keys in batches still being written. */
    public int pending() {
        return inFlight + changes.size();
    }

    /** Main thread, at startup, in registration order. */
    public void load() {
        changes.load(database);
    }

    /**
     * Main thread: snapshots dirty keys. The future completes on the main thread once they commit, or fails if any failed. Failed keys
     * are logged and retried by the next flush, so callers that don't need to know can ignore it.
     */
    @CanIgnoreReturnValue
    public CompletableFuture<Void> flush() {
        Flush flush = changes.take();
        if (flush.size() == 0) return CompletableFuture.completedFuture(null);
        inFlight += flush.size();
        CompletableFuture<Void> done = new CompletableFuture<>();
        io.execute(() -> {
            Result result = new BatchWriter(database.handle()).write(flush);
            completions.add(() -> written(flush, result, done));
            mainThread.accept(this::runCompletions);
        });
        return done;
    }

    /** Main thread: drains writes and completions, retries failures, then closes the database. */
    public void close() {
        for (int round = 0; round < CLOSE_ROUNDS; round++) {
            flush();
            if (!awaitIdle()) break;
            runCompletions();
            if (pending() == 0) break;
        }
        if (pending() > 0) log.error("Failed to save {} ChestsPlusPlus change(s) before shutdown: {}", pending(), unsaved());
        io.shutdown();
        try {
            database.close();
        } catch (JdbiException e) {
            log.warn("Closing the ChestsPlusPlus database failed", e);
        }
    }

    private void written(Flush flush, Result result, CompletableFuture<Void> done) {
        inFlight -= flush.size();
        RuntimeException error = changes.written(flush, result);
        if (error == null) done.complete(null);
        else done.completeExceptionally(error);
    }

    private void runCompletions() {
        Runnable completion;
        while ((completion = completions.poll()) != null) completion.run();
    }

    /** Waits for every submitted write without stopping the I/O thread, so close can retry. */
    private boolean awaitIdle() {
        try {
            CompletableFuture.runAsync(() -> {}, io).orTimeout(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
            return true;
        } catch (CompletionException e) {
            log.warn("ChestsPlusPlus persistence did not finish saving within {}s", CLOSE_TIMEOUT_SECONDS);
            return false;
        }
    }

    private Map<String, Integer> unsaved() {
        Map<String, Integer> unsaved = changes.unsaved();
        if (inFlight > 0) unsaved.put("still being written", inFlight);
        return unsaved;
    }
}
