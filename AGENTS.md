# AGENTS.md

Guidance for anyone (human or agent) changing ChestsPlusPlus. The aim is code that reads well at a glance: short methods, few
comments, no repeated logic.

## Build and test

- `./gradlew spotlessApply` formats the code. Run it before committing: CI runs `./gradlew build`, which fails on unformatted code.
- `./gradlew unitTest integrationTest` runs the JVM tests. See [docs/testing.md](docs/testing.md) for E2E and the manual checklist.
- The compiler runs with `-Xlint:all -Werror`, so any warning fails the build.

## Formatting

- The Eclipse formatter ([config/eclipse-formatter.xml](config/eclipse-formatter.xml)) wraps at 150 columns and keeps line breaks
  you write yourself.
- Keep ordinary calls on one line up to 150 columns. Don't put one argument per line.
- Write builder chains one call per line: Brigadier trees, dialog builders, HTTP requests, longer streams.
- Put field annotations on the field's line: `@Getter private final NodeIndex nodes = new NodeIndex();`.

## Comments

- Comment the *why*, not the *what*. If a comment restates the code, delete it.
- Keep comments that record a non-obvious constraint: Paper or MockBukkit quirks, thread rules, hot paths, why a check runs last.
- Prefer a one-line Javadoc. Skip Javadoc where the name already says it all.
- Don't add section-divider comments (`// ---- Helpers ----`), references to design-doc sections or spikes, or notes about how v2
  did things.

## Methods and classes

- If a method needs comments to separate its parts, split it into named methods. `ChestsPlusPlus.onEnable` is the model: one call
  per lifecycle step.
- For a run of guard clauses, have a helper return the refusal and send it once, instead of repeating send-and-return. Examples:
  `LinkService.linkRefusal`, `NodeListener.relinkRefusal`, `LinkService.openRefusal`.
- Use modern Java: pattern-matching `switch` over sealed types, `_` for unused pattern variables, records for data, streams for small
  collection queries, early returns.
- Use imports, not fully qualified names. The one exception is Paper's `io.papermc.paper.command.brigadier.Commands`, which clashes
  with our own `Commands` class.
- Name string keys that are defined in one place and read in another, like dialog input keys (`UiService.NAME_INPUT`).
- Don't take nullable callbacks. Accept a `Runnable` and have callers pass `() -> {}`.
- When a class starts doing several jobs, move the extra jobs out (as was done with `PlayerNames`, `DoubleChests` and
  `AccessService.accessibleGroups`).

## Dependencies

- Pass collaborators in through the constructor; don't call `services.get(...)` mid-method. The command classes are the only
  exception: they're built at bootstrap, before any services exist, so they look services up when a command runs.
- Every user-facing action goes through `GroupActions`, so a dialog button can never do more than the equivalent command.
- Keep packages acyclic: `chestlink` and `autocraft` depend on `link`, never the other way round.

## Lombok

- **Logging:** `@Slf4j(topic = ChestsPlusPlus.NAME)`. That is the same logger as Paper's `getSLF4JLogger()`, so console output keeps
  the plugin's name. Don't inject `Logger`s or call `getSLF4JLogger()`.
- **Constructors:** `@RequiredArgsConstructor` when a constructor only assigns fields. The generated parameters follow field
  declaration order, so check that callers' argument order still matches. Two fields of the same type swapped silently is the risk.
- **Getters:** `@Getter` on the field for a trivial accessor. `lombok.config` sets fluent accessors, so you get `group()`, not
  `getGroup()`.
- **Setters:** write them by hand. Fluent setters would turn `setPublic(boolean)` into `isPublic(boolean)`.
- **Don't use:** `@UtilityClass`, `@Data`, `@Builder`. Records cover data classes.

## Reuse these instead of rewriting them

| Need | Use |
|---|---|
| Choose a value per group type | `type.pick(chestlinkValue, autocraftValue)`, `type.displayName()` |
| Send a message | `services.send(audience, Message.X, ...)` |
| Common placeholders | `Messages.group(group)`, `Messages.player(name)`, `Messages.text(key, int)` |
| Node or group at a block (no allocation) | `services.nodes().at(block)`, `services.groupAt(block)` |
| Player names and group references | `PlayerNames.of(uuid)`, `PlayerNames.join(uuids)`, `group.referenceFor(requester)` |
| Groups a player can use | `services.access().accessibleGroups(...)` |
| Right-click on a linked block | `LinkService.claimNodeClick(event, type)` |
| Repeating work | `Tickers.every` / `Tickers.everyInterval`. Never one task per group or node. |
| Ghost-item inventories | extend `GhostEditor`; `MenuListener` enforces the no-real-items rules |
| Clickable chest menus | `Menu` / `PaginatedMenu` |
| Save a model change | `services.groupStore().markDirty(group)` (or `trustStore().markDirty(owner)`); the next flush saves the whole group |
| SQL | a `RecordTable` in the store that owns the table; anything it can't do is a plain JDBI call in that store. Schema: edit `V1.sql` until v3 ships (then clear your dev database), afterwards a new `db/migration/V<n>.sql` with `Database.SCHEMA_VERSION` bumped |
| Persist a new kind of thing | a row record, its `CREATE TABLE`, a `Store` of about 15 lines using `RecordTable`, `persistence.register(store)` in `ChestsPlusPlus`, and `markDirty` wherever the data changes |
| Command tree pieces | `Commands.literal`, `argument`, `permission`, `playerArgument` |

## Runtime libraries

- Add a library Paper should download at startup (rather than shading it) as `paperLibrary(...)` in `build.gradle.kts`.
  `ChestsPlusPlusLoader` resolves everything in that configuration from Paper's Maven Central mirror.

## Runtime rules

- The model (groups, nodes, trust) is main-thread only. Only `Persistence`'s I/O thread touches the database, apart from loading at
  startup. A store's `snapshot` runs on the main thread and must copy what it needs: clone `ItemStack`s, since `getContents()` returns
  live mirrors and items are serialised on the I/O thread.
- Hot paths run every tick per hopper, so keep them to a few hash lookups with no allocation. These are
  `HopperBridge.onSearch` and `FilterListener.onMove`.
