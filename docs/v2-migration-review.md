# v2 → v3 migration review

A review of the v2 import (commit `6c8deac` plus the uncommitted change that allows spaces in group names), compared against
[v2-migration-plan.md](v2-migration-plan.md). At the time of review, `./gradlew unitTest integrationTest spotlessCheck` passed.

Follow [AGENTS.md](../AGENTS.md) for style, and add a test for each fix where noted. Paths below are relative to
`src/main/java/com/jamesdpeters/chestsplusplus/` unless they start with `src/` or `docs/`.

---

## High-priority findings (addressed)

### 1. Re-running the import can duplicate items

**Where:** `migration/V2Importer.java`: `Run` constructor (line 74) and `importGroup` (line 86).

**Problem:** a group counts as "already imported" only if a **live** group has that `v2_source`. If a player deletes the group
in v3, the next `/cpp migrate v2 confirm` (or a startup import after a failed rename) imports it again with all its v2 items.

**Scenario:**
1. Start v3; `T:Storage` is imported with its items.
2. T unlinks the last chest, so `LinkService.unlink` → `removeGroup` drops the items and deletes the group.
3. An admin runs `/cpp migrate v2 confirm`. The report itself tells them to do this when a world wasn't found.
4. `T:Storage` is recreated with every v2 item and no blocks, because they're all recorded in `v2_blocks`. The items are now
   duplicated.

**Resolution:** imports now run once, gated by a durable `migration_state.v2_import` record committed with the imported
model, trust and unresolved locations. The record survives deletion. YAML renaming is backup housekeeping, and restored copies
cannot replay. Pending locations recover against original group IDs rather than rereading inventories. Regression coverage uses
the real `LinkService.removeGroup` path, checks dropped items and verifies restored-YAML attempts cannot recreate them.

### 2. Re-running the import trusts party members again after the owner removed them

**Where:** `migration/V2Importer.java`: `importParty` (lines 176-182).

**Problem:** the only check is `trust.isTrusted(owner, member)`. If the owner untrusts a former party member in v3, the next
`confirm` trusts them again without the owner knowing.

**Resolution:** the same one-time import gate prevents party trust from being applied again. Tests revoke trust before
another import attempt and after database failure, then verify it stays revoked across persistence reload/retry.

### 3. `/cl list` and `/ac list` click links break for names with spaces

**Where:** `src/main/resources/messages.yml` lines 39 and 54 (`<click:run_command:'/chestlink open <ref>'>`), and
`link/GroupActions.java` line 165, which builds the `ref` placeholder.

**Problem:** for a group named `Iron Ore`, clicking the link runs `/chestlink open Iron Ore`, which is a syntax error.
`GroupArgument` needs the name quoted. This affects every imported v2 name that has a space.

**Resolution:** `GroupActions.list` quotes references with `StringArgumentType.escapeIfRequired` and builds the Adventure
click event in code via the `<open>` tag. The rendered-command test exposed a second issue: MiniMessage left `<ref>` literal inside
the old quoted click-event argument. Updated default templates avoid that. Tests extract the actual rendered click payload and
dispatch it for own and other-owner names with spaces, for both ChestLinks and AutoCrafters. Existing custom list-entry overrides
need the matching `<open>...</open>` syntax. Extra quoting of `Owner:name` remains harmless.

### 4. `filters convert-all <world>` stops filter conversion in every other world

**Where:** `migration/V2FilterConversion.java`: `finish` (line 185-187), and `V2Migration.convertAll`.

**Problem:** `finish` always sets `MigrationState.Filters.DONE`. After a single-world run, that turns off the on-load listener
so frames in every other world are never converted. Op join warnings only apply to `PENDING`; entering `ON_LOAD` already stops those warnings.

**Resolution:** jobs carry explicit global vs single-world scope. Only a successful global run can set `DONE`;
single-world runs, failed scans/loads, entity timeouts and known missing worlds retain `ON_LOAD`. Scope includes worlds loaded when
the scan starts, and already-loaded chunks. Tests cover scope, failures, cancellation, missing worlds and retry.

### 5. Timeout fallback can force synchronous entity loading (corrected finding)

**Where:** `migration/V2FilterConversion.java`: `convertReady`; `migration/V2FilterMigration.java`: `convert`.

The original claim that `chunk.getEntities()` returned an incomplete list and could lose marker data is **not established**.
[Paper 26.3 Chunk.getEntities documentation](https://jd.papermc.io/paper/26.3/org/bukkit/Chunk.html#getEntities()) says it force-loads
unloaded entities. This was also verified in the locally pinned `26.3.build.146-beta` API sources. The concern is synchronous work
on the server thread when the old 200-tick fallback calls it before entities are ready.

**Resolution:** timeout defers the chunk without calling `getEntities` or marking it scanned, releases its ticket, records
unfinished work and retains `ON_LOAD`. A later entity-load event or scan retries it. A marker-only guard would not suffice if
completion disabled that listener. Tests check delayed entities, timeout/event completion, retry, ticket release, cancellation and
no duplicate filters/items/frame drops.

---

## Worth fixing

### 6. Some v2 armour stands are never removed

**Where:** `migration/V2WorldCleanup.java`: `onEntitiesLoad` (lines 59-64).

**Problem:** stands are only removed while `cleanup` still has blocks waiting. Once it's empty, these stands stay:
- stands in a neighbouring chunk whose entities load after the block's chunk was finished (the code's own comment says a stand
  can sit there);
- stands next to v2 blocks that were never imported (unresolved world, position already linked, or a stale location).

**Suggested fix:** also remove v2 stands in `V2FilterMigration.convert`, which already visits every chunk once and marks it, or
add a separate one-time-per-chunk marker for stand removal.

### 7. Cleanup can load neighbouring chunks synchronously inside chunk events

**Where:** `migration/V2WorldCleanup.java`: `finishBlocks` → `removeV2Sign` (`block.getRelative(side)`) and
`DoubleChests.split` (the partner block). Also `migration/V2FilterMigration.java` line 52: a frame's attached hopper can be in
the next chunk.

**Problem:** at a chunk edge, reading block data from an unloaded neighbour makes Paper load that chunk synchronously during
`ChunkLoadEvent`/`EntitiesLoadEvent`. It happens once per chunk, but on large worlds and during `convert-all` it's an avoidable
source of lag.

**Suggested fix:** check `world.isChunkLoaded(...)` for the neighbour. If it isn't loaded, leave the block pending so it's
finished when that chunk loads.

### 8. Stale imported blocks aren't unlinked in chunks that are already loaded

**Where:** `migration/V2WorldCleanup.java`: `finishBlocks`; compare with plan §5 step 1.

**Problem:** the plan says world cleanup unlinks a block that is no longer the right type. The code relies on
`NodeListener.unlinkChangedBlocks`, which only runs on `ChunkLoadEvent`. Blocks finished through `finishLoaded()` (chunks
already loaded at startup or after `confirm`) are never checked.

**Fix:** in `finishBlocks`, check the block with the group's handler (`isValidBlock`) and unlink it with
`keepGroup = true`, as `NodeListener` does. Or update the plan to match the code, if that's the decision.

### 9. Addressed while fixing commit ordering: a failure to write the log file reports the import as failed and skips the rename

**Where:** `migration/V2Migration.java`: `run` (lines 81-93).

**Problem:** `writeLog` runs after the import has been applied but before `flush().thenRun(rename)`. If writing the log throws,
the sender is told the import failed and the file isn't renamed, even though the data was imported.

**Fix:** catch log-writing errors separately (log a warning), or write the log after scheduling the flush.

### 10. Addressed while fixing retry: two imports in the same second fail on the backup folder

**Where:** `migration/V2Migration.java`: `backup` (line 136) and `writeLog` (line 130).

**Problem:** the timestamps have one-second precision. A second `confirm` in the same second gets a
`FileAlreadyExistsException` from `Files.copy`, and the log file is overwritten.

**Fix:** pick a free name with `V2ConfigMigrator.unusedFile`, or similar.

### 11. `limit-chests: true` without an amount sets the limit to 0

**Where:** `migration/V2ConfigMigrator.java` line 65.

**Problem:** if `limit-chestlinks-amount` is missing, `v2.getInt("limit-chestlinks-amount")` returns 0, so every player is
limited to 0 ChestLinks.

**Fix:** fall back to v2's default amount (`getInt(key, <v2 default>)`), or keep the v3 default when the key is absent. Add a
case to `V2ConfigMigratorTest`.

---

## Docs and small cleanups

- **README migration wording (updated):** `README.md` line 8 still says v3 is "not backwards compatible with v2 data … or
  configuration". It should say that data and config are imported.
- **Migrate command and permission (documented in README):** `/cpp migrate v2 …` and `chestsplusplus.admin.migrate` are missing from
  the README's command and permission tables.
- **Upgrade guide not written:** plan §9 step 8 (an old → new table of commands and permissions) is still to do.
- **Stale rename message (fixed):** in `migration/ImportReport.java` line 57, the rename note still says "(v3 names allow up to 32
  letters, numbers, - and _)". Add spaces.
- **Over-quoted suggestions:** in `command/GroupArgument.java`, `escapeIfRequired` quotes any reference containing `:`, so
  suggestions for other owners' groups show as `"Steve:ores"` even without a space. It still parses, but it looks odd.
  Consider quoting only when the reference contains a space. Do the same for #3 if you change the helper.
- **Development schema support:** schema v2 adds pending locations and gates old imports with known evidence without deleting databases.
  Historical unresolved locations and party-only imports with no remaining evidence cannot be reconstructed; see the plan.
- **Freeze the schema at release:** before 3.0.0 ships, `V1.sql` must be frozen. After that, schema changes go in
  `db/migration/V<n>.sql` with `Database.SCHEMA_VERSION` bumped. This matters for #1 and #2 if they add a table after release.
- **Open plan question:** plan §10 still asks whether `v2-backup-*` folders should be deleted after N successful starts.

## Checked and fine

These were reviewed and need no change:
- **Crash safety (updated):** groups, trust, cleanup, completion and pending locations share one transaction. Renaming follows
  commit. Synchronous adoption failures roll back before retry; database failures requeue current snapshots without rereading YAML.
  The earlier review's blanket claim that repeat imports were safe was incorrect in light of #1 and #2.
- **Config migration:** it runs before `loadSettings`, the original is kept as `config-v2.yml`, and `isV2` won't fire again
  on a v3 config.
- **Name clean-up:** `cleanName` + `uniqueName` always give a name that passes `GroupNames.isValid`, including after truncation
  and the `_n` suffix.
- **Display timing:** displays spawned during `ChunkLoadEvent` at `LOWEST` aren't duplicated. They're non-persistent, and
  `spawnPendingChunks` checks `displays.containsKey`.
- **Load order:** the plugin loads at the default `POSTWORLD`, so worlds and recipes exist when the startup import runs.
- **Chunk tickets:** `convert-all` takes and releases tickets correctly, including on cancel.


## Validation of the high-priority fixes (6 October 2026)

`./gradlew spotlessApply unitTest integrationTest spotlessCheck` passed: 56 unit tests and 114 integration tests, with the
existing 26.3-only MockBukkit lifecycle check skipped because its runtime is 26.2. `git diff --check` also passed.

An isolated copy of the real v2 snapshot was run on the pinned Paper 26.3 build 146 and Java 25. It imported 14 ChestLinks,
5 AutoCrafters, 21 nodes, 7 item stacks and 1 trusted player. A single-world scan converted 8 filters and retained `ON_LOAD`;
a global scan finished with `DONE` and no cleanup pending. After restoring the original YAML beside its renamed backup and
restarting, preview/confirm stayed gated. Group/trust counts and the inventory/matrix blob digest were unchanged across restart.
The isolated server was stopped; the existing user server and fixture were preserved. The check jar was assembled separately
from compiled classes/resources and cached bStats libraries, avoiding `build`/`shadowJar` while the user server was running.

Native-client UI, live crafting/hopper gameplay and an actual late-world-loading plugin were not manually exercised here.
JVM tests cover rendered click-command dispatch, recovery identity/validation, atomic pending removal/attachment retry,
synchronous import rollback, database failure/retry, filter scope, delayed entities, timeout/event retry and cancellation.
