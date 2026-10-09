# Upgrading from v2

v3 imports a v2 install's data and config once, automatically, on its first start. Commands, permissions and language files
start fresh; the README lists the new ones.

## What gets imported

v2 and v3 have the same plugin name, so v3 starts in v2's `plugins/ChestsPlusPlus/` folder and finds:

| v2 artefact | v3 equivalent |
|---|---|
| ChestLinks in `data/storage.yml` | ChestLink groups with their linked blocks and all 54 slots of items |
| AutoCrafters in `data/storage.yml` | AutoCraft groups with their linked tables and recipe |
| Parties | Owner-wide trust: every party member is trusted by the party's owner, which grants the same access |
| `config.yml` with v2 keys | v3 `config.yml` (the old file is kept as `config-v2.yml`) |
| Wall sign in front of every linked block | Removed; the v3 display replaces it |
| Marker armour stands | Removed; v3 uses item displays |
| Item frames on hoppers (filters) | Hopper filters, once an admin opts in (see [Hopper filters](#hopper-filters)) |
| Linked double chests | Split into single chests, which is all v3 links |
| `lang/*.properties` | Not migrated; `messages.yml` starts fresh |

Group owners, public flags, members and sort modes carry over as they are. Only v2's `data/storage.yml` is read: a server on a
release older than v2 should start v2 once first, so it converts its old `chests.yml`.

### Names

v3 group names are up to 32 characters of `A-Z a-z 0-9 _ -` with single spaces between words, and are unique per owner and type,
ignoring case. v2 names are cleaned up to fit: colour codes are stripped, other invalid characters become `_`, long names are cut
to 32 characters, and a clash gets `_2`, `_3`, and so on. The import report lists every rename.

### AutoCraft recipes

Shaped and shapeless recipes are rebuilt from the server's recipe, and every slot matches anything the recipe accepts (any planks
for "any planks"). Complex recipes use the items v2 stored. A recipe the server no longer has (a removed datapack) leaves an empty
matrix and is listed in the report.

### Limits, blacklist and features

The import is an admin operation, so it ignores per-player limits and the world blacklist: refusing data an admin is moving over
would be worse. Groups that end up over a limit are flagged in the report. AutoCraft groups are imported even when
`features.autocraft` is off, so the feature can be turned on later.

## First start

1. **Config.** If `config.yml` has v2 keys and no v3 sections, it's renamed to `config-v2.yml` and its values are copied into a
   fresh v3 config:

   | v2 key | v3 key |
   |---|---|
   | `chestlinks-enabled` | `features.chestlinks` |
   | `autocrafters-enabled` | `features.autocraft` |
   | `hopper-filters-enabled` | `features.hopper-filters` |
   | `limit-chests` + `limit-chestlinks-amount` | `limits.chestlink-default` (`-1` when `limit-chests` is false) |
   | `should-animate-all-chests` | `chestlink.animate-all-nodes` |
   | `display_chestlink_armour_stand` | `chestlink.display.enabled` |
   | `display_autocraft_armour_stands` | `autocraft.display.enabled` |
   | `world-blacklist` | `worlds.blacklist` |
   | `update-checker` | `update-checker.enabled` |
   | `update-checker-period`, `set-filter-itemframe-invisible`, `language-file` | dropped, and logged |

2. **Backup.** The data folder (except v3's `data.db`) is copied to `v2-backup-<timestamp>/` inside it, so the server can always
   go back to v2.
3. **Import.** It runs on the main thread before anything else can use the plugin. The summary is logged, and the full report is
   written to `v2-migration-<timestamp>.log`.
4. **Rename.** Once the import is saved, `data/storage.yml` is renamed to `storage.yml.v2-migrated`.

If the import fails, the error is logged and `storage.yml` is left where it is. The plugin still starts, and the import can be
retried with `/cpp migrate v2 confirm` once the problem is fixed.

### The import runs once

Completion is recorded in `data.db` in the same transaction as the imported data, so it survives deleting every imported group
or revoking trust. After that, every import command reports completion without reading any YAML. `.v2-migrated` files are never
imported, even by explicit filename, and copying one back to `storage.yml` doesn't bypass completion. A crash before the save
leaves nothing behind, so the next start retries; a crash after it can't import twice.

## Worlds that aren't loaded yet

A v2 location whose world isn't loaded is looked up by the world's `uid.dat`, which covers Multiverse worlds that load after
ChestsPlusPlus. If the world can't be found at all, the location is kept as **unresolved** and attached when that world and
chunk load, as long as the group still exists and the block is still the right type and not linked to anything else. Renaming the
group keeps its unresolved locations; deleting it discards them. `/cpp migrate v2 status` shows how many are left.

## World cleanup

Signs, armour stands, block facing and double chests need the world itself, and loading every chunk at startup could take tens
of seconds on a large server. So each imported block is cleaned up when its chunk first loads:

- If the block is no longer the right type, it's unlinked. Its items stay in the group.
- Facing comes from the v2 wall sign, which is then removed; otherwise from the chest's own facing.
- Double chests are split, and v2's armour stands are removed.

A block a player has unlinked since the import is never linked again. `/cpp migrate v2 status` shows how many chunks are still
waiting, and `/cpp migrate v2 cleanup [radius]` loads the ones near you.

## Hopper filters

v2 filters are item frames on hoppers anywhere in the world, so converting them means looking at chunks. Nothing is scanned until
an admin chooses. Until then, the console and ops (on join) are warned that v2 filters haven't been converted.

- `/cpp migrate v2 filters on-load` converts each chunk as players reach it.
- `/cpp migrate v2 filters convert-all [world]` loads every chunk that has entities and converts it, with progress every 5%. One
  run at a time; `cancel` stops it, and running it again resumes, because converted chunks are marked and skipped. Only a
  successful run over every loaded world finishes the conversion; load every world you want covered first, or keep `on-load` on
  for worlds that load later.
- `/cpp migrate v2 filters dismiss` stops the warnings without converting, for servers that never used filters.

Each converted frame becomes a filter entry on its hopper, and the frame is removed with its item dropped, since the v3 filter
display now shows it:

| Frame rotation | v3 entry |
|---|---|
| Clockwise | Deny, exact |
| Counter-clockwise | Deny, similar |
| Flipped | Allow, similar |
| Anything else | Allow, exact |

## Commands

All need `chestsplusplus.admin.migrate` (op by default) and work from the console.

| Command | Does |
|---|---|
| `/cpp migrate v2` | Dry run: replies with the report (groups, blocks, items, trust, renames, skips, unresolved worlds). Changes nothing. |
| `/cpp migrate v2 confirm` | Runs the import and shows the report once it's saved. |
| `/cpp migrate v2 [confirm] file <name>` | Previews or imports another file inside the data folder instead of `data/storage.yml`. |
| `/cpp migrate v2 status` | Import completion, unresolved locations, chunks waiting for cleanup and filter state. |
| `/cpp migrate v2 cleanup [radius]` | Loads the waiting chunks near you (players only). |
| `/cpp migrate v2 filters <on-load\|convert-all [world]\|cancel\|dismiss>` | See [Hopper filters](#hopper-filters). |

## Rolling back

Stop the server, replace the data folder's contents with the `v2-backup-<timestamp>/` copy, and put the v2 jar back. The world
changes (signs, armour stands, item frames, split double chests) aren't undone, so roll back before players have been on for long.

## Upgrade test bed

Gradle tasks build a flat world with **v2 on Paper 1.21.7**, populate it with every case below, then run it on **v3 on Paper
26.3**. That's the same path a real server takes, Minecraft's own world upgrade included.

```bash
./gradlew v2UpgradeFixture -Pchestsplusplus.acceptMinecraftEula=true -Pchestsplusplus.testPlayer=<your name>
./gradlew runV2Upgrade
./gradlew resetV2Upgrade
```

| Task | Does |
|---|---|
| `v2UpgradeFixture` | Builds `run/v2-upgrade/` from scratch with v2 (steps below), then copies it to `run/v2-upgrade-snapshot/`. |
| `runV2Upgrade` | Runs Paper 26.3 with the v3 jar in the foreground on `run/v2-upgrade/`, like `runServer`, with the hot-swap agent. |
| `resetV2Upgrade` | Restores `run/v2-upgrade/` from the snapshot in a few seconds, to retry the upgrade without rebuilding. |
| `runV2Server` | Runs the v2 server in the foreground on `run/v2-upgrade/`, for adding cases by hand before upgrading. |

**How `v2UpgradeFixture` works.** It reuses the E2E server tasks with different inputs:

1. Get the pieces:
   - Paper 1.21.7, pinned in `libs.versions.toml` as `v2PaperServer`/`v2PaperServerBuild`.
   - The v2 jar. v2 isn't published anywhere we can download from, so `buildV2Jar` builds it from `master` with Maven. On the
     JDK 25 toolchain it needs a newer Lombok, forced annotation processing, and a `lombok.config` that stops v3's config applying
     to it. `-Pchestsplusplus.v2Jar=<path>` skips the build.
   - ViaVersion, so you can join the v2 server with your normal client.
2. Write `server.properties`: the same flat world as E2E, `online-mode=false`, its own ports, and creative mode. Offline mode makes
   UUIDs depend only on the name, so you join both servers with the same name. A v2 `config.yml` with non-default values is copied
   in, so the config migration has something to check.
3. Start v2 on Java 21 with the `v2-fixture` helper plugin, run `v2fixture build <testPlayer> Alex` over RCON, then `save-all` and
   stop. v2 writes `storage.yml` itself on disable.
4. Start v2 again without the helper and wait for it to finish starting. This proves v2 reads its own file back (the task fails on
   a v2 stack trace), and leaves the armour-stand displays v2 spawns.
5. Snapshot the folder, and copy `storage.yml` to `src/test/resources/v2/fixture-storage.yml` with fixed UUIDs, so the unit tests
   parse real v2 output.

**The `v2-fixture` helper plugin** is its own source set, `src/v2Fixture/java`, and is never shipped. It compiles against
`paper-api` 1.21.7 with the v2 jar as `compileOnly`, and builds each case with v2's own classes, so the data is exactly what v2
writes. One `Scenario` class lays the cases out south of spawn, one per cell, each with a **standing** label sign naming the case
and the expected result (standing, so the upgrade can't mistake it for a v2 link sign).

### Test cases

The tester is `T`; the second player is `Alex` and never needs to join.

| Cell | v2 setup | Expected after upgrade |
|---|---|---|
| CL-basic | `T:Storage`, chests facing N and E. Items include enchanted tools, renamed items, potions, a written book and a filled shulker box. Sort `NAME`. | One group with 2 blocks, displays on the N and E faces, every item intact, sort NAME, signs and armour stands gone |
| CL-double | `T:Double` on a double chest | Group kept, chest split into two singles |
| CL-names | `T`: `Iron Ore`, `iron` + `IRON`, a 40-character name, `§aGreen` | `Iron Ore` (unchanged), `iron` + `IRON_2`, cut to 32 characters, `Green`; the renames listed in the report |
| CL-access | `T:Public` public; `T:Shared` with member Alex; `Alex:Gift` with member T | Public flag and members kept; T can open `Alex:Gift` |
| CL-hoppers | Hopper → `T:Hoppers` chest → hopper → plain chest | Items still flow through after the upgrade |
| CL-stale | `T:Stale` with a location whose chest was broken afterwards | The block is dropped on chunk load and the group's items are kept |
| CL-nether | `T:Nether` in `world_nether`, which the v2 config blacklists | Imported anyway (the blacklist doesn't apply to an import) |
| CL-faraway | `T:Distant` at x=2000, pre-generated with forceload | Facing and sign cleanup only happen when you go there, or after `convert-all` |
| AC-shaped | Torch, on two tables (the second's sign on the east) | Matrix rebuilt, crafts with coal or charcoal; the second table faces east |
| AC-shapeless | Bone meal | Matrix rebuilt, crafts |
| AC-complex | Repair recipe with items stored | Matrix from the stored items |
| AC-missing | A recipe key from a datapack that the fixture adds and then removes | Empty matrix, key kept, listed in the report |
| AC-empty | No recipe | Group with an empty matrix |
| Party | `T` owns the party `friends` with Alex | Alex is trusted by T |
| Filters | One hopper per rotation (none, flipped, clockwise, counter-clockwise), one with 3 frames, one frame at x=2004 | Warning shown to ops; after `filters on-load` or `convert-all`: allow/exact, allow/similar, deny/exact, deny/similar, frames removed and their items dropped |
| Config | `limit-chests: true`, `limit-chestlinks-amount: 5`, `world-blacklist: [world_nether]`, `display_autocraft_armour_stands: false` | `config-v2.yml` kept; the v3 config has `chestlink-default: 5`, the blacklist, and `autocraft.display.enabled: false`; `T` is flagged as over the limit |

[testing.md](testing.md#v2-upgrade) has these as a checklist. Worlds that aren't loaded, or don't exist, aren't in the fixture,
because v2 itself can't load a `storage.yml` that names one; `V2ImporterTest` covers both.
