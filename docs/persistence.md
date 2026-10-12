# Persistence

The in-memory model is authoritative. After a model edit, call `store.markDirty(key)`. The next flush copies its current state on the
main thread and writes it on the single I/O thread. Repeated edits to a key need only one snapshot; a null snapshot deletes its rows.

Register stores in dependency order, then load them once during startup:

```java
TrustStore trustStore = persistence.register(new TrustStore(persistence, trust));
trust.onChange(trustStore::markDirty);
persistence.load();
```

## Choosing a store

- `Store<K, S>` defines snapshots, writes, deletes and loading.
- `TrackedStore<K, S>` adds `markDirty(key)` and `isDirty(key)`. Expose the constructor-injected `Persistence` with `@Getter`.
- `RowStore<K, R>` adds writes and deletes for a single `RecordTable<R>`. A store supplies its table, snapshot and loading code.
  Single-column keys are ordinary values; composite keys are records whose component names match the table's key columns in camelCase.

`MigrationStateStore` is the single-row example; `V2CleanupStore` uses a composite `BlockPos` key. `GroupStore` keeps explicit writes
because it owns several tables and must retire deleted IDs.

For child rows, let `RecordTable` collect the parent keys and flatten the snapshots:

```java
TRUST.replaceFor(handle, "owner", snapshots, TrustSnapshot::owner, TrustSnapshot::rows);
```

This deletes every affected parent's old rows before inserting any new ones. Empty child lists clear a parent's rows, and children can
move between parents in the same flush. SQL beyond these table operations stays in the store that owns it.

## Saving related changes

Use `together` when several edits must survive retries as one transaction:

```java
persistence.together(() -> {
    groupStore.markDirty(group);
    pending.remove(location);
});
```

It also accepts a `Supplier<T>` when the edit returns a value. Nested calls and units sharing a key are combined. This groups saves;
it does not roll back model changes if the callback throws. A failed callback leaves its edits dirty without creating a new unit.

## Threads and failures

`markDirty`, `together`, `load`, `flush` and `close` run on the main thread. Snapshots must own their data: clone mutable items before
handing them to the I/O thread. All writes and deletes run inside a transaction. Registration order is also write order.

`Persistence` owns scheduling and shutdown. `PendingChanges` owns dirty keys, related units and retry budgets. `WriteBatch` holds copied
snapshots, and `BatchWriter` runs transactions and isolates failed stores or keys without splitting related units.

After a failed flush, the next attempt snapshots current model state. A persistently failing key or related unit is given up on after
three isolated failures, until it changes again. Failures count only when another write commits, so a database outage does not exhaust
every key's retry budget. `flush()` completes on the main thread and fails if any part failed, even if independent writes committed.
During shutdown, `close()` drains queued completions itself and retries up to three rounds before logging unsaved changes.
