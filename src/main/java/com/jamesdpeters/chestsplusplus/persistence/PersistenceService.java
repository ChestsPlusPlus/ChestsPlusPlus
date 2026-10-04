package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.LoadedData;
import com.jamesdpeters.chestsplusplus.persistence.Records.SaveBatch;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Write-behind persistence (plan §4.3). The in-memory model is authoritative; dirty groups are snapshotted on the main
 * thread, a bounded number per tick, and written by a single I/O thread. A dirty flag is cleared only once its write
 * has committed. {@link #close()} drains everything synchronously.
 */
public final class PersistenceService {

    private final Database database;
    private final Repository repository;
    private final GroupRegistry groups;
    private final NodeIndex nodes;
    private final TrustService trust;
    private final Logger logger;
    private final Consumer<Runnable> mainThread;
    private final IntSupplier maxPerTick;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ChestsPlusPlus-persistence-io");
        thread.setDaemon(true);
        return thread;
    });

    /** Group id → generation of its latest change. */
    private final Map<Long, Long> dirty = new LinkedHashMap<>();

    private final Set<Long> deleted = new LinkedHashSet<>();
    private final Map<UUID, Long> dirtyTrust = new LinkedHashMap<>();
    private final Set<Long> inFlight = new HashSet<>();
    private final Set<Long> hopperTouched = new HashSet<>();
    private final Map<Long, Integer> savedFingerprints = new HashMap<>();
    private long generation;
    private boolean flushing;
    private boolean closed;

    /**
     * @param mainThread runs a task on the server thread (the scheduler); used for I/O completion callbacks
     * @param maxPerTick current {@code storage.max-serialisations-per-tick}
     */
    public PersistenceService(Database database, GroupRegistry groups, NodeIndex nodes, TrustService trust, Logger logger,
            Consumer<Runnable> mainThread, IntSupplier maxPerTick) {
        this.database = database;
        this.repository = new Repository(database);
        this.groups = groups;
        this.nodes = nodes;
        this.trust = trust;
        this.logger = logger;
        this.mainThread = mainThread;
        this.maxPerTick = maxPerTick;
        trust.onChange(this::markTrustDirty);
    }

    /**
     * Reads every row on the I/O thread, then builds the model on the calling (main) thread. {@code attach} is called
     * for each group after it is registered and its nodes are indexed (e.g. to create ChestLink inventories).
     *
     * @return the number of groups loaded
     */
    public int load(Consumer<LoadedGroup> attach) throws SQLException {
        LoadedData data;
        try {
            data = CompletableFuture.supplyAsync(() -> {
                try {
                    return repository.loadAll();
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, io).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof SQLException sql) throw sql;
            throw e;
        }
        for (GroupRecord record : data.groups()) {
            StorageGroup group = ModelMapper.fromRecord(record);
            groups.add(group);
            record.members().forEach(member -> groups.addMember(group, member));
            ModelMapper.nodes(record).forEach(nodes::put);
            @Nullable ItemStack[] contents = record.inventory() == null ? null : ModelMapper.deserialize(record.inventory());
            attach.accept(new LoadedGroup(group, contents));
            if (group instanceof ChestLinkGroup chest && chest.hasInventory()) {
                savedFingerprints.put(group.id(), fingerprint(chest.inventory().getContents()));
            }
        }
        trust.load(data.trust());
        return data.groups().size();
    }

    /** A freshly loaded group and its stored ChestLink contents (null for AutoCraft groups). */
    public record LoadedGroup(StorageGroup group, @Nullable ItemStack @Nullable [] contents) {}

    public void markDirty(StorageGroup group) {
        if (closed) return;
        dirty.put(group.id(), ++generation);
    }

    public void markDeleted(StorageGroup group) {
        if (closed) return;
        dirty.remove(group.id());
        hopperTouched.remove(group.id());
        savedFingerprints.remove(group.id());
        deleted.add(group.id());
    }

    public void markTrustDirty(UUID owner) {
        if (closed) return;
        dirtyTrust.put(owner, ++generation);
    }

    /**
     * A hopper moved items through this group via the hopper bridge. With Paper's {@code hopper.disable-move-event}
     * no move event fires (spike S1), so such groups are compared against their last saved fingerprint at flush time.
     */
    public void markHopperTouched(ChestLinkGroup group) {
        hopperTouched.add(group.id());
    }

    public boolean isDirty(StorageGroup group) {
        return dirty.containsKey(group.id());
    }

    public int pendingCount() {
        return dirty.size() + deleted.size() + dirtyTrust.size();
    }

    /** Starts a flush pass: subsequent {@link #tick()} calls write everything that is dirty now, a bit per tick. */
    public void requestFlush() {
        promoteHopperTouched();
        flushing = true;
    }

    /** Called every tick by the central ticker. Cheap when nothing is pending. */
    public void tick() {
        if (!flushing || closed) return;
        SaveBatch batch = snapshot(maxPerTick.getAsInt(), true);
        if (batch == null) {
            flushing = !dirty.isEmpty() && inFlight.size() < dirty.size();
            return;
        }
        Map<Long, Long> generations = new HashMap<>();
        batch.groups().forEach(g -> generations.put(g.id(), dirty.get(g.id())));
        Map<UUID, Long> trustGenerations = new HashMap<>();
        batch.trust().keySet().forEach(owner -> trustGenerations.put(owner, dirtyTrust.get(owner)));
        batch.groups().forEach(g -> inFlight.add(g.id()));
        CompletableFuture.runAsync(() -> write(batch), io).whenComplete((ok, error) -> mainThread.accept(() -> {
            batch.groups().forEach(g -> inFlight.remove(g.id()));
            if (error != null) {
                logger.error("Failed to save {} ChestsPlusPlus group(s); will retry", batch.groups().size(), error);
                batch.deletedGroups().forEach(deleted::add);
                batch.trust().keySet().forEach(owner -> dirtyTrust.putIfAbsent(owner, ++generation));
                flushing = true;
                return;
            }
            generations.forEach((id, gen) -> dirty.remove(id, gen));
            trustGenerations.forEach((owner, gen) -> dirtyTrust.remove(owner, gen));
        }));
    }

    /** Stops the I/O thread, writes everything still dirty on the calling thread, and closes the database. */
    public void close() {
        if (closed) return;
        closed = true;
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Persistence I/O thread did not finish in 30s; continuing with the final flush");
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        inFlight.clear();
        promoteHopperTouched();
        SaveBatch batch = snapshot(Integer.MAX_VALUE, false);
        try {
            if (batch != null) repository.write(batch);
            dirty.clear();
            deleted.clear();
            dirtyTrust.clear();
        } catch (SQLException e) {
            logger.error("Final save of ChestsPlusPlus data failed; recent changes may be lost", e);
        }
        try {
            database.close();
        } catch (SQLException e) {
            logger.warn("Closing the ChestsPlusPlus database failed", e);
        }
    }

    private void write(SaveBatch batch) {
        try {
            repository.write(batch);
        } catch (SQLException e) {
            throw new CompletionException(e);
        }
    }

    /** Builds a batch of up to {@code limit} dirty groups (skipping in-flight ones), or null if there is nothing. */
    private @Nullable SaveBatch snapshot(int limit, boolean consumeDeletes) {
        List<GroupRecord> records = new ArrayList<>();
        for (Iterator<Long> it = dirty.keySet().iterator(); it.hasNext() && records.size() < limit;) {
            long id = it.next();
            if (inFlight.contains(id)) continue;
            StorageGroup group = groups.byId(id);
            if (group == null) {
                it.remove();
                continue;
            }
            List<Node> groupNodes = nodes.nodesOf(id);
            records.add(ModelMapper.toRecord(group, groupNodes));
            if (group instanceof ChestLinkGroup chest && chest.hasInventory()) {
                savedFingerprints.put(id, fingerprint(chest.inventory().getContents()));
            }
        }
        List<Long> deletes = List.copyOf(deleted);
        Map<UUID, Set<UUID>> trustChanges = new LinkedHashMap<>();
        dirtyTrust.keySet().forEach(owner -> trustChanges.put(owner, Set.copyOf(trust.trustedBy(owner))));
        if (consumeDeletes) deleted.clear();
        SaveBatch batch = new SaveBatch(records, deletes, trustChanges);
        return batch.isEmpty() ? null : batch;
    }

    private void promoteHopperTouched() {
        for (long id : hopperTouched) {
            if (groups.byId(id) instanceof ChestLinkGroup chest && chest.hasInventory()) {
                int now = fingerprint(chest.inventory().getContents());
                Integer saved = savedFingerprints.get(id);
                if (saved == null || saved != now) dirty.putIfAbsent(id, ++generation);
            }
        }
        hopperTouched.clear();
    }

    /** Cheap contents fingerprint: material and amount per slot. */
    static int fingerprint(@Nullable ItemStack[] contents) {
        int[] parts = new int[contents.length * 2];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.isEmpty()) continue;
            parts[i * 2] = item.getType().ordinal() + 1;
            parts[i * 2 + 1] = item.getAmount();
        }
        return Arrays.hashCode(parts);
    }
}
