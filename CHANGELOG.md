# Changelog

## 3.0.0 (unreleased)

A ground-up rewrite for **Paper 26.x** (Java 25). v2 data and config are imported on the first start; commands, permissions and
language files start fresh.

### New
- **v2 import:** on its first start v3 imports v2's `data/storage.yml` (ChestLinks with their items, AutoCrafters, members,
  public flags, sort modes, and parties as trust) and converts `config.yml`, after backing the data folder up. Blocks are tidied
  up as their chunks load (v2 signs and armour stands removed, double chests split). `/cpp migrate v2` previews or repeats the
  import, and v2 hopper filters (item frames) are converted on request with `/cpp migrate v2 filters`.
- **Sign → display:** linking signs turn into floating item and label displays; no more fake-air sign packets.
- **Name tag linking:** right-click a chest, barrel or crafting table with a named name tag to link it to that group.
- **Hopper filter editor:** sneak + right-click a hopper to set allow/deny filters (exact item, same type, or
  similar items). Filters live in the hopper itself, shown as small icons on every side: a green-pane row of allowed items and a red-pane row of denied ones.
- **Dialog menus:** a searchable hub with per-group dialogs (rename, public, sort mode, members, remove) and a trust
  list, plus an icon grid.
- **Trust lists and members** replace parties.
- **Silk Touch** moves linked blocks without unlinking them.
- **Brigadier commands** with suggestions, including `owner:group` for shared groups.
- **AutoCraft match modes:** right-click a ghost item in the recipe editor to choose what that slot accepts: what the
  recipe allows (e.g. any planks), this exact item, or any item of its type (e.g. any damaged pickaxe for repairs).

### Changed
- ChestLink hoppers are now vanilla transfers (`HopperInventorySearchEvent`), so speed and hopper settings match a
  normal chest. There are no per-group tasks.
- Data is stored incrementally in SQLite (`data.db`), with crash-safe write-behind saving.
- `config.yml` gains settings added by later versions on start-up and `/cpp reload`, with their defaults and comments. The
  file is rewritten each time, so comments you add yourself are not kept.
- AutoCraft runs on one central ticker and crafts on the next tick after its recipe, inputs or output change, instead of waiting for
  a fixed sweep; idle crafters back off. Inputs can be ChestLinks.
- Sorting happens on open/close and on demand instead of after every click.
- Permissions moved to `chestsplusplus.*`; limits are `chestsplusplus.limit.<type>.<n>`.
- Updates are checked on Modrinth, and notices go only to players with `chestsplusplus.admin.update`, when they join. Beta builds
  are also told about newer betas.

### Removed
- Item-frame hopper filters, parties, language files (English only; i18n-ready), and Spigot/NMS support.
- The plugin no longer keeps anything in the world except hopper filters and its linked blocks.
