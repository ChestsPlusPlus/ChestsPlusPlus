# ChestsPlusPlus

<a href="https://www.buymeacoffee.com/jamespeters"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a beer&emoji=🍺&slug=jamespeters&button_colour=FF5F5F&font_colour=ffffff&font_family=Cookie&outline_colour=000000&coffee_colour=FFDD00"></a>

Paper plugin that enhances chests and hoppers, with ChestLinks, Auto-Crafting and Hopper filters.

> **This is the `v3` branch: a ground-up rewrite for Paper 26.x and Java 25.** v3 is a new major version and is
> **not** backwards compatible with v2 data, commands, permissions or configuration. The current stable plugin (v2)
> lives on the [`2.x`](../../tree/2.x) branch. The design is in [docs/v3-rewrite-plan.md](docs/v3-rewrite-plan.md).

## Features

- **ChestLinks**: link any number of chests and barrels so they share one 54-slot inventory. Open it from any linked
  block, or remotely with `/cl open <group>` or the menu. Hoppers move items in and out exactly as with a normal
  chest.
- **AutoCraft**: link crafting tables to a shared recipe. Place a container above or beside a table and a hopper
  below, and it crafts automatically. A container below crafts only while the table is powered.
- **Hopper filters**: sneak + right-click a hopper with an empty hand to choose which items it accepts (allow/deny,
  matching the exact item, the same type, or similar items such as all logs). Filters are stored in the hopper itself.
- **Sharing**: make a group public, add members to a group, or trust players with all of your groups.
- **Menus**: a searchable dialog hub (`/cl`, `/ac`) plus an icon grid.

## Getting started

- **Create a ChestLink:** place a wall sign on a chest or barrel and write `[ChestLink]` on the first line and a group
  name on the second (use `owner:group` to join someone else's group you have access to). The sign turns into a
  floating label. Or rename a name tag to the group name in an anvil and right-click the block with it (by default one
  tag is used up; see `linking.consume-name-tags`). Alternatively, look at the block and run `/cl add <group>`.
- **Create an AutoCrafter:** the same, with `[AutoCraft]` on a crafting table (or a named name tag). Right-click the table to set its recipe:
  click slots with items to place ghost copies (nothing is used up).
- **Move a linked block:** break it with a Silk Touch tool. The item you get re-links wherever you place it.

## Commands

| Command | Description |
|---|---|
| `/chestlink` (`/cl`) | Open the ChestLink hub |
| `/cl add <group>` | Link the block you're looking at (creates the group if it's new) |
| `/cl open <group>` | Open a group remotely |
| `/cl list` / `/cl menu` | List your groups / open the hub |
| `/cl remove <group>` | Remove a group (its items drop at your feet) |
| `/cl rename <group> <new>` · `/cl public <group> <true\|false>` · `/cl sort <group> <off\|name\|amount_asc\|amount_desc>` | Manage a group |
| `/cl members <add\|remove\|list> <group> [player]` | Per-group members |
| `/autocraft` (`/ac`) | The same for AutoCrafters (no `sort`) |
| `/chestsplusplus` (`/cpp`) `trust <add\|remove\|list> [player]` | Trust players with all your groups |
| `/cpp reload` · `/cpp version` · `/cpp help` | Admin and help |

`<group>` is `name` for your own groups, or `owner:name` for someone else's group you can access.

## Permissions

| Node | Default |
|---|---|
| `chestsplusplus.chestlink.{create,open,remote,menu,remove,sort,members}` | everyone |
| `chestsplusplus.autocraft.{create,open,remote,menu,remove,members}` | everyone |
| `chestsplusplus.filter`, `chestsplusplus.trust` | everyone |
| `chestsplusplus.limit.chestlink.<n>` / `chestsplusplus.limit.autocraft.<n>` | not set (falls back to `limits` in config) |
| `chestsplusplus.admin.{bypass,reload,update,version}` | op |

## Configuration

`plugins/ChestsPlusPlus/config.yml` controls features, displays, limits, the world blacklist, saving and the update
checker; see the comments in the file. Messages use [MiniMessage](https://docs.papermc.io/adventure/minimessage/format):
copy keys from the bundled `messages.yml` into `plugins/ChestsPlusPlus/messages.yml` to change them. Data is stored in
`plugins/ChestsPlusPlus/data.db` (SQLite).

Notes:
- Hopper filters rely on Paper's inventory move event. If a world sets `hopper.disable-move-event: true`, the plugin
  logs a warning and filters don't apply there (ChestLink hoppers still work).
- Hopper minecarts don't interact with ChestLinks, and comparators read the (empty) physical chest.

## Building and testing

Requires JDK 25 (the Gradle wrapper selects it through toolchains).

```bash
./gradlew build          # unit + MockBukkit tests, release jar (build/libs), release-jar guard
./gradlew runServer      # Paper 26.3 dev server with the plugin
./gradlew e2e -Pchestsplusplus.acceptMinecraftEula=true   # Plugwright E2E against a Via-bridged 26.3 server
```

The E2E server needs the [Minecraft EULA](https://aka.ms/MinecraftEULA) accepted (the property above, or
`ACCEPT_MINECRAFT_EULA=true`). See [docs/testing.md](docs/testing.md) for the manual checklist.
