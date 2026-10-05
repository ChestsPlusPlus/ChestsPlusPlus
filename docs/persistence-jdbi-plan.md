# Persistence cleanup: SQL migration files + JDBI

Status: implemented, then superseded by [persistence-stores-plan.md](persistence-stores-plan.md): the `Repository` SQL Object and
`PersistenceService` were replaced by per-store tables and a generic write-behind engine. Runtime libraries are declared as
`paperLibrary(...)` in the build, which writes `paper-libraries.txt` for `ChestsPlusPlusLoader`.

Scope: `persistence` package, its wiring in `ChestsPlusPlus`, build config, and persistence tests.

## Goals

1. **Schema in `.sql` files, not Java.** The migrations move out of `Database.MIGRATIONS` into
   `src/main/resources/db/migration/`. We keep the small `PRAGMA user_version` runner.
2. **JDBI instead of raw JDBC.** `Repository` becomes a JDBI SQL Object interface. UUID↔BLOB conversion is
   registered once, and transactions use `@Transaction` instead of manual autocommit/rollback.
3. **Fewer statements per save.** A `SaveBatch` should cost a fixed number of statements, not about 7 per group.

## Unchanged

- The architecture stays as it is: in-memory model as the source of truth, SQLite as the write-behind layer, one I/O
  thread, and a synchronous final flush (plan §4.3).
- The schema stays the same. `V1.sql` is the current v1 DDL word for word, so existing `data.db` files at
  `user_version = 1` open without a data migration.
- `Records` (`GroupRecord`, `NodeRecord`, `SaveBatch`, `LoadedData`) and `PersistenceService`'s public API stay
  the same.
- Paper still provides the SQLite driver (bundled `sqlite-jdbc`, spike S2).

## Steps

### 1. Build and runtime loading

- `gradle/libs.versions.toml`: add `jdbi = "<latest 3.x>"`, plus `jdbi-core` and `jdbi-sqlobject` library entries.
- `build.gradle.kts`:
  - `compileOnly(libs.jdbi.core)`, `compileOnly(libs.jdbi.sqlobject)`, and the same two as `testImplementation`.
  - Add `-parameters` to `options.compilerArgs` so `:name` bindings and record constructor mapping work without
    `@Bind`/`@ColumnName` everywhere.
  - `paperPluginYaml { loader = "$pluginPackage.ChestsPlusPlusLoader" }`.
- New `ChestsPlusPlusLoader implements PluginLoader`: a `MavenLibraryResolver` on
  `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR` that resolves `org.jdbi:jdbi3-sqlobject:<version>`. JDBI's
  transitive dependencies (geantyref, ANTLR runtime) come with it, which is why we use the resolver instead of
  shading.
  - The version has to match the catalog. Either generate a constant (e.g. a resource-filtered
    `libraries.properties`), or hardcode it with a comment pointing to `libs.versions.toml`.
- Check: `-Xlint:all -Werror` still compiles cleanly with the JDBI annotations.

### 2. Migrations as resources

- Add `src/main/resources/db/migration/V1.sql` containing the six `CREATE TABLE` statements and the `nodes_group`
  index from `Database.MIGRATIONS`.
- `Database.migrate()`:
  - `SCHEMA_VERSION` becomes a plain constant (`1`). A classpath directory inside a jar can't be listed reliably,
    so the runner loads `V{n}.sql` for `n = current+1 .. SCHEMA_VERSION` and fails loudly if a file is missing.
  - Each version runs in `handle.useTransaction(...)`: `h.createScript(sql).execute()`, then
    `PRAGMA user_version = n`. JDBI's `Script` splits the file into statements.
  - The "newer than this plugin supports" check and its message stay.
  - Keep the `.sql` files free of comments containing `;`, or confirm `Script` handles them, before relying on
    comments.
- Convention for later versions: add `V2.sql` and bump `SCHEMA_VERSION`. Never edit a shipped file.

### 3. `Database`: owns the `Jdbi` and a single `Handle`

- `open(String jdbcUrl)`:
  - `Jdbi.create(jdbcUrl).installPlugin(new SqlObjectPlugin())`.
  - Register the UUID mappings (step 4).
  - Open one `Handle`, run the PRAGMAs on it (`foreign_keys` applies per connection), then migrate.
- Expose `handle()` instead of `connection()`. `close()` closes the handle.
- Threading stays the same: the handle is used only on the I/O thread, and by the final flush after that thread has
  stopped. Never call `jdbi.open()` anywhere else, because each call opens a new connection without the PRAGMAs.

### 4. UUID ↔ BLOB mapping

- New package-private `UuidBlob` with the existing `bytes(UUID)` and `uuid(byte[])` helpers moved from
  `Repository`, plus:
  - an `AbstractArgumentFactory<UUID>(Types.BLOB)` that binds with `setBytes`;
  - a `ColumnMapper<UUID>` that reads with `getBytes`.
- Register both on the `Jdbi` in `Database.open`. The custom argument factory registered later takes precedence
  over JDBI's built-in UUID handling.

### 5. `Repository` → `GroupDao` SQL Object

Row records shared by reads and writes, each with a `groupId` so batches can be flattened:

- `MemberRow(long groupId, UUID member)`
- `NodeRow(long groupId, UUID world, int x, int y, int z, String facing)`
- `InventoryRow(long groupId, byte[] items, long updatedAt)`
- `RecipeRow(long groupId, @Nullable String recipeKey, byte[] matrix)`
- `TrustRow(UUID owner, UUID trusted)`
- `GroupRow`: the `groups` columns

**Writes.** Each statement runs once per `SaveBatch`, as a `@SqlBatch` over the flattened rows:

1. `deleteGroups(List<Long> id)`
2. `upsertGroups(@BindMethods List<GroupRecord>)`, using the same `ON CONFLICT (id) DO UPDATE` SQL as today
3. `clearMembers(List<Long> groupId)`, then `insertMembers(@BindMethods List<MemberRow>)`
4. `clearNodes(List<Long> groupId)`, then `insertNodes(@BindMethods List<NodeRow>)` (keep `INSERT OR REPLACE`)
5. `upsertInventories(List<InventoryRow>)` and `upsertRecipes(List<RecipeRow>)`, for groups that have them
6. `clearTrust(List<UUID> owner)`, then `insertTrust(List<TrustRow>)`

`@Transaction default void write(SaveBatch batch)` builds the flattened lists and calls those statements in that
order. It returns early when `batch.isEmpty()`, and skips any `@SqlBatch` whose list is empty.

- **Order check against today's per-group loop.** Clearing every group's nodes before inserting any reaches the same
  final state, including a node that moved between two groups in the same batch: insert order is still list order,
  so the last snapshot wins as it does today. The existing `updatesReplaceChildRowsAndDeletesCascade` test covers
  this. Add a same-batch move case to it.
- **`updated_at`.** `write` takes the timestamp once per batch (`System.currentTimeMillis()`) and puts it on every
  `InventoryRow`, instead of reading the clock inside each insert.

**Reads.** Each read is a `@SqlQuery` with `@RegisterConstructorMapper`, mapping onto the row records with the same
`ORDER BY` clauses as today. `loadAll()` (a default method, or a small helper class) assembles `LoadedData`:

- members and nodes: `groupingBy(groupId)` with list values, which keeps rowid order;
- inventories and recipes: maps by `groupId`;
- trust: a `LinkedHashMap` of `LinkedHashSet`s, which keeps today's iteration order.

Optionally, move the longer SQL into `GroupDao/*.sql` files with `@UseClasspathSqlLocator`. Short statements can
stay inline. Pick one rule and apply it to the whole interface.

### 6. Exceptions

JDBI throws the unchecked `JdbiException` instead of `SQLException`.

- `PersistenceService`: replace the `SQLException` catches and rethrows (`load`, `loadAllOnIoThread`, `close`,
  `writeEverythingNow`, `write`) with `JdbiException`. `load` no longer declares `throws SQLException`. Retry and
  logging behaviour stays the same.
- `Database.open`: the newer-schema check throws `IllegalStateException`.
- `ChestsPlusPlus.onEnable`: catch `IOException | JdbiException | IllegalStateException`. That file has uncommitted
  local changes, so keep this edit small and merge it carefully.

### 7. Tests

- `RepositoryTest` becomes `GroupDaoTest`. Keep every existing case:
  - fresh migrate to `SCHEMA_VERSION`;
  - full round trip;
  - child-row replacement and cascade;
  - refusing a newer schema;
  - UUID round trip, moved to `UuidBlob`.
- New cases:
  - **Legacy compatibility.** Build a database with the old in-code DDL at `user_version = 1`, open it with the new
    code, and round-trip data. This proves `V1.sql` is identical in practice.
  - **Same-batch node move** between two groups.
  - **Missing migration file** fails with a clear message.
- `PersistenceServiceTest` should only need the exception-type changes.
- E2E: the first start of the E2E server now downloads JDBI into its `libraries/` folder, so CI needs network access
  to the Maven Central mirror once. Confirm the plugin still enables and that `/reload`-free restarts keep data.

## Order of work and commits

1. Build: JDBI deps, `-parameters`, `ChestsPlusPlusLoader`. The plugin still runs on the old code.
2. `V1.sql` + resource-based runner + `Database` on JDBI + `UuidBlob` + legacy-compatibility test.
3. `GroupDao` with batched writes + reads, delete `Repository`, port the tests.
4. Exception switch in `PersistenceService` and `ChestsPlusPlus`. Update plan §4.2/§4.1 in `v3-rewrite-plan.md`
   (migrations are now "`V{n}.sql` resources" instead of "numbered migration classes", and JDBI is now a runtime
   library).

Each step builds and passes `./gradlew test` on its own.

## Risks

- **Library download at startup.** Servers without internet access to the mirror can't fetch JDBI. That's the
  standard trade-off of Paper's resolver. If it becomes a problem, shade JDBI with relocation, which costs about
  1–2 MB with ANTLR.
- **Record mapping depends on `-parameters`.** If someone drops the flag, mapping fails at runtime, not at compile
  time. The round-trip tests catch it.
- **The handle is not thread-safe.** The single-I/O-thread rule now protects the `Handle` the same way it protected
  the `Connection`. Note this in the `Database` Javadoc.
