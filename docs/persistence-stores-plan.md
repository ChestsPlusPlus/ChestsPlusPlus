# Persistence rework: generic engine, a store per kind of thing, a table per store

Status: implemented. Replaces the partial-save design (`Change`, per-part dirty tracking, content hashes) that followed the JDBI move.
Steps 3 and 4 landed as one commit, because the old `PersistenceService` could not run on the rewritten `V1.sql`. With no SQL Object
left, the runtime library is `jdbi3-core` rather than `jdbi3-sqlobject`. Schema tests live in `DatabaseTest`, and `ItemsBlob` has its own
MockBukkit test, since serialising items needs a server.

## Why

`PersistenceService` grew to about 330 lines of interlocking state: generation counters, an in-flight set, a flushing flag, the
`persisted` set, `hopperTouched`, two hash maps, and separate tracking for trust. Most of it optimised SQL writes, which are cheap and
already off the main thread. Adding a new kind of data today means changes in five places (migration, `Repository`, `Records`,
`ModelMapper`, `PersistenceService`).

## Goals

1. **A plain write-behind engine** that knows nothing about chests: keys marked dirty, a flush that copies them on the main thread
   and writes them in one transaction on the I/O thread, and a retry that marks them dirty again.
2. **One store per kind of thing** (groups, trust, and later anything else). A store says how to copy, write, delete and load its
   data; the engine never changes when a store is added.
3. **A table per store, with real columns.** A small `RecordTable<R>` helper generates the routine SQL from a record type.
4. **Item serialisation on the I/O thread**, from cloned items, so the main thread only clones.
5. Adding a new kind of thing is: a record, a migration file, a store class of about 15 lines, one registration line and a
   `markDirty` call.

## Unchanged

- JDBI, the single SQLite handle, the PRAGMAs, the `V<n>.sql` migration runner and `ChestsPlusPlusLoader`.
- The in-memory model is the source of truth; the database is only loaded at startup.
- Hopper filters stay in hopper PDCs.
- Flush triggers: every `storage.flush-interval-seconds`, on `WorldSaveEvent`, and on disable.

## Step 0: confirm off-thread item serialisation

From reading Paper 26.3's code: `CraftItemStack.serializeAsBytes()` only encodes the item with `ItemStack.CODEC` against the
(frozen) registries and compresses it; there is no main-thread check. `Inventory.getContents()` returns live mirrors of the slots, and
`ItemStack.clone()` copies the underlying item. So the plan clones on the main thread and serialises the clones on the I/O thread.

Before building on this, confirm it on a real server: run the E2E suite and the manual "kill -9 and restart" check with items that
carry data (names, enchantments, shulker contents).

**Fallback** if it turns out to be unsafe: serialise inside the store's `snapshot` (main thread) instead. Nothing else in the design
changes; only the item converter moves.

## Design

### Engine: `Persistence` (replaces `PersistenceService`)

```java
public final class Persistence {
    public <K, S> void register(Store<K, S> store);
    public <K> void markDirty(Store<K, ?> store, K key);   // main thread; one hash-set add
    public void load();                                     // each store's load, in registration order
    public void flush();                                    // copy on main, write on the I/O thread
    public void close();                                    // flush, wait for the I/O thread, close the database
    public boolean isDirty(Store<?, ?> store, Object key);  // for tests and the test harness
    public int pending();                                   // dirty keys plus keys in batches still being written
}
```

- **State:** `Map<Store<?, ?>, Set<Object>> dirty`, and an in-flight batch counter for `pending()`. Nothing else.
- **`flush()`:** for each store, take and clear its dirty keys and call `snapshot(key)` for each one, giving either a snapshot to
  write or a key to delete. The batch goes to the I/O thread, which runs one transaction calling each store's `write` and `delete`.
- **Ordering:** the single I/O thread runs batches in order, so a key that changes again while its batch is being written is simply
  saved again in a later batch. No generation counters or in-flight set.
- **Failure:** the I/O thread logs and hands the batch's keys back to the main thread, which marks them dirty again. The next flush
  copies their *current* state, so a retry can never write stale data.
- **`close()`:** flush, then `io.shutdown()` and wait up to 30 s, then close the database. A failed final write is logged.
- **Threading:** `markDirty`, `flush` and `load` are main-thread only. The `Handle` is used on the I/O thread, and on the main thread
  only during `load()`, before any flush exists.

### Store

```java
public interface Store<K, S> {
    @Nullable S snapshot(K key);                  // main thread, cheap; null means the thing no longer exists
    void write(Handle handle, List<S> snapshots); // I/O thread, inside the flush transaction
    void delete(Handle handle, List<K> keys);     // I/O thread, inside the flush transaction
    void load(Handle handle);                     // main thread, at startup
}
```

Each store is registered once and exposed through `Services` (like `persistence()` today), so call sites read
`services.groupStore().markDirty(group)`. Stores get a small `markDirty(K)` that delegates to the engine, so callers never pass the
store to itself.

### `RecordTable<R extends Record>`: generated SQL

```java
RecordTable<NodeRow> nodes = new RecordTable<>(NodeRow.class, "nodes", "world", "x", "y", "z");  // table, primary key columns

nodes.upsert(handle, rows);                        // INSERT ... ON CONFLICT (world, x, y, z) DO UPDATE SET <other columns>
nodes.deleteWhere(handle, "group_id", ids);        // DELETE FROM nodes WHERE group_id = ?   (batched)
nodes.replaceFor(handle, "group_id", ids, rows);   // deleteWhere, then upsert: for a parent's child rows
List<NodeRow> all = nodes.all(handle);             // SELECT <columns> FROM nodes ORDER BY rowid
```

- Columns are the record's components in snake_case (`groupId` → `group_id`). The SQL strings are built once, in the constructor.
- Rows are bound with JDBI's `bindMethods` and read back with `ConstructorMapper`. Both need the existing `-parameters` flag.
- If every column is part of the key, the upsert becomes `ON CONFLICT DO NOTHING`.
- Table and column names come from code constants and record components, never from user input.
- Keep it to these four operations. Anything unusual goes in a store as a plain JDBI call rather than growing the helper.

### Column types, registered once in `Database.open`

| Java | Column | Notes |
|---|---|---|
| `UUID` | BLOB (16 bytes) | the existing `UuidBlob`, unchanged |
| enums | TEXT | JDBI's default (by name) |
| `boolean` | INTEGER 0/1 | JDBI's default |
| `ItemStack[]` | BLOB | new `ItemsBlob`: `ItemStack.serializeItemsAsBytes` when binding (I/O thread), deserialise and normalise empty stacks to `null` when reading (the current `ModelMapper.deserialize` logic) |

### Schema: edit `V1.sql` in place

v3 is still in development and has never shipped, so there is no data to migrate. Rewrite `V1.sql` and keep `SCHEMA_VERSION = 1`:

- `groups` gains `items BLOB` and `recipe_key TEXT`.
- `chest_inventories` and `autocraft_recipes` are removed (`updated_at` was never read).
- `group_members`, `nodes` and `trust` stay as they are, including the `ON DELETE CASCADE` foreign keys.

**Dev and test servers must start from a fresh database.** An old `data.db` is also at `user_version = 1`, so the migration runner
won't touch it, and the plugin would then fail on the missing columns. Delete `plugins/ChestsPlusPlus/data.db` (plus `data.db-wal`
and `data.db-shm`) under `run/` and `run/e2e/` when this lands.

From the first v3 release onwards, `V1.sql` is frozen and every schema change goes in a new `V<n>.sql`.

### `GroupStore` (`Store<Long, GroupSnapshot>`)

```java
record GroupRow(long id, GroupType type, UUID owner, String name, boolean isPublic, @Nullable SortMode sortMode, long createdAt,
        @Nullable ItemStack @Nullable [] items, @Nullable String recipeKey) {}
record MemberRow(long groupId, UUID member) {}
record NodeRow(long groupId, UUID world, int x, int y, int z, BlockFace facing) {}
record GroupSnapshot(GroupRow group, List<MemberRow> members, List<NodeRow> nodes) {}
```

- **`snapshot(id)`:** null if the group is no longer in `GroupRegistry`. Otherwise copy the fields, members and nodes, and clone the
  items: each slot of a ChestLink's inventory, or the AutoCraft matrix (`AutoCraftGroup.matrix()` already returns clones).
- **`write`:** upsert the group rows, `replaceFor` the members and nodes of those group ids. Three statements per flush.
- **`delete`:** `deleteWhere("id", ids)` on `groups`; the cascades remove members and nodes.
- **`load`:** read the three tables, rebuild groups, members and nodes, and call the attach callback (ChestLink inventories,
  AutoCraft recipe resolution) passed to the store's constructor, as `PersistenceService.load` does today.
- Absorbs `ModelMapper` (the conversions and the lenient `SortMode`/`BlockFace` parsing).

### `TrustStore` (`Store<UUID, TrustSnapshot>`)

- `record TrustRow(UUID owner, UUID trusted)`, `record TrustSnapshot(UUID owner, List<TrustRow> rows)`.
- **`snapshot(owner)`:** the owner's current trusted players. An empty list still writes (it clears the owner's rows), so it isn't
  treated as a delete.
- **`write`:** `replaceFor("owner", owners, rows)`. **`load`:** read all rows into `TrustService.load`.
- `TrustService.onChange` calls `trustStore.markDirty(owner)`.

### Call sites

- Every `markDirty(group, Change.X)` becomes `groupStore.markDirty(group)` (`LinkService`, `ChestLinkService`, `AutoCraftService`,
  `HopperBridge`). `markDeleted` becomes `markDirty` too: the snapshot returns null for a removed group, which deletes it.
- `HopperBridge.onSearch` calls `groupStore.touch(group)`, one hash-set add on the hot path. At the start of each flush
  (`Store.beforeFlush`), a touched group is marked dirty only if its contents differ from what it last wrote, so an idle hopper
  doesn't re-save its group every flush. Only hoppered groups' contents are kept for this; the first touch saves the group once.
- `ChestLinkService.setSortMode` stays, minus its extra `Change.META` call.
- `ChestsPlusPlus`: build `Persistence`, register `GroupStore` then `TrustStore`, call `load()`. Remove the per-tick
  `persistence` ticker; the interval ticker and `WorldSaveEvent` call `flush()`.

### Settings

`storage.max-serialisations-per-tick` has nothing left to limit. Remove it from `config.yml`, `Settings.Storage` and the wiring. If
profiling a large server ever shows the main-thread copy as a spike, the engine can take at most N keys per tick instead; that's a few
lines and doesn't change any store.

### Removed

`PersistenceService`, `Change`, `Records`, `ModelMapper`, the annotated `Repository` interface and its row records, and the
`GroupSave`/hash/`persisted`/generation logic. Kept: `Database`, `UuidBlob`, `Resources`, `ChestsPlusPlusLoader`.

Expected size: the persistence package goes from about 800 lines to about 350 (engine ~90, `RecordTable` ~60, `GroupStore` ~90,
`TrustStore` ~30, `Database` ~85, type converters ~40).

## Adding a new kind of thing (goes in `AGENTS.md`)

1. A row record, e.g. `record PlayerPrefsRow(UUID player, boolean showDisplays) {}`.
2. Its `CREATE TABLE`: in `V1.sql` until v3 ships (then clear your dev database), afterwards in a new `db/migration/V<n>.sql` with
   `Database.SCHEMA_VERSION` bumped.
3. A store (about 15 lines) using `RecordTable` for `write`, `delete` and `load`.
4. Register it in `ChestsPlusPlus` and call `markDirty` wherever the data changes.

## Tests

- **`RecordTableTest` (unit, in-memory SQLite):** generated column lists and SQL; upsert inserts then updates; all-key tables;
  `deleteWhere`; `replaceFor` removes stale child rows; `all()` keeps insertion order; nullable columns; UUID and enum round trips.
- **`PersistenceTest` (unit, fake in-memory store, no Bukkit):** flush writes dirty keys; a null snapshot deletes; a key changed while
  its batch is in flight is written again; a failing write marks its keys dirty again and the retry writes current state;
  `close()` drains everything.
- **`GroupStoreTest` (MockBukkit):** the current `writeBehindSurvivesRestart` round trip (inventory, sort mode, public flag, members,
  nodes, recipe, trust); deleting a group removes its members and nodes; a node moving between groups in one flush; a renamed sword
  in the same slot survives a restart.
- **Schema:** a fresh database migrates to `SCHEMA_VERSION`, and a newer `user_version` is refused (both carried over from
  `RepositoryTest`). The legacy-schema test (`opensDatabasesCreatedBeforeMigrationsMovedToSqlFiles`) is deleted, since there is no
  legacy data.
- **E2E and the manual robustness checklist** on a real server, which also covers step 0.
- Integration tests that assert `isDirty(...)` switch to `persistence.isDirty(groupStore, id)` (or a `groupStore.isDirty(group)`
  helper); the test harness's `dirty=` output does the same.

## Order of work (each step builds and passes `./gradlew unitTest integrationTest`)

1. `RecordTable`, `ItemsBlob` and their unit tests (nothing uses them yet).
2. `Persistence`, `Store`, `PersistenceTest`.
3. The new `V1.sql`, `GroupStore`, `TrustStore`; delete the dev databases under `run/` and `run/e2e/`.
4. Switch `ChestsPlusPlus`, `Services` and the call sites; remove `PersistenceService`, `Change`, `Records`, `ModelMapper` and the old
   `Repository`; remove `max-serialisations-per-tick`; port the integration tests.
5. Docs: `AGENTS.md` (the "Save a model change", "SQL" and new "Adding a new kind of thing" rows; drop `persistence.tick` from the
   hot-path notes if mentioned), `v3-rewrite-plan.md` §4, and mark this plan and `persistence-jdbi-plan.md` accordingly.
6. E2E run and the manual robustness checklist.

## Risks

- **Off-thread serialisation** rests on reading Paper's current code, not a documented guarantee. Step 0 and E2E check it; the
  fallback is a one-place change.
- **A large flush on the main thread.** Cloning every dirty group's items at once could show up on servers with thousands of busy
  ChestLinks. The per-tick cap described under Settings is the escape hatch.
- **`RecordTable` growing into an ORM.** Keep it to the four operations; anything else is a direct JDBI call in the store that needs it.
- **Losing the last batch on shutdown.** Same as today: a failed final write is logged, and the WAL plus 30 s flushes bound the loss.
