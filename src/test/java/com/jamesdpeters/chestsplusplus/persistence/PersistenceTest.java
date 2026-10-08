package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class PersistenceTest {

    public record Entry(String name, String value) {}

    /** An in-memory model of name → value pairs, saved to its own table ({@code entries} by default). */
    private static final class EntryStore implements Store<String, Entry> {

        private final String tableName;
        private final RecordTable<Entry> table;
        final Map<String, String> values = new LinkedHashMap<>();
        volatile boolean failNextWrite;
        volatile boolean failEveryWrite;
        volatile @Nullable String failingName;

        EntryStore() {
            this("entries");
        }

        EntryStore(String tableName) {
            this.tableName = tableName;
            this.table = new RecordTable<>(Entry.class, tableName, "name");
        }

        volatile CountDownLatch gate = new CountDownLatch(0);

        @Override
        public @Nullable Entry snapshot(String name) {
            String value = values.get(name);
            return value == null ? null : new Entry(name, value);
        }

        @Override
        public void write(Handle handle, List<Entry> snapshots) {
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("gate never opened");
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (failNextWrite) {
                failNextWrite = false;
                throw new IllegalStateException("simulated failure");
            }
            if (failEveryWrite || snapshots.stream().anyMatch(entry -> entry.name().equals(failingName))) {
                throw new IllegalStateException("simulated failure");
            }
            table.upsert(handle, snapshots);
        }

        @Override
        public void delete(Handle handle, List<String> names) {
            table.deleteWhere(handle, "name", names);
        }

        @Override
        public void load(Handle handle) {
            handle.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
            table.all(handle).forEach(entry -> values.put(entry.name(), entry.value()));
        }

        void set(Persistence persistence, String name, @Nullable String value) {
            if (value == null) values.remove(name);
            else values.put(name, value);
            persistence.markDirty(this, name);
        }
    }

    @TempDir Path dir;

    private final ConcurrentLinkedQueue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();
    /** Like {@code ChestsPlusPlus.runOnMainThread}, the fake main thread drops tasks once the plugin is disabling. */
    private volatile boolean disabling;
    private final Logger logger = Logger.getLogger(ChestsPlusPlus.NAME);
    private final List<LogRecord> logs = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {

        @Override
        public void publish(LogRecord record) {
            logs.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void captureLogs() {
        logger.addHandler(handler);
    }

    @AfterEach
    void releaseLogs() {
        logger.removeHandler(handler);
    }

    private Database database() {
        return Database.open("jdbc:sqlite:" + dir.resolve("data.db"));
    }

    private Persistence start(Database database, EntryStore... stores) {
        Persistence persistence = new Persistence(database, task -> {
            if (!disabling) mainQueue.add(task);
        });
        for (EntryStore store : stores) persistence.register(store);
        persistence.load();
        return persistence;
    }

    private void flushAndWait(Persistence persistence) throws InterruptedException {
        var flushed = persistence.flush();
        drainUntil(flushed::isDone);
    }

    private void drainUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Runnable task;
            while ((task = mainQueue.poll()) != null) task.run();
            Thread.sleep(5);
        }
        assertThat(done.getAsBoolean()).as("condition reached within 10s").isTrue();
    }

    private void disableAndClose(Persistence persistence) {
        disabling = true;
        persistence.close();
    }

    private Map<String, String> savedRows() {
        try (Database database = database()) {
            return rows(database);
        }
    }

    private List<String> logged(Level level) {
        return logs.stream().filter(record -> record.getLevel() == level).map(LogRecord::getMessage).toList();
    }

    private static Map<String, String> rows(Database database) {
        return rows(database, "entries");
    }

    private static Map<String, String> rows(Database database, String table) {
        Map<String, String> rows = new LinkedHashMap<>();
        database.handle().createQuery("SELECT name, value FROM " + table + " ORDER BY rowid")
                .map((rs, ctx) -> rows.put(rs.getString(1), rs.getString(2)))
                .list();
        return rows;
    }

    @Test
    void flushWritesDirtyKeys() throws InterruptedException {
        EntryStore store = new EntryStore();
        try (Database database = database()) {
            Persistence persistence = start(database, store);
            store.set(persistence, "a", "1");
            store.set(persistence, "b", "2");
            assertThat(persistence.pending()).isEqualTo(2);

            persistence.flush();
            assertThat(persistence.isDirty(store, "a")).isFalse();
            drainUntil(() -> persistence.pending() == 0);

            assertThat(rows(database)).containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
        }
    }

    @Test
    void nullSnapshotDeletes() throws InterruptedException {
        EntryStore store = new EntryStore();
        try (Database database = database()) {
            Persistence persistence = start(database, store);
            store.set(persistence, "a", "1");
            store.set(persistence, "b", "2");
            persistence.flush();
            drainUntil(() -> persistence.pending() == 0);

            store.set(persistence, "a", null);
            persistence.flush();
            drainUntil(() -> persistence.pending() == 0);

            assertThat(rows(database)).containsExactly(Map.entry("b", "2"));
        }
    }

    @Test
    void keyChangedWhileItsBatchIsInFlightIsWrittenAgain() throws InterruptedException {
        EntryStore store = new EntryStore();
        try (Database database = database()) {
            Persistence persistence = start(database, store);
            store.gate = new CountDownLatch(1);
            store.set(persistence, "a", "1");
            persistence.flush();

            store.set(persistence, "a", "2");
            assertThat(persistence.isDirty(store, "a")).isTrue();
            assertThat(persistence.pending()).isEqualTo(2);
            store.gate.countDown();
            drainUntil(() -> persistence.pending() == 1);
            assertThat(rows(database)).containsExactly(Map.entry("a", "1"));

            persistence.flush();
            drainUntil(() -> persistence.pending() == 0);
            assertThat(rows(database)).containsExactly(Map.entry("a", "2"));
        }
    }

    @Test
    void failedWriteMarksKeysDirtyAgainAndTheRetryWritesCurrentState() throws InterruptedException {
        EntryStore store = new EntryStore();
        try (Database database = database()) {
            Persistence persistence = start(database, store);
            store.failNextWrite = true;
            store.set(persistence, "a", "1");
            persistence.flush();
            drainUntil(() -> persistence.isDirty(store, "a"));
            assertThat(rows(database)).isEmpty();

            store.values.put("a", "2");
            persistence.flush();
            drainUntil(() -> persistence.pending() == 0);

            assertThat(rows(database)).containsExactly(Map.entry("a", "2"));
        }
    }

    @Test
    void keyThatCannotBeWrittenDoesNotBlockOtherKeysOrStores() throws InterruptedException {
        EntryStore store = new EntryStore();
        EntryStore other = new EntryStore("others");
        try (Database database = database()) {
            Persistence persistence = start(database, store, other);
            store.failingName = "bad";
            store.set(persistence, "a", "1");
            store.set(persistence, "bad", "2");
            other.set(persistence, "x", "3");
            flushAndWait(persistence);
            assertThat(persistence.pending()).isEqualTo(3);

            flushAndWait(persistence);

            assertThat(rows(database)).containsExactly(Map.entry("a", "1"));
            assertThat(rows(database, "others")).containsExactly(Map.entry("x", "3"));
            assertThat(persistence.isDirty(store, "bad")).isTrue();
            assertThat(persistence.pending()).isEqualTo(1);
        }
    }

    @Test
    void keyIsGivenUpOnAfterTheRetryLimitUntilItChangesAgain() throws InterruptedException {
        EntryStore store = new EntryStore();
        try (Database database = database()) {
            Persistence persistence = start(database, store);
            store.failingName = "bad";
            store.set(persistence, "bad", "1");
            for (int attempt = 0; attempt <= Persistence.MAX_ATTEMPTS; attempt++) {
                assertThat(persistence.isDirty(store, "bad")).isTrue();
                store.set(persistence, "good" + attempt, "1");
                flushAndWait(persistence);
            }
            assertThat(persistence.pending()).isZero();
            assertThat(rows(database)).containsOnlyKeys("good0", "good1", "good2", "good3");

            store.failingName = null;
            store.set(persistence, "bad", "2");
            flushAndWait(persistence);

            assertThat(rows(database)).containsEntry("bad", "2");
        }
    }

    @Test
    void databaseWideFailureIsRetriedRatherThanGivenUpOn() throws InterruptedException {
        EntryStore store = new EntryStore();
        try (Database database = database()) {
            Persistence persistence = start(database, store);
            store.failEveryWrite = true;
            store.set(persistence, "a", "1");
            store.set(persistence, "b", "2");
            for (int attempt = 0; attempt <= Persistence.MAX_ATTEMPTS; attempt++) flushAndWait(persistence);
            assertThat(persistence.pending()).isEqualTo(2);

            store.failEveryWrite = false;
            flushAndWait(persistence);

            assertThat(rows(database)).containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
        }
    }

    @Test
    void closeDrainsEverythingAndLoadReadsItBack() {
        EntryStore first = new EntryStore();
        Persistence persistence = start(database(), first);
        first.set(persistence, "a", "1");
        persistence.flush();
        first.set(persistence, "b", "2");
        persistence.close();

        EntryStore second = new EntryStore();
        start(database(), second).close();

        assertThat(second.values).containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
    }

    @Test
    void finalWriteThatFailsDuringCloseIsLoggedAndRetried() {
        EntryStore store = new EntryStore();
        Persistence persistence = start(database(), store);
        store.failNextWrite = true;
        store.set(persistence, "a", "1");

        disableAndClose(persistence);

        assertThat(logged(Level.WARNING)).anyMatch(message -> message.startsWith("Failed to save 1 ChestsPlusPlus change(s)"));
        assertThat(logged(Level.SEVERE)).isEmpty();
        assertThat(savedRows()).containsExactly(Map.entry("a", "1"));
    }

    @Test
    void batchStillBeingWrittenWhenCloseStartsIsRetriedIfItFails() {
        EntryStore store = new EntryStore();
        Persistence persistence = start(database(), store);
        store.gate = new CountDownLatch(1);
        store.failNextWrite = true;
        store.set(persistence, "a", "1");
        persistence.flush();
        CompletableFuture.runAsync(store.gate::countDown, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));

        disableAndClose(persistence);

        assertThat(logged(Level.SEVERE)).isEmpty();
        assertThat(savedRows()).containsExactly(Map.entry("a", "1"));
    }

    @Test
    void storeThatAlwaysFailsGivesUpAtCloseAndLogsAnError() {
        EntryStore store = new EntryStore();
        EntryStore other = new EntryStore("others");
        Persistence persistence = start(database(), store, other);
        store.failEveryWrite = true;
        store.set(persistence, "a", "1");
        store.set(persistence, "b", "2");

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> disableAndClose(persistence));

        assertThat(logged(Level.SEVERE)).containsExactly("Failed to save 2 ChestsPlusPlus change(s) before shutdown: {EntryStore=2}");
        assertThat(savedRows()).isEmpty();
    }
}
