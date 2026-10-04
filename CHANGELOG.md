# Changelog

## 3.0.0 (unreleased)

A ground-up rewrite for **Paper 26.x** (Java 25). Not compatible with v2 data, commands, permissions or config.

### New
- **Sign → display:** linking signs turn into floating item and label displays; no more fake-air sign packets.
- **Hopper filter editor:** sneak + right-click a hopper to set allow/deny filters (exact item, same type, or
  similar items). Filters live in the hopper itself, shown as small items on every side; look at one to see its details.
- **Dialog menus:** a searchable hub with per-group dialogs (rename, public, sort mode, members, remove) and a trust
  list, plus an icon grid.
- **Trust lists and members** replace parties.
- **Silk Touch** moves linked blocks without unlinking them.
- **Brigadier commands** with suggestions, including `owner:group` for shared groups.

### Changed
- ChestLink hoppers are now vanilla transfers (`HopperInventorySearchEvent`), so speed and hopper settings match a
  normal chest. There are no per-group tasks.
- Data is stored incrementally in SQLite (`data.db`), with crash-safe write-behind saving.
- AutoCraft runs on one central ticker with backoff for idle crafters; inputs can be ChestLinks.
- Sorting happens on open/close and on demand instead of after every click.
- Permissions moved to `chestsplusplus.*`; limits are `chestsplusplus.limit.<type>.<n>`.
- Update notices go only to players with `chestsplusplus.admin.update`, when they join.

### Removed
- Item-frame hopper filters, parties, language files (English only; i18n-ready), and Spigot/NMS support.
- The plugin no longer keeps anything in the world except hopper filters and its linked blocks.
