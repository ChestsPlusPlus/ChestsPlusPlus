package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class PersistenceTest {

    public record Entry(String name, String value) {}

    /** An in-memory model of name → value pairs, saved to an {@code entries} table. */
    private static final class EntryStore implements Store<String, Entry> {

        private final RecordTable<Entry> table = new RecordTable<>(Entry.class, "entries", "name");
        final Map<String, String> values = new LinkedHashMap<>();
        volatile boolean failNextWrite;
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
            table.upsert(handle, snapshots);
        }

        @Override
        public void delete(Handle handle, List<String> names) {
            table.deleteWhere(handle, "name", names);
        }

        @Override
        public void load(Handle handle) {
            handle.execute("CREATE TABLE IF NOT EXISTS entries (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
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

    private Database database() {
        return Database.open("jdbc:sqlite:" + dir.resolve("data.db"));
    }

    private Persistence start(Database database, EntryStore store) {
        Persistence persistence = new Persistence(database, mainQueue::add);
        persistence.register(store);
        persistence.load();
        return persistence;
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

    private static Map<String, String> rows(Database database) {
        Map<String, String> rows = new LinkedHashMap<>();
        database.handle().createQuery("SELECT name, value FROM entries ORDER BY rowid").map((rs, ctx) -> rows.put(rs.getString(1), rs.getString(2)))
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
}
