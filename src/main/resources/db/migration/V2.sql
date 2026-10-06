CREATE TABLE v2_pending_locations (
  group_id  INTEGER NOT NULL REFERENCES groups ON DELETE CASCADE,
  world_name TEXT NOT NULL,
  x INTEGER NOT NULL,
  y INTEGER NOT NULL,
  z INTEGER NOT NULL,
  PRIMARY KEY (group_id, world_name, x, y, z)
);

-- Old development imports have no durable completion record or unresolved-location history.
INSERT OR IGNORE INTO migration_state(key, value)
SELECT 'v2_import', 'LEGACY' WHERE EXISTS (SELECT 1 FROM groups WHERE v2_source IS NOT NULL)
  OR EXISTS (SELECT 1 FROM v2_blocks)
  OR EXISTS (SELECT 1 FROM migration_state WHERE key = 'filters' AND value <> 'NONE');
