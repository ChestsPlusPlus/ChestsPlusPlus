# Testing ChestsPlusPlus v3

Three automated layers run in CI. This page lists what they cover and the **manual checklist** for what
they can't.

| Layer | Command | Covers |
|---|---|---|
| Unit | `./gradlew unitTest` | `BlockPos`, `Settings`, `Messages`, model indexes, access/trust, SQLite repository and migrations, display layout, item grouping, versions, command tree guard |
| MockBukkit | `./gradlew integrationTest` | Plugin lifecycle, persistence round trips, linking (signs, silk touch, limits, blacklist, access), breaking/explosions, hopper substitution, displays (item only), sorting, all commands, the icon grid, filters (semantics, PDC, enforcement, stall avoidance, editor), AutoCraft (planner, crafting, ChestLink inputs, scheduling and wake-ups, editor) |
| E2E (Plugwright) | `./gradlew e2e -Pchestsplusplus.acceptMinecraftEula=true` | Real hopper transfers through a ChestLink, display entities, `/cl list`, `/cl open`, `/cpp help`, a real filtered hopper behind a rejected slot, real torch crafting, crafting on the next tick after a recipe, input or output change, the powered/unpowered crafter rule, and hoppers, AutoCrafters and copper golems refused by a locked ChestLink exactly as by a locked vanilla chest (via a stand-in lock plugin) on Paper 26.3 |

Performance against v2 is measured separately; see [benchmarking.md](benchmarking.md).

Known automation limits:
- **MockBukkit** doesn't implement `TextDisplay#setBillboard`, redstone power, recipe matching or `PluginBootstrap`.
  `FailOnUnimplemented` turns any such gap into a test failure, so nothing is silently skipped.
- **E2E bots** (Mineflayer 26.1 via ViaBackwards) are kicked after about 3 s or on any movement. E2E sets
  the world up over RCON with the never-shipped `/cpptest` harness and keeps bot interactions short. Dialogs can't be
  driven by bots at all.
- **Copper golem fixtures** enclose each site and force-load the whole arena: a golem crossing into an adjacent chunk can stop
  ticking even though that chunk is loaded. Locks are installed before spawning, and refusal counters verify that each protected
  golem actually searched. Failed assertions capture diagnostics before removing the golems, groups and site tickets.

## Manual checklist

Run each item on a native 26.3 client against `./gradlew runServer` (or the server you're testing).

### Linking and displays
- [ ] A `[ChestLink]` sign on a chest, a barrel and a copper chest creates the group. The sign disappears, goes back
      into your inventory (not in creative), and the item display plus label appear on the sign's face. With
      `linking.consume-signs: true`, the sign is used up. A full inventory drops the returned sign at your feet.
- [ ] Display orientation is correct on all four faces for chests, barrels and crafting tables (this is what
      `DisplayLayout.ITEM_YAW_OFFSET` is tuned against).
- [ ] Displays are readable at about 16 blocks and at night, and disappear and reappear with chunk unload/load.
- [ ] `/cl add <group>` while looking at a chest links it; it's refused in a protected region (WorldGuard or
      GriefPrevention).
- [ ] A chest placed next to a linked chest stays single.
- [ ] Silk Touch breaking gives a named link item; placing it re-links and keeps the contents.
- [ ] Breaking the last node drops every item exactly once.

### ChestLink use
- [ ] Lids animate on open/close (one node, or all nodes with `animate-all-nodes`).
- [ ] Remote open from `/cl open`, the hub and the grid; closing returns to the grid when opened from it.
- [ ] Sorting (each mode) on open/close and on `/cl sort`.
- [ ] Hopper chains in and out; a dropper pointing into a linked chest adds to the group.
- [ ] A copper golem takes items from a linked copper chest and delivers them to a chest, and carries items from a copper chest into a
      linked chest. The linked blocks' own containers stay empty, a golem holding an item the group doesn't have walks past it, and
      `features.copper-golems: false` makes golems ignore linked blocks.

### Menus
- [ ] Hub search, paging, the group dialog (rename, public, sort mode, save), members, trust, and remove with
      confirmation.
- [ ] Every dialog button works once (single use) and expires after 10 minutes; no errors in the console.

### Hopper filters
- [ ] Sneak + right-click with an empty hand opens the editor; the normal hopper GUI doesn't open.
- [ ] Adding entries from the cursor doesn't consume items; click cycles exact → type → similar; shift-click removes;
      barrier clears.
- [ ] Every side shows a green pane followed by the allowed items, and a red pane followed by the denied items (only rows that have entries; Allow on top), as small inventory-style icons from the top-left.
- [ ] Allow/deny behaviour with real hopper chains, including a rejected first slot.

### AutoCraft
- [ ] The recipe editor shows the result; non-owners can view but not edit.
- [ ] Right-clicking a ghost cycles its match mode (shown in the tooltip); a repair recipe with both slots on "any item of
      this type" repairs any two damaged tools of that type.
- [ ] Hopper below: crafts unless the hopper is locked by redstone. Container below: only while the table is powered.
- [ ] Recipes with tag choices (e.g. any planks), and recipes that leave items behind (e.g. buckets).
- [ ] ChestLink inputs next to a crafter.

### Robustness
- [ ] `kill -9` the server after changes; on restart groups, nodes, inventories, recipes and trust are intact (WAL
      plus a flush every 30 s).
- [ ] `/cpp reload` applies config and message changes without duplicate listeners or tasks.
- [ ] No orphan display entities after a crash (`/execute if entity @e[type=item_display]` counts only live ones).

## v2 upgrade

A flat world built by ChestsPlusPlus v2 on Paper 1.21.7, with every upgrade case laid out south of spawn, then run on v3. See
[v2-migration.md](v2-migration.md#upgrade-test-bed) for how it's built.

```bash
./gradlew v2UpgradeFixture -Pchestsplusplus.acceptMinecraftEula=true -Pchestsplusplus.testPlayer=<your name>
```

```bash
./gradlew runV2Upgrade
```

```bash
./gradlew resetV2Upgrade
```

- `v2UpgradeFixture` builds v2 from `master` with Maven (it must be on your `PATH`, or pass `-Pchestsplusplus.maven=<path>`;
  `-Pchestsplusplus.v2Jar=<jar>` skips the build). It writes `run/v2-upgrade/`, a snapshot next to it, and
  `src/test/resources/v2/fixture-storage.yml` (the file v2 wrote, with the players' UUIDs fixed). `V2StorageTest` reads it when
  it is there, so commit it to keep that check running in CI.
- `runV2Upgrade` starts that folder on v3 with hot-swap. Join with a native 26.3 client under the name you gave (offline mode;
  you are op). `resetV2Upgrade` puts the v2 world back in a few seconds.
- `runV2Server` runs the same folder on v2 (with ViaVersion, so a current client can join) for adding cases by hand.

ChestLinks are on z=0, AutoCrafters on z=10 and hopper filters on z=20, facing spawn. A standing sign beside each case names it
and says what should happen. "Alex" is a second player who never joins.

### On first start
- [ ] The console logs the backup folder, an import summary (14 ChestLinks, 5 AutoCrafters, 1 trusted player) with a line per
      rename and missing recipe, a note that you are over the ChestLink limit of 5, and a warning that v2 hopper filters haven't been converted.
- [ ] `plugins/ChestsPlusPlus` has `config-v2.yml`, a v3 `config.yml` with `limits.chestlink-default: 5`, `worlds.blacklist:
      [world_nether]` and `autocraft.display.enabled: false`, `v2-backup-<time>/`, `v2-migration-<time>.log`, and
      `data/storage.yml.v2-migrated`.
- [ ] Joining as op shows the filter warning with clickable `[Convert as chunks load]`, `[Convert every chunk now]` and `[Dismiss]`.

### Cases near spawn
- [ ] **CL-basic:** `Storage` opens on both chests with every item intact: an enchanted damaged sword, a renamed name tag, a
      potion, a written book, and a shulker box still holding its diamonds and emeralds. Displays sit on the N and E faces. The v2
      signs and armour stands are gone. Sorting is by name.
- [ ] **CL-double:** the double chest is now two single chests; `Double` is linked to the left one.
- [ ] **CL-names:** `/cl list` shows `Iron Ore`, `iron` and `IRON` (whichever v2 saved second gets `_2`), a 32-character name
      and `Green`.
- [ ] **CL-access:** `Public` is public, `Shared` has Alex as a member, and you can open `Alex:Gift`.
- [ ] **CL-hoppers:** cobblestone keeps flowing from the top hopper through the ChestLink into the plain chest.
- [ ] **CL-stale:** nothing is left linked where the broken chest was; `Stale` still exists with no blocks.
- [ ] **CL-nether:** `Nether` was imported even though `world_nether` is blacklisted.
- [ ] **AC-shaped:** `torches` crafts with coal or charcoal. AutoCraft displays are off (the v2 config turned them off); set
      `autocraft.display.enabled: true` and `/cpp reload` to check the second table's display faces east (its v2 sign was there).
- [ ] **AC-shapeless / AC-complex:** `bonemeal` and `repair` have their recipes (repair has two damaged pickaxes as ghosts).
- [ ] **AC-missing / AC-empty:** `gone` has no recipe (its datapack was removed before the upgrade) and the report said so;
      `empty` has none.
- [ ] **Party:** `/cpp trust list` shows Alex.

### Commands
- [ ] `/cpp migrate v2 status` shows the blocks still waiting (CL-faraway) and the filter state.
- [ ] `/cpp migrate v2` and `/cpp migrate v2 confirm` say everything has already been imported; no items are duplicated.
- [ ] `/cpp migrate v2 filters on-load`: the filter hoppers near spawn convert straight away (allow exact, allow similar, deny
      exact, deny similar, and coal + iron allowed with dirt denied). Frames drop with their items, and the warning stops.
- [ ] `resetV2Upgrade`, then `/cpp migrate v2 filters convert-all`: progress every 5% in chat and the console, the far hopper
      at x=2004 converts, `Distant` at x=2000 gets its display on the right face, and the state becomes `done`.
- [ ] `/cpp migrate v2 filters cancel` mid-run, then `convert-all` again, carries on where it stopped.
- [ ] `/cpp migrate v2 cleanup 32` near x=2000 finishes `Distant` without visiting it.
- [ ] `resetV2Upgrade`, `kill -9` the server straight after the import message, then restart: a transaction committed before the crash
      is gated by durable completion; an uncommitted transaction retries from scratch, with no duplication.
- [ ] Rolling back: copy `v2-backup-<time>/*` over `plugins/ChestsPlusPlus`, rename `config-v2.yml` to `config.yml`, and
      `runV2Server` shows the v2 world working as before.

### Migration regressions

- [ ] Delete an imported group through `/cl remove`, revoke imported party trust, then restore `storage.yml` from its backup.
      Restart and try preview/confirm and an explicit `.v2-migrated` filename: the group/items/trust never return.
- [ ] Leave a world unavailable at import, rename/edit the imported group, restart, then load the world and its relevant chunk:
      its original group receives the location and keeps current items/members. Deleted/recreated names inherit nothing.
- [ ] Break or claim a deferred block before its chunk loads: the pending location is reported and discarded permanently.
- [ ] Obstruct the YAML rename after import: completion remains durable and the original file cannot replay.
- [ ] `filters convert-all <world>` keeps `on-load`. A failed scan/load or entity timeout releases tickets, leaves unscanned chunks
      available for retry and retains `on-load`; a successful global scan of all loaded worlds can become `done`.
- [ ] Click the rendered `/cl list` and `/ac list` entries for own and other-owner names containing spaces.

SQLite transaction failure/retry, durable completion and pending rows, synchronous adoption rollback, delayed entities,
timeout/event retry without duplicate drops, conversion scope and cancellation are also covered by JVM regression tests.
Existing custom `chestlink.list-entry` and `autocraft.list-entry` overrides should use `<open>...</open>` for the command link,
matching the bundled defaults. MiniMessage does not substitute `<ref>` inside a quoted click-event argument.
