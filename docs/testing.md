# Testing ChestsPlusPlus v3

Three automated layers run in CI (plan §10). This page lists what they cover and the **manual checklist** for what
they can't.

| Layer | Command | Covers |
|---|---|---|
| Unit | `./gradlew unitTest` | `BlockPos`, `Settings`, `Messages`, model indexes, access/trust, SQLite repository and migrations, display layout, item grouping, versions, command tree guard |
| MockBukkit | `./gradlew integrationTest` | Plugin lifecycle, persistence round trips, linking (signs, silk touch, limits, blacklist, access), breaking/explosions, hopper substitution, displays (item only), sorting, all commands, the icon grid, filters (semantics, PDC, enforcement, stall avoidance, editor), AutoCraft (planner, crafting, ChestLink inputs, scheduling and wake-ups, editor) |
| E2E (Plugwright) | `./gradlew e2e -Pchestsplusplus.acceptMinecraftEula=true` | Real hopper transfers through a ChestLink, display entities, `/cl list`, `/cl open`, `/cpp help`, a real filtered hopper behind a rejected slot, real torch crafting, crafting on the next tick after a recipe, input or output change, and the powered/unpowered crafter rule on Paper 26.3 |

Known automation limits:
- **MockBukkit** doesn't implement `TextDisplay#setBillboard`, redstone power, recipe matching or `PluginBootstrap`.
  `FailOnUnimplemented` turns any such gap into a test failure, so nothing is silently skipped.
- **E2E bots** (Mineflayer 26.1 via ViaBackwards) are kicked after about 3 s or on any movement (spike S6). E2E sets
  the world up over RCON with the never-shipped `/cpptest` harness and keeps bot interactions short. Dialogs can't be
  driven by bots at all.

## Manual checklist

Run each item on a native 26.3 client against `./gradlew runServer` (or the server you're testing).

### Linking and displays
- [ ] A `[ChestLink]` sign on a chest, a barrel and a copper chest creates the group. The sign disappears and the
      item display plus label appear on the sign's face.
- [ ] Display orientation is correct on all four faces for chests, barrels and crafting tables (spike S4: row A/B
      decides `DisplayLayout.ITEM_YAW_OFFSET`).
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

### Menus (spike S3)
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
