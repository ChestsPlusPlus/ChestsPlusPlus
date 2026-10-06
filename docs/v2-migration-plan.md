# v2 → v3 migration plan

v3 imports a v2 install's data automatically the first time it starts, and admins can run the same import again with
`/cpp migrate v2`. The rewrite plan (§1) said v2 compatibility was not a goal. This changes that for **data and config only**.
Commands, permissions and language files still start fresh, and the upgrade guide documents the differences.

**Status:** implemented in the `migration` package, with the test bed in §8.1. Where the code differs from the first draft of this
plan, the plan below has been updated to match it.

---

## 1. What v2 leaves behind

v2 and v3 have the same plugin name, so v3 starts in v2's `plugins/ChestsPlusPlus/` folder and finds:

| v2 artefact | Where | v3 equivalent |
|---|---|---|
| `data/storage.yml` → `chests++: ConfigStorage` | data folder | `data.db` (groups, members, nodes, trust) |
| `chests.<ownerUUID>.<name>: ChestLinkStorage` | storage.yml | `ChestLinkGroup` + nodes + 54-slot items |
| `autocraftingtables.<ownerUUID>.<name>: AutoCraftingStorage` | storage.yml | `AutoCraftGroup` + nodes + 3×3 matrix |
| `parties.<ownerUUID>.ownedParties.<name>.members` | storage.yml | owner-wide trust (`TrustService`) |
| `config.yml` with flat v2 keys | data folder | v3 `config.yml` (nested sections) |
| Wall sign in front of every linked block | world | removed; the v3 display replaces it |
| Marker armour stands tagged `chestsplusplus:chestsplusplus` | world | removed; v3 uses `ItemDisplay` |
| Item frames on hoppers (rotation = mode) | world | `FilterCodec` entries in the hopper's PDC |
| Linked double chests | world | v3 links only single chests, so these are split |
| `lang/*.properties`, `language-file` | data folder | not migrated; `messages.yml` starts fresh |

### Field mapping

| v2 | v3 |
|---|---|
| owner map key / `playerUUID` | `owner` |
| map key (`inventoryName` / `identifier`) | `name`, cleaned up (see §4.3) |
| `isPublic` | `isPublic` |
| `members` (UUID strings) | `group_members` |
| `sortMethod` `OFF/NAME/AMOUNT_ASC/AMOUNT_DESC` | `SortMode`, same names |
| `inventory` (list of 54 `ItemStack`) | `items` blob, passed to `ChestLinkService.attachLoaded` |
| `recipe: C++Recipe {namespace, key, items?}` | matrix + `recipe_key`. `items` only exists for complex recipes, so for shaped and shapeless recipes the matrix is rebuilt from `Bukkit.getRecipe(key)` (see §4.4). Every slot gets `SlotMatch.RECIPE`. |
| `locationInfo[].Location` (world **name**, x, y, z) | `nodes` (world **UUID**, x, y, z, facing). v2 doesn't store facing (see §5). |
| party member of owner O | `trust.trust(O, member)`, which grants the same access a v2 party did |

---

## 2. Shape of the code

A new package, `migration`. It depends on `persistence`, `model`, `chestlink` and `autocraft`, and nothing depends on it, so packages
stay acyclic.

```
migration/
  V2Storage               reads storage.yml into V2Data (no Bukkit-serialisable shims, see §3)
  V2Data                  records: Group, Party, Location, Recipe
  V2Importer              plans the import against the live registry and, unless it is a preview, applies it
  V2Recipes               rebuilds an AutoCraft matrix from a v2 recipe (§4.4)
  ImportReport            counts, renames, skips and unresolved worlds; one place for logging and the command reply
  V2ConfigMigrator        rewrites a v2 config.yml into v3 layout before Settings load
  V2Cleanup(+Store)       every imported block, flagged while its world cleanup is pending, persisted in v2_blocks
  V2WorldCleanup          per-chunk cleanup: facing fix, sign and armour-stand removal, double-chest split
  MigrationState(+Store)  the filter migration state, persisted in migration_state
  V2FilterMigration       per-chunk item frame → filter conversion
  V2FilterConversion      the convert-all job; RegionFiles lists the chunks it loads
  WorldUids               world name → UUID (loaded world, else <container>/<name>/uid.dat)
  V2Migration             startup import, backup, rename, op warnings, and everything the command calls
```

### One apply path for startup and command

`GroupStore.load` already builds a group from rows, registers it, indexes its nodes and calls `attach`. Pull that into a
`GroupStore.adopt(GroupRow, members, nodes, items)` that `load` uses and that marks the group dirty when it's called after load.
The importer then makes rows and calls `adopt`. There's no second way to build a group, and the write goes through `Persistence`
like any other change.

`V2Importer.run` works the same at startup and from the command:

1. Parse the file (§3).
2. Build the plan against the live `GroupRegistry`/`NodeIndex`/`TrustService` (§4).
3. For a dry run, return the report. Otherwise `adopt` each group, `trust` each party member, then `persistence.flush()`.
4. Once the flush has been written, rename `storage.yml` → `storage.yml.v2-migrated` (startup only, see §6).

For step 4, `Persistence.flush()` returns a `CompletableFuture<Void>` that completes on the main thread after the batch commits.
Nothing else changes.

### Idempotency: `groups.v2_source`

Add a nullable column, `groups.v2_source TEXT`, set to `chestlink:<ownerUUID>:<v2 name>` or `autocraft:…`. Until v3 ships we
edit `V1.sql` in place. It's written in the same transaction as the group, so:

- If the server crashes before the flush, nothing is saved and the file isn't renamed, so the next start imports again from scratch.
- If it crashes after the flush but before the rename, the next start finds every `v2_source` and skips them all.
- When the command runs again, already-imported groups are recognised and **their items are never imported a second time**, so
  items can't be duplicated. Locations that were skipped last time (unloaded world, position taken) are still added to the
  existing group.

`StorageGroup` gets a `@Getter private @Nullable String v2Source` that `GroupRow` round-trips. The registry keeps a
`byV2Source` map only while an import is running. There's no need for a permanent index.

---

## 3. Reading storage.yml without v2's classes

`YamlConfiguration.loadConfiguration` fails on v2's aliases (`==: ChestLinkStorage`, `C++Recipe`, …) because they aren't
registered. Registering stand-in classes still leaves `Location.deserialize` throwing for any world that isn't loaded.

**Approach:** parse with plain SnakeYAML, which Paper bundles, into `Map`/`List` trees, then walk the tree:

- `==: Location` → read `world`, `x`, `y`, `z` directly into `V2Location(worldName, x, y, z)`. No world lookup yet.
- `==: ChestLinkStorage | AutoCraftingStorage | LocationInfo | C++Recipe | ConfigStorage | PlayerPartyStorage | PlayerParty`
  → keep as a map and read it into `V2Data`.
- Any other `==` (`org.bukkit.inventory.ItemStack`, `ItemMeta`, …) → `ConfigurationSerialization.deserializeObject` from the
  inside out. Paper upgrades old items using their `v` data version, so items from older Minecraft versions still load.

Also handle the old forms v2 still accepted:

- `locations: [Location]` instead of `locationInfo`.
- A `C++Recipe` `items` stored as a list.
- Nulls inside lists.
- The old recipe type name `==: Recipe`, which v2 renames to `C++Recipe` on every start.

Only v2's `data/storage.yml` is read. Releases before it kept `chests.yml` in the server root, but v2 converts that file on its
first start, so a server that old should start v2 once before upgrading.

A broken entry is logged and skipped. It never stops the rest of the import.

---

## 4. Building the plan

### 4.1 Worlds

`WorldUids.resolve(name)` checks `Bukkit.getWorld(name)` first, then reads `<worldContainer>/<name>/uid.dat` (two longs), which
works for Multiverse worlds that load after us. A location whose world can't be found is reported as **unresolved** and left
out. Running `/cpp migrate v2` again once the world exists picks it up (see §2, idempotency).

### 4.2 Nodes

- Duplicate positions within a group are collapsed.
- A position that is already a v3 node, or that is claimed by a group earlier in the import (v2 sometimes left one block in two
  groups), is skipped and reported.
- Facing starts as `NORTH` and the node is marked **pending cleanup** (§5).

### 4.3 Names

v3 names are up to 32 of `[A-Za-z0-9_-]` with single spaces between words, and are unique per (type, owner), ignoring case. v2's were free text, unique only
with case counted.

- Strip colour codes, collapse runs of spaces, swap other invalid characters for `_`, trim to 32 characters, and use `group` if nothing is left.
- On a clash, append `_2`, `_3`, … (staying within 32 characters).
- The report lists every rename (`Steve: "Ores!" → Ores`).

### 4.4 AutoCraft matrix

| v2 recipe | Matrix |
|---|---|
| has `items` (complex recipe) | those 9 items, made single with `asOne()` |
| key → `ShapedRecipe` | the shape rows placed top-left; each `RecipeChoice` → `MaterialChoice` first material / `ExactChoice` first item |
| key → `ShapelessRecipe` | choices in order, filling the slots row by row |
| key not found (datapack removed) | empty matrix and `recipe_key` kept; the report lists it |

`AutoCraftService.resolveLoaded` then works out the result from the matrix, exactly as it does on a normal load. Using
`SlotMatch.RECIPE` everywhere means "any planks" still accepts any planks.

### 4.5 Limits, blacklist, features

The import is an admin operation. It **ignores** per-player limits and the world blacklist, because refusing data an admin is
moving over would be worse. It still imports AutoCraft groups when `features.autocraft` is off, since the data does no harm and
the feature can be turned on later. The report flags groups that go over a limit so admins can sort them out.

---

## 5. World cleanup (lazy, per chunk)

Some of the cleanup needs the world itself: facing, signs, armour stands and double chests. Loading every chunk at startup could
take tens of seconds on a large server, so this runs on chunk load instead.

- **State:** a small `v2_blocks(world BLOB, x, y, z, pending, PRIMARY KEY …)` table of every block an import linked, owned by a
  `V2CleanupStore` (a `RecordTable`, per AGENTS.md). `pending` marks the ones still waiting for cleanup. A repeated import skips
  every recorded block, so one a player has unlinked since isn't linked again. It's loaded into `V2Cleanup`, with the pending
  blocks indexed by chunk, and is empty on servers that never imported. Once nothing is pending, the listeners return after one
  `isEmpty()` check.
- **`ChunkLoadEvent`**, which must run before `NodeListener`/`DisplayService` spawn displays (`EventPriority.LOWEST`). For each
  migrated node in the chunk:
  1. If the block is no longer the right type, unlink the node with the normal remove path. Its items stay in the group.
  2. Facing: if a v2 wall sign is attached to any side, use that sign's facing and **remove the sign** (no drop, as v3
     `SignLinkListener` does). Otherwise use the chest's `Directional` facing. Otherwise keep `NORTH`.
  3. If it's a double chest, call `DoubleChests.split`.
  4. Re-put the node with its corrected facing and `markDirty` its group.
- **`EntitiesLoadEvent`**, for chunks in the set: remove armour stands whose PDC has `chestsplusplus:chestsplusplus`, which is v2's
  `Values.PluginKey`. Entities load separately from chunks in Paper, so this has to be its own handler.
- Once both handlers have seen a chunk, remove it from the set and `markDirty` the store.
- `/cpp migrate v2 status` shows how many chunks are still waiting. `/cpp migrate v2 cleanup [radius]` loads the waiting chunks
  near the sender, so an admin can finish an area without walking around it.

### Hopper item frames → PDC filters (opt-in by an admin)

v2 filters aren't in `storage.yml`. They're item frames on hoppers anywhere in the world, so finding them means looking at
chunks. That costs something, so nothing is scanned until an admin asks for it.

**State.** A `migration_state(key TEXT PRIMARY KEY, value TEXT)` table, owned by a `MigrationStateStore` (`RecordTable`), holds
`filters` = `NONE | PENDING | ON_LOAD | DONE`:

- A successful data import sets `PENDING`, if it was `NONE` and `features.hopper-filters` is on.
- `ON_LOAD`: chunks are converted as they load.
- `DONE`: a full conversion has finished, or an admin dismissed it. The listener stops looking.

**Warning while `PENDING`.**

- On startup, a `log.warn` says that v2 hopper filters (item frames on hoppers) haven't been converted, and names the two commands.
- On join, players with `chestsplusplus.admin.migrate` get the same message, with the commands as click-to-suggest components.
  They see it on every join until the state changes.
- Both texts are `messages.yml` keys (`MIGRATE_FILTERS_PENDING`, …).

**Converting one chunk** is the same in both modes:

- On `EntitiesLoadEvent`, skip chunks that carry a `chestsplusplus:v2_filters_scanned` chunk-PDC marker.
- Every item frame attached to a hopper becomes a `HopperFilter`. It's appended through `FilterService`, so the cache and displays
  update.
- The frame is removed and its item dropped, since the v3 filter display now shows the entry.
- The chunk gets the marker, so it's never scanned twice. Only one check is done for each chunk that has never been scanned.

**Commands** (§7):

- `/cpp migrate v2 filters on-load` sets `ON_LOAD`. Chunks convert as players reach them, and nothing is loaded on purpose.
- `/cpp migrate v2 filters convert-all [world]` sets `ON_LOAD` and then loads every generated chunk so each one converts:
  - **Finding chunks:** Paper has no "list generated chunks" API. Read each world's `region/r.<x>.<z>.mca` headers instead. That's
    1024 location entries per file, and a non-zero entry means the chunk exists. Paper's `entities/` region files also list
    which chunks have entities. Only those can contain frames, so prefer them and skip chunks with no entity data.
  - **Loading:** `world.getChunkAtAsync`, with a bounded number in flight (8, a constant in `V2FilterConversion`).
    Entities load after the chunk in Paper, so each chunk holds a plugin chunk ticket until its `EntitiesLoadEvent` has run.
    Then the ticket is released and the chunk unloads normally.
  - **Progress:** one run at a time. Progress goes to the sender and the console every 5%. `/cpp migrate v2 filters cancel`
    stops the run, and running the command again resumes it, because the chunk markers skip the work already done.
  - **Finishing:** when every world finishes, the state becomes `DONE`. Loading those chunks also clears the node cleanup
    above, as a side effect.
- `/cpp migrate v2 filters dismiss` sets `DONE` without converting anything, for servers that never used filters.

**Rotation mapping** (from v2 `Filter`):

  | Frame rotation | v3 entry |
  |---|---|
  | `CLOCKWISE` | DENY, EXACT |
  | `COUNTER_CLOCKWISE` | DENY, SIMILAR |
  | `FLIPPED` | ALLOW, SIMILAR |
  | anything else | ALLOW, EXACT |

  v2's "by meta" check matched the same item, the same tag or the same material. v3's `SIMILAR` is the closest fit.

---

## 6. Startup flow

`ChestsPlusPlus.onEnable` gains two steps, one call each:

```java
services = new Services(this, migrateV2Config() /* before loadSettings */, loadMessages());
registerFeatures(services);
openDatabase(services);
importV2Data(services);      // no-op unless data/storage.yml exists
...
```

- **`migrateV2Config()`**: if `config.yml` has v2 keys and none of v3's sections, rename it to `config-v2.yml`, write the v3
  default, and copy the values across:

  | v2 key | v3 key |
  |---|---|
  | `chestlinks-enabled` | `features.chestlinks` |
  | `autocrafters-enabled` | `features.autocraft` |
  | `hopper-filters-enabled` | `features.hopper-filters` |
  | `limit-chests` + `limit-chestlinks-amount` | `limits.chestlink-default` (`-1` when `limit-chests` is false) |
  | `should-animate-all-chests` | `chestlink.animate-all-nodes` |
  | `display_chestlink_armour_stand` | `chestlink.display.enabled` |
  | `display_autocraft_armour_stands` | `autocraft.display.enabled` |
  | `world-blacklist` (drop `""`) | `worlds.blacklist` |
  | `update-checker` | `update-checker.enabled` |
  | `update-checker-period`, `set-filter-itemframe-invisible`, `language-file` | dropped, and logged |

  This has to happen before `loadSettings()`. Otherwise `saveDefaultConfig()` sees the v2 file, keeps it, and v3 runs on
  defaults with stale keys left in the file.

- **`importV2Data()`**: runs only when `data/storage.yml` exists. It runs synchronously on the main
  thread before the listeners register, so nothing can link or open a group halfway through. It logs the report summary,
  writes the full report to `data/v2-migration-<timestamp>.log`, and renames the file once the flush has finished. If the
  import throws, it logs the error and **leaves the file where it is**. The plugin still starts with whatever v3 data it already
  has, and an admin can fix the problem and run the command.

Before importing, copy the data folder (except v3's `data.db` and earlier backups) to `v2-backup-<timestamp>/` inside it, so a
server can always go back to v2.

---

## 7. Command: `/cpp migrate v2`

Added to the root tree in `Commands`, gated by a new `chestsplusplus.admin.migrate` (default op). It works from the console.

| Command | Does |
|---|---|
| `/cpp migrate v2` | **Dry run.** Parses the file and replies with the report (groups, nodes, items, trust, renames, skips, unresolved worlds). Changes nothing. |
| `/cpp migrate v2 confirm` | Runs the import into the live model (§2), then shows the report. Safe to run more than once. |
| `/cpp migrate v2 status` | How many chunks are still waiting for cleanup, and whether frame scanning is on. |
| `/cpp migrate v2 cleanup [radius]` | Loads waiting chunks near the sender (players only). |
| `/cpp migrate v2 filters on-load` | Converts v2 hopper item frames in each chunk as it loads (§5). |
| `/cpp migrate v2 filters convert-all [world]` | Loads every chunk that has entities and converts it, with progress reports. Can be resumed. |
| `/cpp migrate v2 filters cancel` | Stops a running `convert-all`. |
| `/cpp migrate v2 filters dismiss` | Marks filter migration done without converting, which stops the warnings. |

- The file is looked up as `data/storage.yml`, then `data/storage.yml.v2-migrated`. An optional
  `file <name>` argument, relative to the data folder, covers backups.
- At runtime, groups are adopted straight into the live model on the main thread, which is fine because the import is pure
  computation plus a handful of `adopt` calls. If a v2 file ever turns out to be very large, parse it on an async task and do
  only the plan and apply on the main thread.
- Messages go in `messages.yml` as `MIGRATE_*` keys, sent with `services.send`.

---

## 8. Tests

- **Unit:** `V2Storage` against small v2 YAML files in `src/test/resources/v2/` copied from real v2 output: current format,
  `locations` legacy form, the old recipe type name, nulls, unknown world, bad recipe. Also name cleanup and clashes, rotation →
  filter mapping, and config key mapping.
- **MockBukkit integration:**
  - Startup import of a fixture creates the expected groups, members, trust and items.
  - The file is renamed.
  - A restart doesn't import again.
  - `confirm` run twice doesn't duplicate items.
  - A previously unresolved world is picked up on a re-run.
  - Name clashes with existing v3 groups get a suffix.
  - Chunk cleanup fixes facing from a wall sign and removes the sign.
- **E2E (Plugwright):** start a real v2 server with a scenario world and save, swap in the v3 jar, then check that ChestLinks
  open with their items, hoppers still feed them and AutoCrafters craft.
- **Manual checklist** (`docs/testing.md`): a large real-world `storage.yml`, a Multiverse world loaded after us, and v2 → v3 → v2
  rollback from the backup.

### 8.1 Manual test bed: a real v2 world, upgraded

Two Gradle tasks build a flat world with **v2.16 on Paper 1.21.7**, populate it with every case in §8.2, and then run it on
**v3 on Paper 26.3**. That's the same path a real server takes, Minecraft's own world upgrade (DFU) included.

```bash
./gradlew v2UpgradeFixture -Pchestsplusplus.acceptMinecraftEula=true -Pchestsplusplus.testPlayer=<your name>
```

```bash
./gradlew runV2Upgrade
```

```bash
./gradlew resetV2Upgrade
```

| Task | Does |
|---|---|
| `v2UpgradeFixture` | Builds `run/v2-upgrade/` from scratch with v2 (steps below), then copies it to `run/v2-upgrade-snapshot/`. |
| `runV2Upgrade` | Runs Paper 26.3 with the v3 jar in the foreground on `run/v2-upgrade/`, the way `runServer` does, with the hot-swap agent, so you can join, fix and re-check. |
| `resetV2Upgrade` | Restores `run/v2-upgrade/` from the snapshot (a few seconds), so you can retry the upgrade without rebuilding the fixture. |
| `runV2Server` | Runs the v2 server in the foreground on `run/v2-upgrade/`, for adding cases by hand before upgrading. |

**How `v2UpgradeFixture` works.** It reuses `StartE2eServer`/`StopE2eServer`, `DownloadFile` and `Rcon`, with different inputs:

1. Get the pieces:
   - Paper 1.21.7, pinned in `libs.versions.toml` as `v2PaperServer`/`v2PaperServerBuild`.
   - The v2 jar. v2 isn't published anywhere we can download from, so `buildV2Jar` builds it from `master` with Maven
     (`git archive`, then `mvn package`). On the JDK 25 toolchain it needs a newer Lombok, forced annotation processing, and a
     `lombok.config` that stops v3's config applying to it. `-Pchestsplusplus.v2Jar=<path>` skips the build.
   - ViaVersion, so you can join the v2 server with your normal client.
2. Write the fixture `server.properties`: the same flat world as E2E, `online-mode=false`, a new port pair, and creative mode.
   - Offline mode makes UUIDs depend only on the name (`OfflinePlayer:<name>`), so the fixture can use your UUID without network
     calls. You join both servers with the same name.
   - Copy in a v2 `config.yml` with non-default values (§8.2), so the config migration has something to check.
3. Start v2 on Java 21 with the helper plugin `v2-fixture` (below). Over RCON, run `v2fixture build <testPlayer> Alex`, then
   `save-all`, then stop. v2's `onDisable` calls `Config.save()`, so `storage.yml` ends up written by v2 itself.
4. Start v2 a second time without the helper and wait for `Done (`, then stop. This proves v2 reads its own file back without
   errors (the task fails if the log has a v2 stack trace), and it leaves the armour-stand displays v2 spawns in the world.
5. Snapshot the folder. Then copy `plugins/ChestsPlusPlus/data/storage.yml` to `src/test/resources/v2/fixture-storage.yml`, with
   the UUIDs normalised to fixed values, so the unit tests in §8 parse real v2 output rather than a hand-written file.

**The `v2-fixture` helper plugin** is a separate Gradle source set, `src/v2Fixture/java`, and is never shipped. It compiles
against `paper-api` 1.21.7 with the v2 jar as `compileOnly`. Its scenario uses **v2's own classes**, so the data is exactly what
v2 would write:

- `new ChestLinkStorage(owner, name, location, signLocation)` moves the chest's contents into the group.
- `addLocation`, `setPublic`, `addMember` and `setSortMethod` set up the rest of the group.
- `new AutoCraftingStorage(...)` with `setRecipe(recipe, items)`.
- `PlayerPartyStorage`/`PlayerParty` for parties.
- `Config.getStore()` to register everything.

The world itself is built with the Bukkit API: blocks, chest contents, wall signs written the way v2's `placeSign` writes them,
item frames with a rotation, and hoppers. One `Scenario` class lays the cases out on a grid at spawn, one case per 4-block
cell. A **standing** label sign in front of each cell names the case and the expected v3 result. They're standing signs, so the
migration can't mistake them for v2 link signs.

If a v2 constructor turns out to need a live `Player`, fall back to building the `ConfigStorage` maps in the helper and saving
them with `YamlConfiguration`. The items are still serialised by the real 1.21.7 server, but v2's constructors are skipped.

### 8.2 Scenario cases

The tester is `T`; the second player is `Alex` and never needs to join.

| Cell | v2 setup | Expected after upgrade |
|---|---|---|
| CL-basic | `T:Storage`, chests facing N and E. Items include enchanted tools, renamed items, potions, a written book and a filled shulker box. Sort `NAME`. | One group with 2 nodes, displays on the N and E faces, every item intact (DFU-upgraded), sort NAME, signs and armour stands gone |
| CL-double | `T:Double` on a double chest | Group kept, chest split into two singles |
| CL-names | `T`: `Iron Ore`, `iron` + `IRON`, a 40-character name, `§aGreen` | `Iron Ore` (unchanged), `iron` + `IRON_2`, cut to 32 characters, `Green`; the renames listed in the report |
| CL-access | `T:Public` public; `T:Shared` with member Alex; `Alex:Gift` with member T | Public flag and members kept; T can open `Alex:Gift` |
| CL-hoppers | Hopper → `T:Hoppers` chest → hopper → plain chest | Items still flow through after the upgrade |
| CL-stale | `T:Stale` with a location whose chest was broken afterwards | The node is dropped on chunk load and the group's items are kept |
| CL-nether | `T:Nether` in `world_nether`, which the v2 config blacklists | Imported anyway (the blacklist doesn't apply to an import) |
| CL-faraway | `T:Distant` at x=2000, pre-generated with forceload | Facing and sign cleanup only happen when you go there, or after `convert-all` |
| AC-shaped | Torch, on two tables (the second's sign on the east) | Matrix rebuilt, `RECIPE` match, crafts with coal or charcoal; the second table faces east |
| AC-shapeless | Bone meal | Matrix rebuilt, crafts |
| AC-complex | Repair recipe with items stored | Matrix from the stored items |
| AC-missing | A recipe key from a datapack that the fixture adds and then removes | Empty matrix, key kept, listed in the report |
| AC-empty | No recipe | Group with an empty matrix |
| Party | `T` owns the party `friends` with Alex | Alex is trusted by T |
| Filters | One hopper per rotation (NONE, FLIPPED, CLOCKWISE, COUNTER_CLOCKWISE), one with 3 frames, one frame at x=2004 | Warning shown to ops; after `filters on-load` or `convert-all`: ALLOW/EXACT, ALLOW/SIMILAR, DENY/EXACT, DENY/SIMILAR, frames removed and their items dropped |
| Config | `limit-chests: true`, `limit-chestlinks-amount: 5`, `world-blacklist: [world_nether]`, `display_autocraft_armour_stands: false` | `config-v2.yml` kept; the v3 config has `chestlink-default: 5`, the blacklist, and `autocraft.display.enabled: false`; `T` is flagged as over the limit |

`docs/testing.md` gets a **v2 upgrade** section that copies this table as a checklist. It also covers:

- running `/cpp migrate v2` (dry run), then `confirm` again after the upgrade, and checking nothing is duplicated;
- running `resetV2Upgrade`, then killing the server mid-upgrade and restarting it;
- checking that `v2-backup-*` is enough to roll back to v2 with `runV2Server`.

Worlds that aren't loaded, or don't exist, aren't in the fixture: v2 itself can't load a `storage.yml` that names a world that
isn't loaded yet, so the second v2 start would fail. `V2ImporterTest` covers both (`uid.dat` and unresolved worlds).

The fixture is also where the Plugwright E2E upgrade scenario in §8 starts from.

---

## 9. Order of work

0. The test bed (§8.1): the `v2-fixture` helper, `v2UpgradeFixture`, `runV2Upgrade` and `resetV2Upgrade`. It produces the
   real `storage.yml` that step 1 parses, and every later step is checked against it.
1. `V2Storage` + `V2Data` + unit tests against the fixture file. This is the riskiest part, so do it first.
2. `GroupStore.adopt` refactor, the `v2_source` column, and `Persistence.flush()` returning a future.
3. `V2Importer` + `ImportReport` + startup hook + backup and rename.
4. `/cpp migrate v2` (dry run, confirm, status).
5. `V2ConfigMigrator`.
6. `V2WorldCleanup` (facing, signs, armour stands, double chests) + `v2_blocks` store + `cleanup [radius]`.
7. Filter migration:
   - `migration_state` store, warnings for the console and ops, `filters on-load`/`dismiss`
   - per-chunk frame conversion
   - `convert-all`: region-header scan, throttled async loading with tickets, progress, cancel
8. Upgrade guide in README/docs: command and permission changes, with an old → new table.

---

## 10. Decisions

- **Item-frame filters:** opt in. Warn the console and ops until an admin picks `on-load`, `convert-all` or `dismiss` (§5).
  Converted frames are removed and their items dropped.
- **v2 signs:** removed silently, as v3 sign linking does.
- **Limits and blacklist:** ignored on import. Groups over a limit are flagged in the report.
- **Still open:** delete `v2-backup-*` after N successful starts, or leave them for admins to remove?
