CREATE TABLE groups (
  id          INTEGER PRIMARY KEY,
  type        TEXT    NOT NULL,
  owner       BLOB    NOT NULL,
  name        TEXT    NOT NULL COLLATE NOCASE,
  is_public   INTEGER NOT NULL DEFAULT 0,
  sort_mode   TEXT,
  created_at  INTEGER NOT NULL,
  items       BLOB,
  recipe_key  TEXT,
  matches     TEXT,
  v2_source   TEXT
);

CREATE TABLE group_members (
  group_id INTEGER NOT NULL REFERENCES groups ON DELETE CASCADE,
  member   BLOB    NOT NULL,
  PRIMARY KEY (group_id, member)
);

CREATE TABLE nodes (
  world    BLOB    NOT NULL,
  x        INTEGER NOT NULL,
  y        INTEGER NOT NULL,
  z        INTEGER NOT NULL,
  group_id INTEGER NOT NULL REFERENCES groups ON DELETE CASCADE,
  facing   TEXT    NOT NULL,
  PRIMARY KEY (world, x, y, z)
);

CREATE INDEX nodes_group ON nodes(group_id);

CREATE TABLE trust (
  owner   BLOB NOT NULL,
  trusted BLOB NOT NULL,
  PRIMARY KEY (owner, trusted)
);

CREATE TABLE v2_blocks (
  world   BLOB    NOT NULL,
  x       INTEGER NOT NULL,
  y       INTEGER NOT NULL,
  z       INTEGER NOT NULL,
  pending INTEGER NOT NULL,
  PRIMARY KEY (world, x, y, z)
);

CREATE TABLE migration_state (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
