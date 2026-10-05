package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class RecordTableTest {

    public enum Colour {
        RED,
        BLUE
    }

    public record Thing(long id, UUID ownerId, Colour colour, @Nullable String note) {}

    public record Label(long thingId, String text) {}

    public record Slot(long thingId, int slot, String value) {}

    private static final UUID OWNER = UUID.randomUUID();

    private final Database db = Database.open("jdbc:sqlite::memory:");
    private final Handle handle = db.handle();
    private final RecordTable<Thing> things = new RecordTable<>(Thing.class, "things", "id");
    private final RecordTable<Label> labels = new RecordTable<>(Label.class, "labels", "thing_id", "text");
    private final RecordTable<Slot> slots = new RecordTable<>(Slot.class, "slots", "thing_id", "slot");

    RecordTableTest() {
        handle.execute("CREATE TABLE things (id INTEGER PRIMARY KEY, owner_id BLOB NOT NULL, colour TEXT NOT NULL, note TEXT)");
        handle.execute("CREATE TABLE labels (thing_id INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY (thing_id, text))");
        handle.execute("CREATE TABLE slots (thing_id INTEGER NOT NULL, slot INTEGER NOT NULL, value TEXT NOT NULL, PRIMARY KEY (thing_id, slot))");
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void generatesSnakeCaseColumnsAndUpsertSql() {
        assertThat(things.columns()).containsExactly("id", "owner_id", "colour", "note");
        assertThat(things.upsertSql()).isEqualTo("INSERT INTO things (id, owner_id, colour, note) VALUES (:id, :ownerId, :colour, :note)"
                + " ON CONFLICT (id) DO UPDATE SET owner_id = excluded.owner_id, colour = excluded.colour, note = excluded.note");
        assertThat(RecordTable.snakeCase("isPublic")).isEqualTo("is_public");
    }

    @Test
    void tableWhoseColumnsAreAllKeysIgnoresDuplicates() {
        assertThat(labels.upsertSql()).endsWith("ON CONFLICT (thing_id, text) DO NOTHING");
        labels.upsert(handle, List.of(new Label(1, "a"), new Label(1, "a"), new Label(1, "b")));

        assertThat(labels.all(handle)).containsExactly(new Label(1, "a"), new Label(1, "b"));
    }

    @Test
    void rejectsUnknownKeysAndColumns() {
        assertThatThrownBy(() -> new RecordTable<>(Thing.class, "things", "missing")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> things.deleteWhere(handle, "missing", List.of(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertInsertsThenUpdatesAndRoundTripsUuidsEnumsAndNulls() {
        things.upsert(handle, List.of(new Thing(1, OWNER, Colour.RED, "first"), new Thing(2, OWNER, Colour.BLUE, null)));
        things.upsert(handle, List.of(new Thing(1, OWNER, Colour.BLUE, null)));

        assertThat(things.all(handle)).containsExactly(new Thing(1, OWNER, Colour.BLUE, null), new Thing(2, OWNER, Colour.BLUE, null));
    }

    @Test
    void deleteWhereRemovesMatchingRows() {
        things.upsert(handle,
                List.of(new Thing(1, OWNER, Colour.RED, null), new Thing(2, OWNER, Colour.RED, null), new Thing(3, OWNER, Colour.RED, null)));

        things.deleteWhere(handle, "id", List.of(1L, 3L));
        things.deleteWhere(handle, "id", List.of());

        assertThat(things.all(handle)).extracting(Thing::id).containsExactly(2L);
    }

    @Test
    void replaceForRemovesStaleChildRowsOfThoseParentsOnly() {
        slots.upsert(handle, List.of(new Slot(1, 0, "a"), new Slot(1, 1, "b"), new Slot(2, 0, "c")));

        slots.replaceFor(handle, "thing_id", List.of(1L), List.of(new Slot(1, 1, "B")));

        assertThat(slots.all(handle)).containsExactly(new Slot(2, 0, "c"), new Slot(1, 1, "B"));
    }

    @Test
    void allKeepsInsertionOrder() {
        labels.upsert(handle, List.of(new Label(3, "z"), new Label(1, "y"), new Label(2, "x")));

        assertThat(labels.all(handle)).extracting(Label::text).containsExactly("z", "y", "x");
    }
}
