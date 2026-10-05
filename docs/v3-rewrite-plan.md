# ChestsPlusPlus v3: Paper Rewrite Plan

**Status:** Implemented on `v3`; see §14 for status and open items · **Date:** 3 October 2026 · **Target:** Paper 26.x (Java 25)

v3 is a ground-up rewrite of ChestsPlusPlus as a native **Paper plugin**. It keeps the core gameplay: ChestLinks, AutoCraft, hopper filters and sharing. It replaces the Spigot-era plumbing with modern Paper APIs, a cleaner architecture and a performance-first runtime. It ships as a new major version, so **backwards compatibility with v2 data, commands, permissions and config is explicitly not a goal**.

---

## 1. Decisions (agreed)

| Topic | Decision |
|---|---|
| Platform | **Paper 26.x only**, Java 25, `paper-plugin.yml`. No Spigot fallback, reflection or NMS. Folia is not a target. |
| Persistence | **SQLite** using the `sqlite-jdbc` driver Paper bundles, accessed through **JDBI** (downloaded at startup by `ChestsPlusPlusLoader`). The live model stays in memory; SQLite is a write-behind save layer. |
| Signs | **Sign → display.** Placing a `[ChestLink]`/`[AutoCraft]` sign still creates the link. The sign is then removed and replaced by a non-persistent `ItemDisplay` and a `TextDisplay` label. |
| ChestLink menu | **Dialog hub + GUI grid.** A Dialog-based manager (search, per-group actions) plus a lightweight in-house icon grid for visual browsing. |
| Sharing | **Trust lists** (owner-wide) plus per-group members. The party/invite system is removed. |
| Hopper filters | **Filter UI.** Sneak + right-click a hopper to edit filters. Filters live in the hopper's PDC and are shown as small `ItemDisplay`s on the hopper faces. Item-frame filters are removed. |
| AutoCraft | **Kept and rebuilt** (shared group recipes, ghost-item recipe editor, multi-side inputs, ChestLink inputs). |
| Kept extras | bStats, update checker, limits + world blacklist, sorting. |
| Identity | Name stays **ChestsPlusPlus** (same data folder). New base package `com.jamesdpeters.chestsplusplus`, and permissions move to `chestsplusplus.*`. |
| Build | **Gradle (Kotlin DSL)** + shadow + run-paper. |
| i18n | No language files in v3. All text goes through one message catalogue so localisation can be added later (see §5.11). |

---

## 2. Audit of v2: what we're fixing

### 2.1 Structural problems
- **Three Maven modules for one plugin.** `ChestsPlusPlusAPI` and `ChestsPlusPlus_1_21_R1` exist only to host `MaterialChecker`, which compensates for armor-stand poses and is no longer needed with `ItemDisplay`.
- **Static global state everywhere** (`Config`, `ChestsPlusPlus.PLUGIN`, `PartyMenu.menus`, `ChestLinkMenu.menus`). `menus` are keyed by `Player` objects and never cleared, so they leak on quit.
- **God classes.** `AbstractStorage` (~670 LOC) mixes persistence, membership, armor-stand rendering, sign packets and inventory handling. `StorageType` mixes the registry, the event listener, sign placement and permission checks.
- **The sign is the source of truth.** `getStorage(Location)` reads the sign's `BlockState` to discover the group on every cache miss. Misses are never cached, and the cache is never invalidated on group delete or rename.
- **Duplicated command classes.** `ChestLinkCommand` and `AutoCraftCommand` are about 90% the same, use hand-rolled tab completion, and mix `ChatColor` strings with `Message` lookups.
- **Two third-party UI libraries.** SmartInvs (pinned to a commit) and AnvilGUI (NMS-dependent, breaks every MC update).

### 2.2 Correctness bugs found while reading
- **Off-thread serialisation.** `Config.saveASync()` serialises live Bukkit inventories on a worker thread while the main thread mutates them, which risks a corrupted save or a CME.
- **Full rewrites.** The whole dataset (every inventory as verbose YAML maps) is rewritten on every add, remove and world save.
- **Async mutations.** The member `add`/`remove`/`add-to-all` commands mutate storage from `runTaskAsynchronously` and call blocking `Bukkit.getOfflinePlayer(name)`.
- **`SETPUBLIC` fall-through.** `SETPUBLIC` falls through into `RENAME` when the group is missing, and broadcasts `"Storage null"` to the whole server.
- **Unchecked `/chestlink sort` arguments.** Without a method it throws `ArrayIndexOutOfBounds`. With a bad method, `Enum.valueOf` throws.
- **`/chests++ reload` calls `onEnable()` again.** This re-registers every listener, task and bStats instance.
- **Party invites run `performCommand("tellraw …")` as the invited player.** This fails for non-ops.
- **Listener per sign placement.** `StorageListener` registers a brand-new `TempListener` for every sign placed against a valid block. If the `SignChangeEvent` never fires, it leaks.
- **Inventory drop removes too much.** `dropInventory` calls `inventory.remove(item)` inside the loop, which removes *all* similar stacks.
- **Update notices are broadcast.** The update checker broadcasts to every player.

### 2.3 Performance hot spots (see `docs/performance-investigation.md`)
- **Per-group repeating tasks.** There is one `VirtualChestToHopper` task per ChestLink group (every 8 ticks) and one `CraftItems` task per AutoCraft group (every 20 ticks). Task count scales with data size, not activity.
- **Sign hiding.** This sends fake-air block packets to every nearby player every 5 ticks per location, plus restore and correction logic. It is the biggest remaining hotspot in the latest profile.
- **Hopper → ChestLink.** The handler cancels the `InventoryMoveItemEvent`, reschedules a manual transfer 1 tick later, then `clear()`s the physical chest.
- **Hopper filters.** `getNearbyEntities` runs on every hopper move into a filtered hopper.
- **AutoCraft.** Every attempt copies each input and output inventory (`Bukkit.createInventory` + `setContents`) and snapshots blocks with `getState()`.
- **Sorting.** Sorting is O(n²) (`stream().filter(isSimilar)` inside comparators) and runs one tick after *every* click.
- **Armor stands.** They are persistent entities, so startup has to sweep every entity in every world to clean up leftovers.

---

## 3. Target architecture

### 3.1 Principles
1. **The in-memory model is authoritative.** The world, signs and entities are views of it. Storage is a save layer.
2. **O(1) on hot paths.** Hopper, inventory and interaction events resolve through a packed-position index, never through world reads or entity scans.
3. **Event-driven, not polling.** Use vanilla mechanics where possible (`HopperInventorySearchEvent`), and a few central tickers instead of per-object tasks.
4. **Main thread for game state, one dedicated thread for I/O.** No Bukkit objects cross threads; only immutable snapshots (byte arrays, records) do.
5. **Small, testable units.** Pure logic (filters, sorting, recipe planning, access control, persistence mapping) has no Bukkit dependency where practical.
6. **Nothing persistent in the world that the plugin owns,** other than hopper filter PDC. All display entities are `setPersistent(false)`.

### 3.2 Package layout (`com.jamesdpeters.chestsplusplus`)

```
ChestsPlusPlusBootstrap        PluginBootstrap: Brigadier command registration (LifecycleEvents.COMMANDS)
ChestsPlusPlus                 JavaPlugin: wires services, owns lifecycle (enable/disable/reload)
core/
  BlockPos                     record(worldId, x, y, z) + packed long key + chunk key
  Services                     tiny explicit service container (no static singletons)
  scheduler/Tickers            central repeating tasks (display queue, autocraft, persistence flush)
config/
  Settings                     immutable record tree loaded from config.yml; swapped atomically on reload
message/
  Messages                     MiniMessage catalogue (English defaults) + Placeholders helpers
model/
  StorageGroup (sealed)        ChestLinkGroup | AutoCraftGroup: id, type, owner, name, public, members
  Node                         a linked block: BlockPos + facing + owning group
  GroupRegistry                (type, owner, name) → group; id → group
  NodeIndex                    packed BlockPos → Node; chunkKey → nodes (for chunk load/unload)
access/
  AccessService                canAccess(player, group): owner | public | member | trusted | bypass
  TrustService                 owner → trusted UUIDs
chestlink/
  ChestLinkService             create/link/unlink/rename/remove/sort, open (local/remote), drop logic
  ChestLinkHolder              custom InventoryHolder for the shared 54-slot inventory
  HopperBridge                 HopperInventorySearchEvent + dropper/crafter redirection
  ChestLinkListener            interact/open/close/break/place/explode/piston/silk-touch
  sort/Sorter                  O(n log n) condensing sort
autocraft/
  AutoCraftService             recipe editing, crafting ticker, backoff
  RecipeEditorHolder           ghost-item 3x3 editor (custom WORKBENCH inventory)
  CraftPlanner                 pure: plans extraction from inputs without copying inventories
filter/
  HopperFilter                 record(ItemStack template, Mode mode, Match match)
  FilterIndex                  BlockPos → compiled filters (loaded from hopper PDC on chunk load)
  FilterEditorHolder           sneak-click editor GUI
  FilterListener               InventoryMoveItemEvent / InventoryPickupItemEvent
  ItemGrouping                 precomputed Material → "similar group" bitset from item tags
display/
  DisplayService               spawns/updates ItemDisplay + TextDisplay for nodes and filters
  DisplayLayout                pure maths: transforms per block type/face
link/
  SignLinkListener             sign → link conversion (SignChangeEvent)
  LinkItem                     silk-touch item PDC encode/decode
ui/
  dialog/                      ChestLinkHubDialog, GroupDialog, MembersDialog, TrustDialog, ConfirmDialog
  menu/                        Menu framework: MenuHolder, Button, PaginatedMenu (≈200 LOC total)
  menu/ChestLinkGridMenu       visual icon grid
command/
  Commands                     Brigadier trees for /chestlink, /autocraft, /chestsplusplus
  arguments/GroupArgument      custom argument with suggestions (incl. owner:group for shared groups)
persistence/
  Database                     JDBI handle, PRAGMAs, migrations (db/migration/V<n>.sql, user_version)
  Persistence                  write-behind engine: dirty keys per store, flush on the I/O thread, final flush
  GroupStore, TrustStore       how each kind of thing is snapshotted, written, deleted and loaded (Store)
  RecordTable                  generated upsert/delete/select SQL for a record-shaped table
integration/
  Metrics (bStats), UpdateChecker (Modrinth/Hangar via java.net.http)
```

### 3.3 Threading model
- **Main thread:** all world, entity, inventory and model mutation. Dialog and click callbacks are re-dispatched to the main thread if Paper delivers them off-thread (spike S3).
- **`persistence-io` (single thread):** all JDBC writes. It receives immutable snapshots (ids, primitives, cloned `ItemStack`s, which it serialises). Because there is one writer, there is no locking.
- **Async pool (Paper scheduler):** update checks only.

### 3.4 Plugin lifecycle
- `paper-plugin.yml` declares the bootstrapper (commands) and the main class. Commands are registered through `LifecycleEvents.COMMANDS`, so the plugin has no `plugin.yml` command section.
- **Enable** (fixed order): load settings → load messages → open DB → load model (each store reads its rows on the main thread, before anything is flushed) → build indexes → register listeners → start tickers → spawn displays for already-loaded chunks.
- **Disable:** stop tickers → despawn displays → final flush, waiting for the I/O thread → close DB.
- **Reload** (`/cpp reload`) only re-reads `config.yml` and messages and re-applies them (display toggles, limits, blacklist, intervals). It never re-runs enable.

---

## 4. Persistence design (SQLite)

### 4.1 Why SQLite (and why the earlier H2 attempt stalled)
The `Database_Migration` branch tried to make the DB the runtime source of truth ("probably going to stick with in memory method for now"). v3 keeps the **in-memory model as the runtime truth** and uses SQLite only as a durable, incremental save layer:
- **Offline owners:** every group is loaded into memory at startup, so offline players' ChestLinks work everywhere (hoppers, remote open, AutoCraft). Player-PDC or chunk-PDC storage would *not* work offline, which rules those approaches out.
- **No new dependency:** Paper 26.3 ships `org.xerial:sqlite-jdbc` 3.49 (checked in `META-INF/libraries.list`).
- **Writes:** writes are transactional and incremental (only dirty groups), and WAL mode makes them crash-safe.
- **Item format:** items are stored with Paper's `ItemStack.serializeItemsAsBytes(...)`. This is compact, includes the data version, and is upgraded by DataFixer on load across Minecraft updates.

### 4.2 Schema (v1)
```sql
PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON;

CREATE TABLE groups (
  id          INTEGER PRIMARY KEY,
  type        TEXT    NOT NULL,            -- 'CHESTLINK' | 'AUTOCRAFT'
  owner       BLOB    NOT NULL,            -- 16-byte UUID
  name        TEXT    NOT NULL COLLATE NOCASE,
  is_public   INTEGER NOT NULL DEFAULT 0,
  sort_mode   TEXT,                        -- chestlink only
  created_at  INTEGER NOT NULL,
  items       BLOB,                        -- a ChestLink's inventory or an AutoCraft matrix
  recipe_key  TEXT                         -- autocraft only
);
CREATE TABLE group_members (group_id INTEGER REFERENCES groups ON DELETE CASCADE, member BLOB, PRIMARY KEY (group_id, member));
CREATE TABLE nodes (
  world BLOB, x INTEGER, y INTEGER, z INTEGER,
  group_id INTEGER NOT NULL REFERENCES groups ON DELETE CASCADE,
  facing TEXT NOT NULL,
  PRIMARY KEY (world, x, y, z)
);
CREATE INDEX nodes_group ON nodes(group_id);
CREATE TABLE trust (owner BLOB, trusted BLOB, PRIMARY KEY (owner, trusted));
```
Names are unique per type and owner in `GroupRegistry`, not in the database: a save can rename several groups at once, and a `UNIQUE` constraint would reject one taking a name another still holds earlier in the same transaction.

Migrations are versioned with `PRAGMA user_version`, using numbered SQL files (`src/main/resources/db/migration/V<n>.sql`). Until v3 first ships, `V1.sql` is edited in place (clear dev databases when it changes); after that a shipped file is never edited.

### 4.3 Write-behind
- **Stores:** each kind of thing (groups, trust) is a `Store` that says how to snapshot, write, delete and load it. The `Persistence` engine knows nothing about chests; it keeps a dirty key set per store.
- **What marks a group dirty:** metadata, member and node changes, viewer close, `InventoryMoveItemEvent` at MONITOR where either side's holder is a `ChestLinkHolder`, AutoCraft output, programmatic changes, and every `HopperBridge` substitution (S1), which covers servers with `hopper.disable-move-event: true`, where the move event never fires. A dirty group is saved whole.
- **Flush** (default every 30 s, configurable, plus on `WorldSaveEvent`): take and clear the dirty keys, snapshot them on main (a removed group snapshots to null and is deleted; item stacks are cloned), and write everything in one transaction on the I/O thread, where the clones are serialised.
- **Ordering:** the single I/O thread runs batches in order, so a group that changes while its batch is being written is simply saved again by the next flush.
- **Retries:** a failed batch is logged and its keys are marked dirty again, so the retry writes their current state.
- **Final flush:** on disable, flush, wait up to 30 s for the I/O thread, then close the database.

### 4.4 Not in the DB
Hopper filters live in the hopper block entity's PDC. They travel with the block (schematics, WorldEdit) and need no index rebuild beyond chunk load.

---

## 5. Feature designs

### 5.1 Domain model & index
- `BlockPos` is a record with a packed `long` key (x 26 bits, z 26 bits, y 12 bits) per world. `NodeIndex` is a `Map<UUID, Long2ObjectOpenHashMap<Node>>`, or a plain `HashMap<Long, Node>` if we avoid fastutil.
- **Chunk lookup:** a `chunkKey → List<Node>` map gives O(1) "what's in this chunk" for display spawning and validation.
- **Lazy validation:** on chunk load, nodes whose block is no longer a valid type (e.g. WorldEdit removed it) are unlinked and logged.
- **Group lookup:** by id, and by `(type, owner, lower(name))`. "Member of" and "trusted by" reverse indexes make menus and tab completion O(k), not O(all groups).

### 5.2 Linking lifecycle (sign → display)
1. **Create via sign.** The player places a sign against a chest, barrel or crafting table and writes `[ChestLink]` / `<group>` (optionally `owner:group`). On `SignChangeEvent`:
   - Validate permission, world blacklist and limit.
   - Validate access to the target group.
   - Check that the block is not already linked.
   - Then create or link, **remove the sign block**, and spawn the display.

   Because this is a real placement, protection plugins already approved it.
2. **Create via command.** `/cl add <group>` targets the looked-at block and needs no sign. To respect protection plugins, fire a synthetic `PlayerInteractEvent(RIGHT_CLICK_BLOCK)` and require `useInteractedBlock() != DENY`.
3. **Contents on link.** The container's existing contents merge into the group inventory, and any overflow drops at the block (as today).
4. **Double chests.** These are prevented at `BlockPlaceEvent` time by forcing `Chest.Type.SINGLE` on both sides when either is linked. This replaces the delayed `ChestLinkVerifier`.
5. **Unlink.**
   - Break the block (or explode it, or a piston/entity changes it) → node removed.
   - When the last node of a group is removed, the inventory drops at that location and the group is deleted. This keeps today's behaviour.
   - `/cl remove <group>` or the dialog removes the whole group, dropping items at the player.
6. **Silk touch.** Breaking a linked block with Silk Touch drops a "linked" item carrying PDC (`group id`, `type`) plus an item name and lore set via the data components API. Placing it re-links facing the placer, so no sign is needed. The group stays alive while it has zero nodes and a pending item, which preserves today's semantics.
7. **Pistons, explosions, burning and entity block changes** are cancelled or handled for linked nodes via an index lookup only.

### 5.3 Displays (`ItemDisplay` / `TextDisplay`)
- **One `ItemDisplay` per node, `ItemDisplayTransform.FIXED`.**
  - The client renders blocks as cubes and items flat, so all of `MaterialChecker`, the pose tables and the three armor-stand variants are deleted.
  - Uses `setBrightness(15,15)` (replaces the fire-ticks lighting hack), a small scale, and an explicit view range.
  - Positioned on the node's front face via `DisplayLayout`, with offsets for chest, barrel and crafting table.
- **`TextDisplay` label** (group name, optional) under the item, facing out of the block.
- **Lifecycle:**
  - Entities are `setPersistent(false)`, which prevents leftovers after a crash. They are spawned on `ChunkLoadEvent` (from the chunk index) and for loaded chunks at enable.
  - They disappear naturally with chunk unload, so there are **no per-player packets and no repeating tasks**.
- **Updates:** a group's "display item" (most common item) is recomputed through a **debounced queue** (≈8 ticks, as now), only when the inventory is dirty and at least one node is in a loaded chunk. The entity is touched only when the normalised item actually changes.
- **AutoCraft:** shows the recipe result.
- **Config:** displays and labels can be toggled per type. Toggling at runtime despawns or spawns them.

### 5.4 Hopper integration (`HopperInventorySearchEvent`)
This Bukkit/Paper event fires when a hopper looks up its source (above) or destination (facing) container, and lets us **substitute the inventory**. `HopperBridge` does an O(1) index lookup on `getSearchBlock()`. If that block is a linked node, it calls `setInventory(group.inventory())`.

Effects:
- **Hopper → linked chest** and **linked chest → hopper** become *vanilla* transfers. Vanilla rate, `hopper-amount`, Paper hopper optimisations, `InventoryMoveItemEvent` and filters all apply naturally.
- **Deleted:** `VirtualChestToHopper` (the per-group 8-tick polling), the cancel/reschedule/`clear()` hack in `LinkedChestHopperListener`, `SpigotConfig`/`WorldSettings` (`hopper-amount` reading) and `InventoryAccess` reflection.
- **Physical containers stay empty**, as today.
- **Droppers, crafters and hopper minecarts** don't fire the search event. A narrow `InventoryMoveItemEvent` redirect handles dropper/crafter sources, and hopper minecarts are out of scope (documented).
- **Spike S1 (done, §13):** both directions work on 26.3 with a custom-holder inventory, including full destinations, stacked hoppers and side-facing hoppers into barrels. Two consequences:
  - **Hot path:** idle hoppers fire the search event every tick (~20/s each), so `HopperBridge` must stay a single hash lookup.
  - **Move event:** with Paper's `hopper.disable-move-event: true`, transfers still work but `InventoryMoveItemEvent` never fires. Dirty tracking therefore can't rely on the move event alone (see §4.3), and hopper filters can't work in that mode. We log a startup warning and document it.

### 5.5 Hopper filters (new UI)
- **Open:** sneak + right-click a hopper with an empty hand (permission `chestsplusplus.filter`, plus the player must pass the same protection interact check).
- **Editor:** a custom 2×9 inventory.
  - **Row 1: Allow, row 2: Deny.** Clicking with an item on the cursor places a *ghost copy* (amount 1); the item is not consumed.
  - **Matching:** clicking an existing entry cycles its match: *Exact* (`isSimilar`) → *Same type* (material) → *Similar group* (shared item tag group, e.g. all logs, wool, seeds).
  - **Removing:** shift-click removes an entry.
  - **Clear button:** clears all entries.
- **Semantics (same as v2):** any Deny match rejects. If any Allow entries exist, an item must match at least one; with no entries, everything is allowed.
- **Storage:** stored in the hopper's PDC as a list of `(item bytes, mode, match)`, versioned.
- **Cache:** `FilterIndex` caches *compiled* filters per hopper position. It is loaded on chunk load and on edit, and dropped on chunk unload and hopper break. The hot path is an index lookup plus predicate tests:
  - The material check is a `BitSet`/`EnumSet`, and tag-groups are precomputed once from Paper item tags.
  - There is no `getNearbyEntities` and no `getState()`.
- **Displays:**
  - **Revised after in-game feedback:** every entry is shown on all four sides as a small inventory-style icon (`GUI` transform, flattened onto the face), in a grid on the hopper bowl read from the top-left. The Allow row starts with a green pane and the Deny row with a red pane; only rows with entries are shown, Allow first. There is no glow outline.
  - An action-bar "hover" readout was tried and removed: it needed a ray trace per player every few ticks for little benefit.
  - Non-persistent, spawned on chunk load.
- **Stall avoidance:** v2 manually moves the next acceptable item when the first slot is rejected. That behaviour is kept, but implemented with a single slot scan and no event re-entry. **Spike S1b** should check whether Paper still stalls on the first slot in 26.3; if not, this code is dropped.
- **Hopper break:** filters are copied onto the dropped hopper item (PDC) so they survive pickup. This is a nice-to-have and can ship later.

### 5.6 ChestLink inventory & interaction
- **Holder and title:** `ChestLinkHolder implements InventoryHolder` (Paper's recommended identification pattern), created with `Bukkit.createInventory(holder, 54, Component title)`.
- **Open:** right-clicking a node opens the shared inventory if `AccessService` allows. Lid animation uses `Lidded#open()/close()` on non-snapshot states (`getState(false)`), and the "animate all nodes" setting is kept.
- **Remote open:** `/cl open <group>` or the menus, with sound. Closing returns you to the menu you came from, tracked per viewer rather than per holder.
- **Sorting:**
  - Modes are `OFF`, `NAME`, `AMOUNT_ASC` and `AMOUNT_DESC`.
  - The algorithm is O(n log n): group by an `isSimilar` key, then condense and order.
  - **Behaviour change:** sort on close and on open, plus an explicit "Sort now" action, instead of one tick after every click (avoids items jumping under the cursor).
- **Limits:** limits are permission based, `chestsplusplus.limit.chestlink.<n>` and `chestsplusplus.limit.autocraft.<n>`, falling back to a config default. Values are resolved on demand.
- **World blacklist:** blocks creating, linking and opening in listed worlds.

### 5.7 Menus: Dialog hub + icon grid
**Dialog hub** (`/cl menu`, or `/cl` with no args):
- A `multi_action` dialog with a `text` input for **search**.
- **Buttons:** "Search", "Browse grid", "Trusted players", then one button per matching group (`name · owner · 1,234 items`, with a tooltip listing members and public status), with paging buttons.
- **Callbacks:** `DialogAction.customClick(callback, options)` with single use and an expiry, reading inputs via `DialogResponseView`.

**Group dialog:**
- An `item` body (the display item and counts).
- **Actions:**
  - Open (remote) and Sort (`single_option` input).
  - Rename (`text` input) and Public (`boolean` input).
  - Members… and Remove group (confirmation dialog).
- Every action re-checks permissions server-side.

**Icon grid** (`ui/menu`):
- A ~200-line in-house menu framework (`MenuHolder`, `Button`, pagination) that replaces SmartInvs.
- Icons are the group's display item. Left-click opens the inventory; right-click opens the group dialog.

**AutoCraft:** gets the same hub (filtered to AutoCraft groups). Opening a group opens the recipe editor.

### 5.8 Sharing: trust + members
- **Access rule:** `canAccess(player, group)` = owner, *or* public, *or* member of the group, *or* trusted by the owner, *or* holds `chestsplusplus.admin.bypass`.
- **Trust list:** `/cpp trust add|remove|list <player>` and the "Trusted players" dialog. Trust applies to all of the owner's ChestLinks and AutoCrafters.
- **Per-group members:** `/cl members add|remove|list <group> <player>` and the Members dialog.
- **Player resolution:** uses `Bukkit.getOfflinePlayerIfCached(name)` (never a blocking lookup on main). Unknown names fall back to an async profile lookup (`PlayerProfile#complete`) and apply the change back on main.

### 5.9 AutoCraft (rebuilt)
- **Recipe editor:** a custom `WORKBENCH` inventory (`RecipeEditorHolder`) where clicks place ghost copies.
  - **On change:** resolve once with `Bukkit.getCraftingRecipe(matrix, world)`, store the recipe key and matrix, play a chime, and update the display.
  - **Animation:** the choice-cycling animation for tag/material choices is kept, but runs only while someone is viewing.
- **Crafting ticker:** **one** central ticker (every 20 ticks) iterates *active* nodes, meaning those in loaded chunks with a valid output:
  - **Hopper below:** craft unless the hopper is powered.
  - **Container below:** craft only while the table is powered.
- **`CraftPlanner`** (pure and testable) builds an extraction plan from the input inventories (top and 4 sides, deduplicated, including ChestLink inventories the owner can access):
  - It counts candidates per slot `RecipeChoice` **without copying inventories**.
  - It computes the result and remaining items with `Bukkit.craftItemResult(plannedMatrix, world)`.
  - It checks output capacity by scanning output slots.
  - It then commits the plan atomically.
- **Backoff:** a node that fails to craft backs off (1 → 2 → 4 … up to 10 s). The backoff resets on an `InventoryMoveItemEvent` into an adjacent input or on a recipe change. Idle crafters cost close to nothing.
- **Block reads:** non-snapshot (`getState(false)`) only.

### 5.10 Commands (Brigadier)
Registered in the bootstrapper via `LifecycleEvents.COMMANDS`, with typed arguments, permission `requires(...)` and server-side suggestions.

```
/chestlink (/cl)    add <group> | remove <group> | open <group> | menu | list
                    | rename <group> <new> | public <group> <bool> | sort <group> <mode>
                    | members <add|remove|list> <group> [player]
/autocraft (/ac)    add <group> | remove <group> | open <group> | menu | list
                    | rename <group> <new> | public <group> <bool>
                    | members <add|remove|list> <group> [player]
/chestsplusplus (/cpp, /c++)  trust <add|remove|list> [player] | reload | version
```
- `GroupArgument` suggests your own groups, plus `owner:group` for groups you can access.
- **Help** comes from Brigadier usage plus a generated `/cpp help` page; there are no hand-written help enums.

### 5.11 Messages (i18n-ready, English only)
- `Messages` is a catalogue of keys mapped to MiniMessage templates. The English defaults ship in a bundled `messages.yml`, rendered with typed placeholders (`Placeholder.component/unparsed`).
- **Text formats:** all output is Adventure `Component`s. There are no `ChatColor`s or legacy `§` strings.
- **Future localisation:** load additional `messages_<locale>.yml` files into Adventure's `MiniMessageTranslationStore` and register it with `GlobalTranslator`, so text renders in each client's locale. Nothing in call sites needs to change.

### 5.12 Configuration
`config.yml` (fresh v3 layout) maps onto an immutable `Settings` record tree:
```yaml
features: { chestlinks: true, autocraft: true, hopper-filters: true }
chestlink: { animate-all-nodes: true, display: { enabled: true, label: true, view-range: 0.5 } }
autocraft: { display: { enabled: true, label: true } , tick-interval: 20 }
filters:   { displays: true }
limits:    { chestlink-default: -1, autocraft-default: -1 }   # -1 = unlimited; overridden by permissions
worlds:    { blacklist: [] }
storage:   { flush-interval-seconds: 30 }
update-checker: { enabled: true, notify-permission: chestsplusplus.admin.update }
metrics:   { enabled: true }
```
It is loaded with the stable Bukkit `YamlConfiguration` API. Paper's bundled Configurate is avoided because it is not a guaranteed API.

### 5.13 Permissions (`chestsplusplus.*`)
| Node | Default |
|---|---|
| `chestsplusplus.chestlink.{create,open,remote,menu,remove,sort,members}` | true |
| `chestsplusplus.autocraft.{create,open,remote,menu,remove,members}` | true |
| `chestsplusplus.filter` | true |
| `chestsplusplus.trust` | true |
| `chestsplusplus.limit.chestlink.<n>` / `.autocraft.<n>` | — |
| `chestsplusplus.admin.bypass`, `.admin.reload`, `.admin.update`, `.admin.version` | op |

---

## 6. Spigot → Paper API migration map

| v2 (Spigot-era) | v3 (Paper) |
|---|---|
| `plugin.yml` + `JavaPlugin` | `paper-plugin.yml` + `PluginBootstrap` + `JavaPlugin` |
| `CommandExecutor`/`TabCompleter` + enums | Brigadier Commands API (`LifecycleEvents.COMMANDS`), custom argument types |
| `ChatColor` strings, `setDisplayName/setLore` | Adventure `Component` + MiniMessage. Data components (`DataComponentTypes.ITEM_NAME`, `LORE`) for items |
| ArmorStand displays + pose tables + NMS `MaterialChecker` | `ItemDisplay` (`FIXED`) / `TextDisplay`, `Display#setBrightness` |
| Fake-air sign packets + `PlayerChunkLoadEvent` reflection + packet cache | Removed (sign → display) |
| `getState()` snapshots, `InventoryAccess` reflection | `Block#getState(false)`, `Inventory#getHolder(false)` directly |
| `VirtualChestToHopper` polling, move-event cancel/reschedule | `HopperInventorySearchEvent#setInventory` |
| Item-frame filters + `getNearbyEntities` per move | Hopper PDC + `FilterIndex` cache + filter editor UI |
| Hard-coded tag lists (`ChestsPlusPlusTag.SEEDS`, `ItemTypeUtil`) | Paper item tags, precomputed into material groups |
| SmartInvs | Dialog API + in-house `MenuHolder` framework |
| AnvilGUI text input | Dialog `text` input |
| `performCommand("tellraw …")` | `ClickEvent.callback(...)` / dialog buttons |
| `ConfigurationSerializable` YAML of the whole dataset | SQLite + `ItemStack.serializeItemsAsBytes` |
| Persistent entities + startup entity sweep | `setPersistent(false)` |
| `spigot.yml` hopper-amount reading, `ServerType`, `VersionMatcher` | Not needed |
| Blocking `Bukkit.getOfflinePlayer(name)` | `getOfflinePlayerIfCached` + async `PlayerProfile#complete` |
| `getLastTwoTargetBlocks` face detection | `Player#getTargetBlockFace` / `rayTraceBlocks` |
| Delayed `ChestLinkVerifier` task | Set `Chest.Type.SINGLE` during `BlockPlaceEvent` |
| Spigot `UpdateChecker` (broadcast) | `java.net.http` against Modrinth/Hangar, notify permitted players on join |
| `/chests++ reload` → `onEnable()` | Settings/messages hot-swap only |

---

## 7. What gets deleted

- **Modules and libraries:** `ChestsPlusPlusAPI`, `ChestsPlusPlus_1_21_R1`, `BaseMaterialChecker`/`MaterialChecker`/`NMSProvider`/`NPCProvider`/`VersionMatcher`, SmartInvs, AnvilGUI, Lombok, commons-lang3/commons-math3.
- **Language and storage legacy:**
  - The `lang/` files, `LangFileProperties`, `LanguageFile`, the `exec-maven-plugin` step and `POEditorImport/`.
  - The legacy `chests.yml` converter, `MaterialSerializer`, `ConfigStorage` and all `ConfigurationSerializable` classes.
- **Display and sign code:** `SignVisibilityListener`/`SignVisibilityCache`, `EntityEventListener`, `TempListener` and `ChestLinkVerifier`.
- **Polling and compatibility helpers:** `VirtualChestToHopper`, `InventoryAccess`, `SpigotConfig`/`WorldSettings` and `ServerType`.
- **Party and unused crafting code:** the party system (`party/*`, invite menus, `AcceptDialogMenu`, `PlayerSelectorMenu`, `PartySelectorMenu`, `InvitesMenu`, `TextInputUI`), plus `CraftingInventoryImpl` and `UserShapedRecipe` (unused or superseded).
- **Repository clutter:** `.travis.yml`, `BuildTools/`, the checked-in `Server/` (replaced by run-paper), `*.iml`, the `*.log` files in `ChestsPlusPlus_Main/`, `release.properties`, `Dockerfile` (unless still used for something; please confirm) and the stale `.jpb/`.

Rough size: about 7,000 LOC of Java in v2. v3 is estimated at **~4,500 LOC** including the new features (filter UI, dialogs, SQLite), with considerably smaller classes.

---

## 8. Build & tooling

- **Gradle 9 (Kotlin DSL), single project,** with a version catalog (`gradle/libs.versions.toml`).
- **Plugins:**
  - `java` (toolchain 25) and `com.gradleup.shadow` (bStats only, relocated).
  - `xyz.jpenilla.run-paper` (`./gradlew runServer` against 26.3).
  - `xyz.jpenilla.resource-factory-paper-convention`, which generates `paper-plugin.yml` from the build script so the version and main class aren't duplicated.
  - `io.github.drownek.plugwright` (E2E, §10.3).
- **Dependencies:**
  - `compileOnly("io.papermc.paper:paper-api:26.3…")` from `repo.papermc.io`.
  - `implementation("org.bstats:bstats-bukkit")`.
  - Tests use JUnit 5, AssertJ, `mockbukkit-v26.x` and `sqlite-jdbc` (all test-only).
- **Tasks:**
  - `test` runs unit and MockBukkit tests (JUnit tags allow running them separately).
  - `plugwrightTest` runs E2E.
  - `testHarnessJar` builds the never-shipped `ChestsPlusPlus-TestHarness.jar` from `src/testHarness` (§10.3). It is consumed only by `runE2eServer`.
  - `verifyReleaseJar` asserts that the release jar contains no harness classes, commands or resources. It runs in `build` and in the release workflow.
  - `check` depends on `test` only, so local builds stay fast.
- **SQLite fallback:** if the bundled driver is unreachable from the Paper plugin classloader, or is removed in a future Paper version, declare it through a `PluginLoader` + `MavenLibraryResolver` using Paper's Maven Central mirror instead of shading ~13 MB (spike S2).
- **CI:** GitHub Actions on Java 25 running `./gradlew build` (tests + shadowJar), with artifact upload and tag-driven releases. Modrinth/Hangar publishing is optional later.
- **Code quality:** Spotless (palantir-java-format), `-Xlint:all,-classfile -Werror`, JSpecify `@NullMarked` packages, and Error Prone if it plays nicely with Java 25 (not evaluated in Phase 0).
  - `-classfile` is excluded because paper-api's JOML dependency (used by `Display#setTransformation`) triggers it on every use (S4).
- **Test framework version:** `mockbukkit-v26.2:4.116.1` depends on JUnit Jupiter **6.1.3**, so the build uses the JUnit 6 BOM. The Jupiter API is unchanged; "JUnit 5" elsewhere in this doc means Jupiter.

---

## 9. Performance strategy

**Budgets:**
- Zero repeating tasks per group or node; at most three central tickers.
- Hot-path event handlers do no world reads, entity scans or allocations beyond the transfer itself.

| Path | v2 | v3 |
|---|---|---|
| Hopper ↔ ChestLink | Per-group 8-tick task + cancel/reschedule | Vanilla transfer; listener = 1 hash lookup |
| Hopper filter check | Entity scan per move | Hash lookup + precompiled predicates |
| Displays | Armor stands + per-player sign packets every 5 ticks | Non-persistent displays, updated only on change |
| AutoCraft | Per-group task, inventory copies per attempt | One ticker, allocation-light planner, backoff when idle |
| Saving | Whole-world YAML, unsafe async | Incremental SQLite, amortised main-thread snapshotting |
| Sorting | O(n²) after every click | O(n log n) on open/close/explicit |

**Validation:**
- Repeat the existing spark methodology (`docs/performance-investigation.md`) on a v2-vs-v3 scenario world. The world contains 200 ChestLink groups × 5 nodes with looping hoppers, 100 filtered hoppers and 50 AutoCrafters, with players walking through.
- Compare MSPT p50/p95/max and plugin sample share.
- Add a small JMH-style harness for `CraftPlanner`, `Sorter` and filter matching.

---

## 10. Testing strategy

Testing has three layers. Each catches a different class of bug, and each runs in CI.

| Layer | Tool | Speed | What it covers |
|---|---|---|---|
| 1. Unit | JUnit 5 (+ AssertJ) | ms | Pure logic, no Bukkit |
| 2. Integration | **MockBukkit** + JUnit 5 | seconds | Plugin wiring against a mocked Paper server: listeners, services, commands, persistence round-trips |
| 3. End-to-end | **Plugwright** (TypeScript, Mineflayer bots, real Paper server) | minutes | Real gameplay through a real client connection: hopper transfers, GUIs, displays, crash/restart |

### 10.1 Unit tests (`src/test/java`, tag `unit`)
- `CraftPlanner`, `Sorter`, filter semantics, `ItemGrouping` and `AccessService`.
- `DisplayLayout` maths, `BlockPos` packing and `Settings` parsing.
- Repositories and migrations against an in-memory SQLite (`jdbc:sqlite::memory:`, `sqlite-jdbc` as a test dependency).
- **Design rule:** keep these classes free of server state so they stay unit-testable. Bukkit types appear only at their edges (e.g. `ItemStack` predicates are passed in).

### 10.2 MockBukkit integration tests (`src/test/java`, tag `integration`)
- **Dependency:** As of October 2026, Maven Central has `org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1` but **no `mockbukkit-v26.3`** (26.3 support is in progress in [MockBukkit#1655](https://github.com/MockBukkit/MockBukkit/pull/1655)). We follow the pattern BentoBox adopted ([BentoBox#3090](https://github.com/BentoBoxWorld/BentoBox/pull/3090)):
  - **Split versions:** compile `main` against `paper-api` 26.3, but put `paper-api` 26.2 + `mockbukkit-v26.2` on the test runtime classpath. Both live in the version catalog (`paperVersion`, `testPaperVersion`).
  - **Guard 26.3-only tests** with `@EnabledIf("api263Present")`, which checks for a 26.3-only class. They are skipped (and visible as skipped) until MockBukkit ships `v26.3`.
  - **Upgrade path:** bump `testPaperVersion` and the `mockbukkit-v26.3` coordinate together, and the skipped tests run automatically.
  - **Prefer 26.2 APIs:** where a 26.2 API exists, use it over a 26.3-only API so that as much as possible stays testable (spike S5 lists which 26.3-only APIs we actually need).
- **Fixture:** a shared `PluginTestBase` that does `MockBukkit.mock()` → `MockBukkit.load(ChestsPlusPlus.class)`, points the DB at a temp file, and calls `MockBukkit.unmock()` after each test.
- **S5 findings (§13):**
  - **Bootstrapper:** MockBukkit reads `paper-plugin.yml` but never runs the `PluginBootstrap`. Command tests therefore load a test-only `CommandHostPlugin`, which registers the same `Commands` tree from `onEnable` via the plugin lifecycle manager.
  - **Lazy `COMMANDS` event:** MockBukkit fires `COMMANDS` once, lazily, on the first `dispatchCommand`, so the host plugin must be loaded before any dispatch.
  - **Plugin class:** MockBukkit loads the plugin through a generated subclass, so `ChestsPlusPlus` can't be `final`.
- **Coverage:**
  - **Lifecycle:** enable/disable, reload doesn't double-register, and the final flush writes dirty groups.
  - **Linking:** `SignChangeEvent` → group created, sign removed, node indexed; limit, blacklist and permission denials; break/explode/piston handling; double-chest prevention.
  - **Access:** owner, member, trust, public and bypass for open/remote-open.
  - **Commands:** `server.executeConsole/ player.performCommand` for every Brigadier route, permission gating and argument errors. Command registration depends on how well MockBukkit supports the Paper lifecycle API; if it doesn't, the command tree builders are unit-tested directly (spike S5).
  - **Persistence:** write → restart (unmock/mock with the same DB file) → identical groups, nodes, inventories (`ItemStack#isSimilar` + amounts), recipes, trust.
  - **Filters:** PDC round-trip, `FilterIndex` invalidation, `InventoryMoveItemEvent` accept/reject.
  - **Holders and menus:** the ChestLink holder and menus (open inventory, click simulation via `PlayerMock#simulateInventoryClick`).
- **Not covered by MockBukkit** (left to E2E): vanilla hopper ticking with `HopperInventorySearchEvent`, display rendering, Dialog client behaviour and real chunk load/unload timing.

### 10.3 Plugwright end-to-end tests (`src/test/e2e`)
[Plugwright](https://plugwright.dev/quickstart) downloads and starts a real Paper server with the freshly built plugin. Mineflayer bots connect as players, and the tests are written in TypeScript (`test`, `expect`, `player.chat`, `player.gui`, `server.execute`).

- **Setup:** add `plugins { id("io.github.drownek.plugwright") version "3.0.0" }` and run `./gradlew plugwrightInit` once to scaffold `src/test/e2e`. The environment config, and why it uses `ExternalMode` rather than `LocalMode` for now, is in §10.3.1. Tests run with `./gradlew e2e`.
- **Scenarios:**
  - **Sign linking:** a bot places a sign on a chest and writes `[ChestLink]`/`test`. Expect the confirmation message, the sign removed, and `/cl list` contains `test`.
  - **Shared inventory:** two chests in a group. Insert via one and open the other; `player.gui({ title: /test/ })` contains the item.
  - **Hopper bridge:** hopper → linked chest → hopper chain moves N items. Assert the counts through `/cl list`, or the test harness's `/cpptest` over RCON, after `waitForStable`.
  - **Filters:** sneak-click a hopper, place an item in the Allow row, check that only that item passes.
  - **Grid menu:** `/cl menu` grid → `gui.locator(i => i.displayName.includes('test')).click()` opens the inventory.
  - **Access:** a second bot (`createPlayer()`) is denied, then allowed after `/cpp trust add`.
  - **AutoCraft:** torch recipe with chest above and hopper below; output arrives.
  - **Durability:** `server.execute('stop')`-style restart using the environment hooks; data persists and there are no orphan display entities. Count them with `server.execute('execute if entity @e[type=item_display]')`.
- **Test harness plugin (never shipped).** Tests need to inspect server-side state (group contents, node and display-entity counts, filter caches, dirty/flush state) without parsing chat. That capability is **physically absent from the release jar**, not hidden behind a startup flag, config key or permission:
  - **Separate source set and jar.** The inspection code lives in a Gradle source set, `src/testHarness/java`, which builds a separate Paper plugin, `ChestsPlusPlus-TestHarness.jar`, with its own `paper-plugin.yml` and its own root command `/cpptest`. The release `shadowJar` only packages `main`, so no harness class, command literal or descriptor entry exists in the shipped plugin. There is no `/cpp debug`, and no system property or config switch that enables anything.
  - **How it sees internals.** The harness declares a `server` dependency on `ChestsPlusPlus` with `join-classpath: true` (Paper plugin dependency feature), so it reads the plugin's existing internal services directly. `main` doesn't add any test hooks, `@VisibleForTesting` accessors or debug endpoints for it. If the harness needs something that isn't reachable, we restructure `main` (e.g. expose a read-only query on a service), not add a backdoor.
  - **Where it's installed.** Only the E2E server task (`runE2eServer`) installs the harness jar next to the plugin. Release artefacts, `runServer` (manual dev server) and CI release jobs never build or attach it.
  - **Release guard:** a `verifyReleaseJar` task, wired into `build` and the release workflow, fails if the release jar contains:
    - any class under the harness package,
    - the string `cpptest`,
    - any `*TestHarness*` resource,
    - or a `paper-plugin.yml` that declares anything beyond the expected main/bootstrapper.

    A unit test also asserts that the Brigadier tree built by `main` has no `debug`/`test` literals.
  - **Harness safety:** the harness's commands are console-only (`requires(source -> source.getSender() instanceof ConsoleCommandSender)`) and read-only, apart from explicit fixture commands such as `/cpptest reset`. Even on the test server, the bots can't use them; they're driven over RCON.

#### 10.3.1 Running 26.3 with bots that only speak 26.1
**The problem:** Plugwright drives bots with Mineflayer, which officially supports up to Minecraft **26.1**. 26.2/26.3 protocol data is still unmerged ([minecraft-data#1299](https://github.com/PrismarineJS/minecraft-data/issues/1299)). In `LocalMode`, the single `minecraftVersion` setting picks *both* the Paper server version and the bot protocol. Setting it to `26.3` therefore gives bots that can't connect, and Plugwright documents no way to override the bot version or the server jar there.

**The fix:** decouple them. The server runs 26.3 with protocol translation, and the bots speak 26.1:
- **ViaVersion + ViaBackwards 5.12+** support 26.3 servers ([ViaBackwards 5.12.0](https://modrinth.com/plugin/viabackwards/version/5.12.0), released 18 Sep 2026) and let older clients, including 26.1, join.
- Plugwright's **`ExternalMode`** takes an explicit `minecraftVersion`, which in that mode is the *bot protocol version*. Plugwright's docs call out that a ViaVersion proxy defeats protocol autodetection, so the version must be set explicitly. Server commands go over RCON.

**Plan (staged):**

1. **Stage A (Phase 0, quickest path).** We own the server lifecycle and use `ExternalMode`:
   - A dedicated run-paper task, `runE2eServer`, runs Paper **26.3** with the freshly built plugin.
   - It adds `downloadPlugins { modrinth("viaversion", …); modrinth("viabackwards", …) }`.
   - It applies a fixture `server.properties` (offline mode, RCON on, fixed seed/flat world, low view distance) and installs the separately built `ChestsPlusPlus-TestHarness.jar` (see the test harness plugin above). No JVM flags or config switches change the plugin's behaviour.
   - An `e2e` Gradle task starts that server in the background, waits for `Done (`, runs `plugwrightTest`, then stops the server via RCON (`finalizedBy`). The same task works locally and in CI.
   ```kotlin
   plugwright {
       testsDir.set(file("src/test/e2e"))
       downloadNode.set(true)
       primaryEnvironment.set("paper263")
       environments {
           create("paper263", ExternalMode) {
               host.set("127.0.0.1"); port.set(25565)
               minecraftVersion.set("26.1")          // bot protocol; Via translates to the 26.3 server
               console { rcon { port.set(25575); password.set(secret.env("E2E_RCON_PASSWORD")) } }
           }
       }
   }
   ```
2. **Stage B (once Stage A is proven).** Package the same thing as a Plugwright **custom mode** ("PaperViaMode"):
   - The Kotlin half's `registerTasks`/`prepareTask` starts the run-paper server and stops it afterwards.
   - The JS half's `connection()` returns version `26.1`.
   - This gets back the "one command, Plugwright owns the lifecycle" experience and full server-log access instead of RCON-only responses.
   - It's also worth offering upstream as a `botVersion` option on `LocalMode`, since every 26.2/26.3 plugin has this problem.
3. **Stage C (when Mineflayer supports 26.3).** Switch to plain `LocalMode` with `minecraftVersion = "26.3"` and remove ViaVersion/ViaBackwards and the custom orchestration. Leave a Via-bridged environment in the matrix only if we want an "old client" compatibility check.

**What this means for writing tests:**
- **Bots see 26.1 registries.** Test fixtures use long-standing vanilla items and blocks (chests, hoppers, stone, logs, torches), never anything added in 26.2/26.3.
- **Via is in the loop.** If a test fails, we first check it isn't a Via translation artefact. Every scenario is also run once by hand on a native 26.3 client during the phase it's introduced.
- **Prefer RCON for server-state assertions** (Stage A): `server.execute('cpptest group test')` etc. The harness responds to the console sender with plain, parseable lines.
- **No dialogs:** Plugwright's docs don't mention 1.21.6+ dialog screens, and Mineflayer can't interact with them. Dialog flows are covered by unit-testing the dialog builders/handlers, MockBukkit tests that invoke the callback handlers directly, and the manual checklist. Every dialog action also has a command equivalent, so E2E exercises the same service code through commands.

If Stage A proves unworkable in spike S6 (Via mistranslating packets we depend on), E2E stays non-blocking (`allowFailure`) and the manual checklist carries those scenarios until Stage C.

**Stage A status after S6 (§13): partly blocked.** Join, chat, commands, RCON and the harness all work. However, any bot older than about 3 s, or one that looks, walks, jumps or sneaks, is kicked by the 26.3 server with "Invalid move player packet received" when bridged by ViaBackwards 5.12.0 or 5.12.1-SNAPSHOT. The same bot works against native 26.1.2 and against 26.2 + Via, so the fault is in the 26.2 ↔ 26.3 movement translation. The E2E job stays non-blocking, and gameplay scenarios wait for a decision on the options in §13 (S6).

### 10.4 Manual checklist (`docs/testing.md`)
This covers what automation can't:
- Dialog look and feel.
- Display placement and readability on every block face.
- Lid animations.
- Crash recovery with `kill -9`.
- Behaviour alongside protection plugins (WorldGuard/GriefPrevention).

### 10.5 CI
- **`build` job (every push and PR):**
  - `./gradlew build` runs unit and MockBukkit tests, then shadowJar.
  - Test reports are uploaded.
- **`e2e` job (PRs to `v3`/`master`, nightly):**
  - `./gradlew e2e` (Stage A: starts the Via-bridged 26.3 server, runs `plugwrightTest`, stops it) with cached Paper/plugin jars and Node. The RCON password comes from a CI secret.
  - Plugwright reports are uploaded as an artifact.
  - The job is non-blocking until spike S6 is resolved, and required after that.

---

## 11. Roadmap

Work happens on a long-lived `v3` branch; v2 stays on `master` for maintenance until release.

| Phase | Scope | Exit criteria |
|---|---|---|
| **0. Groundwork & spikes** | Gradle skeleton, `paper-plugin.yml`, bootstrapper, CI, run-paper. Test harnesses: JUnit, MockBukkit `PluginTestBase` with an "enables cleanly" test, Plugwright scaffold with a "bot joins, `/cpp version` replies" test. Spikes **S1–S6** (§12). | `runServer` boots an empty plugin; all three test layers run green in CI; spike findings recorded in this doc |
| **1. Core** | `BlockPos`, model, `GroupRegistry`/`NodeIndex`, `Settings`, `Messages`, tickers, SQLite + migrations + write-behind, `AccessService`/`TrustService` | Unit + MockBukkit persistence round-trip tests green; groups persist across restart incl. inventories |
| **2. ChestLinks** | Linking lifecycle (sign → display, command, silk touch), holder/open/animation/remote, `HopperBridge`, displays, sorting, limits, blacklist, protection checks | Feature parity with v2 ChestLinks; perf scenario runs without plugin hotspots |
| **3. Commands & UI** | Brigadier trees, `GroupArgument`, Dialog hub/group/members/trust dialogs, menu framework + icon grid | All commands/dialogs usable end-to-end, permission-gated |
| **4. Hopper filters** | PDC format, `FilterIndex`, editor GUI, filter displays, tag grouping | Filter semantics tests green; no entity scans in profile |
| **5. AutoCraft** | Recipe editor, `CraftPlanner`, central ticker + backoff, ChestLink inputs, displays | Torch/complex-recipe scenarios craft correctly; idle crafters near-zero cost |
| **6. Polish & release** | bStats, update checker, README/docs/screenshots, v2-vs-v3 spark comparison, `3.0.0` release | Release checklist done; E2E job required in CI |

From Phase 2 on, every phase's exit criteria include **MockBukkit tests for its listeners, services and commands, and Plugwright scenarios for its gameplay** (the scenario list is in §10.3). Tests are written alongside the feature, not after it.

---

## 12. Spikes, risks & open items

**Spikes (Phase 0):**
- **S1:** `HopperInventorySearchEvent#setInventory` with a custom `InventoryHolder` inventory, both `SOURCE` and `DESTINATION`, on 26.3. Check full destinations, Paper `hopper.disable-move-event`, and stacked hoppers. **S1b:** does the "first slot rejected → hopper stalls" behaviour still exist?
- **S2:** Can a Paper plugin classloader load `org.sqlite.JDBC` from the server's bundled libraries? If not, use `PluginLoader` + `MavenLibraryResolver`.
- **S3:** Dialog callbacks: which thread are they invoked on, how do use-count/lifetime behave, how is a dialog re-opened with updated content, and how many buttons fit before scrolling.
- **S4:** `ItemDisplay` transforms and offsets for chest/barrel/crafting-table faces and for hopper side faces. Check readability at distance and lighting.
- **S5:** MockBukkit on 26.x:
  - Is `mockbukkit-v26.3` out yet? If not, which of our APIs are 26.3-only and therefore need `@EnabledIf`-guarded tests?
  - Does it support `paper-plugin.yml`/`PluginBootstrap` loading and `LifecycleEvents.COMMANDS`?
  - Does it support `HopperInventorySearchEvent` dispatch, display entities, PDC on `TileState`, and `ItemStack.serializeItemsAsBytes`?

  Record any gaps and route those tests to unit or E2E instead.
- **S6:** Plugwright Stage A (§10.3.1):
  - Can Plugwright `ExternalMode` bots (`minecraftVersion = 26.1`) join a run-paper 26.3 server through ViaVersion + ViaBackwards 5.12+?
  - Do sign editing, block placement, sneak + right-click, container GUI open/click and chat/command feedback all survive translation?
  - Does `server.execute` over RCON return the test harness's `/cpptest` output?
  - Does `join-classpath: true` give the harness plugin access to `ChestsPlusPlus`'s internal classes on 26.3? If not, the harness reads state through the read-only query services `main` already uses for its own commands and dialogs (e.g. group lookup and contents), published via Paper's `ServicesManager`. Nothing debug-specific gets added to `main` either way.
  - Can the background start → wait-for-`Done` → test → RCON stop orchestration work on GitHub Actions runners (Java 25 toolchain)?

**Risks:**
- **Bundled driver removal:** Paper may stop bundling JDBC drivers. The S2 fallback covers this.
- **Dialogs are client UI:** layout limits (no per-button item icons) are why the grid menu exists alongside them.
- **Remaining world changes:** `HopperInventorySearchEvent` doesn't cover hopper minecarts or droppers (partially covered by the redirect), and comparators reading a linked chest see the empty physical container. Comparator output is out of scope for 3.0; it could be a later enhancement.
- **MockBukkit lag:** MockBukkit trails Paper releases (`v26.2` is published and `v26.3` is not yet). The compile-26.3 / test-26.2 split with guarded tests (§10.2) handles this. Keeping core logic Bukkit-free minimises reliance on it.
- **Plugwright bots:** these depend on Mineflayer protocol support (26.1 today) and can't drive Dialog screens. ViaVersion bridging is the mitigation, and dialogs are covered by handler-level tests plus the manual checklist.

**Defaults I've assumed (say if you disagree):**
1. Command linking (`/cl add`) doesn't consume a sign item. Sign linking consumes the placed sign, and it is not refunded on unlink.
2. Group names are case-insensitive and unique per owner and type, limited to 32 characters matching `[A-Za-z0-9_-]`.
3. The ChestLink inventory stays fixed at 54 slots.
4. Hopper filters are lost when the hopper is broken in 3.0.0. Carrying them on the dropped item is a follow-up.
5. bStats keeps plugin id **7166**.
6. `Dockerfile` is removed (it currently exists for the dev server; confirm it isn't used elsewhere).

---

## 13. Spike results (Phase 0, 3 October 2026)

Probes live on the `v3-spikes` branch (the `src/spikes` plugin `ChestsPlusPlus-Spikes`, `runSpikeServer`, `S5ProbeTest` and `spike-*.spec.ts`); none of it is on `v3`. Unless stated otherwise, everything ran against **Paper 26.3-146** (`a9d0382`) on JDK 25.0.2, Windows 11.

### S1: `HopperInventorySearchEvent` with a custom inventory: **works**
The probe substituted a `SpikeHolder` 54-slot inventory for chest/barrel nodes and ran all cases in force-loaded chunks for about 12 s.

| Case | Result |
|---|---|
| A: hopper above a linked chest (`DESTINATION`) | 10/10 items in the virtual inventory; physical chest empty |
| B: linked chest above a hopper (`SOURCE`) → plain sink chest | 10/10 items in the sink; virtual inventory drained |
| C: full virtual destination | Items stay in the hopper; no errors or log spam |
| D: two stacked hoppers → linked chest | 10/10 delivered |
| E: side-facing hopper → linked **barrel** | 10/10 delivered |
| `InventoryMoveItemEvent` with the custom holder | Fires for both directions (`toCustom=30`, `fromCustom=10`), so MONITOR-based dirty marking works |
| `hopper.disable-move-event: true` | Search event and all transfers still work; **`InventoryMoveItemEvent` never fires** |

Notes and design impact:
- **Event rate:** `SOURCE` searches fire every tick for idle hoppers (≈1,000 events in 12 s from 7 hoppers). Keep the listener to one hash lookup (§5.4), with no allocation.
- **Dirty tracking:** it can't rely only on the move event (§4.3 updated). Filters can't work with `disable-move-event: true`, so we warn at startup.
- **No spawn chunks in 26.x:** nothing ticked until the area was force-loaded, because spawn chunks were removed in 1.21.9. That matches vanilla, but E2E fixtures must `forceload` the test area or keep a bot nearby.

### S1b: first slot rejected → hopper stalls: **still stalls on 26.3**
A hopper held `[DIRT×5, STONE×5]` and a LOWEST listener cancelled dirt. Stone never moved, and the move was retried and cancelled every cooldown (31 times in 12 s). **The stall-avoidance code in §5.5 stays.** With `disable-move-event: true` both stacks moved, as expected since there is no event to cancel.

### S2: bundled `sqlite-jdbc` from a Paper plugin: **works, no fallback needed**
- **Driver:** `Class.forName("org.sqlite.JDBC")` from a `paper-plugin.yml` plugin loads the server's `libraries/org/xerial/sqlite-jdbc/3.49.1.0` jar (server library `URLClassLoader`).
- **SQLite:** `jdbc:sqlite:<file>`, `PRAGMA journal_mode=WAL` → `wal`, and `sqlite_version()` = 3.49.1.
- **Item blobs:** a `serializeItemsAsBytes` round trip through a BLOB column works (3 slots → 173 bytes).
- **Nulls:** null slots come back as `AIR x0`, so `InventoryRepo` must normalise empty stacks to `null` on load.

The `PluginLoader` + `MavenLibraryResolver` fallback (§8) stays documented but unused.

### S3: Dialogs: **pending in-game check**
`/spike dialog [n]` (on `runSpikeServer`) opens a `multi_action` dialog with:
- a `search` text input;
- the buttons Refresh (unlimited uses), Single use (`uses=1`), Expires in 10 s (`lifetime=10s`), Reopen next tick, and Toggle afterAction;
- `n` extra "group" buttons.

Each callback records its thread, `Bukkit.isPrimaryThread()` and the `search` value (see `/spike dialoglog`). A bot confirmed the dialog is built and sent without server errors. The behaviour itself needs a real 26.3 client (Mineflayer can't render dialogs). Results: _TBD_.

### S4: Display layout: **pending in-game check**
`/spike s4` builds a demo area next to the player:
- **Row A:** chest, barrel and crafting table facing N/E/S/W. Each has an `ItemDisplay` (FIXED, scale 0.5, brightness 15, view range 0.5) on the front face, 0.02 out (the chest face is inset by 1 px), plus a `TextDisplay` label underneath.
- **Row B:** the same with the item yaw flipped 180°.
- **Hopper row:** 4 side `ItemDisplay`s per hopper (scale 0.3) with lime/red glow overrides and a "+3" label.

A bot confirmed it builds without errors. Which row is right, readability at distance and at night, and the glow look need a real client. Results: _TBD_.

**Build finding:** using `Transformation`/JOML from plugin code triggers `-Xlint:classfile` warnings that `-Werror` turns into errors. The build now uses `-Xlint:all,-classfile` (§8).

### S5: MockBukkit on 26.x
| Question | Answer |
|---|---|
| `mockbukkit-v26.3` on Maven Central? | **No** (only `v26.1.2` and `v26.2`; latest `4.116.1`). The compile-26.3 / test-26.2 split is in place; `PluginTestBase#api263Present()` checks for `org.bukkit.inventory.BrewingRecipe` (26.3-only). |
| `paper-plugin.yml` loading | **Yes**: `MockBukkit.load(ChestsPlusPlus.class)` reads name/version/main from it. |
| `PluginBootstrap` | **Not run.** Commands are tested through `CommandHostPlugin` (§10.2). |
| `LifecycleEvents.COMMANDS` | **Yes, from `onEnable`.** It fires lazily on the first dispatch; root and aliases (`cpp`, `c++`) dispatch; `requires()` hides `version` from players without permission. |
| `HopperInventorySearchEvent` | Can be constructed and dispatched; `setInventory` works. MockBukkit never fires it itself (no hopper ticking), so the hopper bridge's real behaviour stays in E2E/manual. |
| Display entities | `world.spawn(..., ItemDisplay.class/TextDisplay.class)` works; item and persistence are kept. |
| PDC on `TileState` (hopper) | Works, including after `update()` and re-reading the state. `getState(false)` works. |
| `ItemStack.serializeItemsAsBytes` / `serializeAsBytes` | Round trip works. |
| Custom `InventoryHolder` inventories | Work (`getHolder()` returns the holder). |
| Dialog API | Classes are present; showing dialogs isn't meaningful in a mock. Dialog tests call the handler code directly, as already planned. |
| Plugin class | Must **not** be `final` (MockBukkit subclasses it). |

### S6: Plugwright Stage A (ExternalMode, 26.1 bots → Via → 26.3): **partly blocked**
**What works** (`./gradlew e2e`, locally on Windows):
- **Orchestration:** `startE2eServer` (fresh world, fixture properties, background JVM, wait for `Done (`) → `plugwrightTest` → `stopE2eServer` over RCON works, about 15 s end to end.
- **Join and commands:** bots join through ViaVersion/ViaBackwards 5.12.0, chat works, and `/cpp version` replies ("bot joins, `/cpp version` replies" passes).
- **RCON:** `server.execute` returns the harness's `/cpptest` output.
- **Harness:** `join-classpath: true` works on 26.3; the harness calls `JavaPlugin.getPlugin(ChestsPlusPlus.class)` directly. Ops can't run `/cpptest` (verified).
- **Console-only check:** RCON arrives as `RemoteConsoleCommandSender`, which is **not** a `ConsoleCommandSender`, so the harness accepts both.
- **Setup needed:**
  - ExternalMode needs an account pool: `autoRegister { usernamePattern = "pw_%s" }`, with a dummy password file because no auth plugin is used.
  - 26.x defaults `white-list=true`, so the fixture sets it to `false`.
- **Interactions:** block placement and opening a command-opened custom-holder GUI reached the server (`BlockPlaceEvent`, `InventoryOpenEvent`) when the bot hadn't moved yet.

**What's broken:**
- **The kick:** the server kicks every bot with `Invalid move player packet received` about 3–4 s after joining (when Mineflayer sends a movement heartbeat), or immediately on look, walk, jump or sneak. Sign editing, sneak-click and GUI clicks therefore can't be exercised reliably.
- **Where it comes from:**
  - The same bot against **native Paper 26.1.2**: no kicks.
  - Against **Paper 26.2 + Via**: look, walk and jump all fine.
  - ViaBackwards **5.12.1-SNAPSHOT+634** (with ViaVersion 5.12.1-SNAPSHOT+1069) behaves the same as 5.12.0.
  - So the fault is in the 26.2 ↔ 26.3 serverbound movement translation. 26.3 moved the position into `ACCEPT_TELEPORTATION`, and ViaBackwards rewrites the client's follow-up `MOVE_PLAYER_POS_ROT` into it. Every bot also logs "moved too quickly!" at spawn.
- **Upstream:** no upstream issue exists yet (searched ViaVersion/ViaBackwards/mineflayer, 3 Oct 2026).
- **Not yet verified:** the orchestration on GitHub Actions. The workflow exists (`.github/workflows/v3-ci.yml`) but hasn't run, because nothing has been pushed.

**Options (decision needed):**
1. **Wait (default):** keep the E2E job non-blocking, with only short smoke tests (join, command, RCON) in Stage A. Gameplay scenarios go on the manual checklist until ViaBackwards is fixed or Mineflayer supports 26.3 (Stage C). Optionally report the bug upstream with the repro above.
2. **Target 26.2 at runtime for E2E:** declare `api-version: 26.2`, avoid 26.3-only APIs (already preferred by §10.2), and run the E2E server on Paper 26.2 + Via, where bots work. This costs a runtime guard against accidental 26.3-only API use and a second server version to maintain.
3. **Custom mode with a newer protocol library:** only if Mineflayer or minecraft-data gains 26.3 data first (it currently has none for 26.2/26.3).

---

## 14. Implementation status (4 October 2026)

All six phases are implemented on `v3`. Every phase is verified with `./gradlew build` (unit + MockBukkit, 0
failures, only the 26.3-guarded test skipped) and `./gradlew e2e` (all scenarios green locally on Paper 26.3-146).

| Phase | Status | Notes |
|---|---|---|
| 0. Groundwork & spikes | Done | §13. S3/S4 still need the in-game check (below). |
| 1. Core | Done | `BlockPos`, `Settings`, `Messages`, model + indexes, access/trust, SQLite v1 + write-behind, restart round-trip tested. |
| 2. ChestLinks | Done | Sign/command/silk-touch linking, holder, lids, remote open, `HopperBridge` (E2E-verified with real hoppers), displays, sorting, limits, blacklist, protection check. |
| 3. Commands & UI | Done | All Brigadier routes, `GroupArgument`, Dialog hub/group/members/trust/confirm, menu framework + grid. |
| 4. Hopper filters | Done | PDC codec, `FilterIndex`, editor, side displays, tag grouping, stall avoidance (E2E-verified). |
| 5. AutoCraft | Done | Recipe editor, `CraftPlanner`, central ticker + backoff, ChestLink inputs (E2E-verified with real recipes). |
| 6. Polish & release | Partly done | bStats, update checker, README, `docs/testing.md`, CHANGELOG and a tag-driven draft-release workflow are done. The rest is listed below. |

### Deviations from the plan (and why)
- **Repositories (§3.2):** no repository classes. Each store (`GroupStore`, `TrustStore`) owns its tables, with
  `RecordTable` generating the routine SQL; see [persistence-stores-plan.md](persistence-stores-plan.md).
- **Recipe choice-cycling animation (§5.9):** not implemented. Ghost items are the concrete items the player placed;
  tag recipes still accept any matching item when crafting (per-slot recipe choices).
- **Update checker (§3.2, §5.12):** uses GitHub Releases, because no Modrinth or Hangar project exists.
- **Rename closes viewers:** inventory titles are fixed at creation, so renaming or reloading recreates the shared
  inventory and closes anyone viewing it.
- **Command `/cl add`:** uses the clicked face when it's horizontal (otherwise it faces the player) for the display.
- **Test seams:** AutoCraft takes a `CraftingBackend` (Bukkit in production, a fake in tests) because MockBukkit has no
  recipe matching. `Holders.of` falls back to `getHolder()` where `getHolder(false)` is unimplemented. Both are
  ordinary dependency/compatibility seams, not debug hooks; the release jar guard still passes.
- **MockBukkit gaps found:** `PluginBootstrap`, `Inventory#getHolder(boolean)`, `Entity#getFacing`,
  `TextDisplay#setBillboard`, redstone power, `Server#getWorldContainer`, and mutable `Block#getLocation`. A
  `FailOnUnimplemented` extension makes any gap a test failure instead of a silent skip; that change also exposed that
  some Phase 2 tests had been skipping before it.

### Open items before tagging 3.0.0
1. **S3/S4 in-game checks** (`docs/testing.md`). S4 decides `DisplayLayout.ITEM_YAW_OFFSET`; row A (0°) is assumed.
2. **S6 decision:** E2E stays non-blocking and limited to short bot sessions until ViaBackwards fixes 26.3 movement
   translation or Mineflayer supports 26.3 (§13, options 1-3). Reporting the bug upstream needs your approval.
3. **First CI run:** nothing has been pushed yet, so `.github/workflows/v3-ci.yml` (build + e2e) and
   `v3-release.yml` haven't run on GitHub Actions.
4. **v2-vs-v3 spark comparison (§9):** needs the scenario world and a manual spark session; not done.
5. **Manual checklist** in `docs/testing.md` on a native 26.3 client, including protection plugins and `kill -9`.
6. **Release:** bump nothing in the build script; push a `v3.0.0` tag to produce a draft GitHub release with the jar
   (needs your go-ahead).
