package com.jamesdpeters.chestsplusplus.persistence;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupRecord;
import com.jamesdpeters.chestsplusplus.persistence.Records.GroupSave;
import com.jamesdpeters.chestsplusplus.persistence.Records.LoadedData;
import com.jamesdpeters.chestsplusplus.persistence.Records.SaveBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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
import lombok.extern.slf4j.Slf4j;
import org.bukkit.inventory.ItemStack;
import org.jdbi.v3.core.JdbiException;
import org.jspecify.annotations.Nullable;

/**
 * Write-behind persistence. The in-memory model is authoritative; dirty groups are snapshotted on the main thread, a bounded number per
 * tick, and written by a single I/O thread. A save rewrites only the parts of a group that changed, and skips contents whose serialised
 * bytes match the last save. A dirty flag is cleared only once its write has committed. {@link #close()} drains everything synchronously.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
public final class PersistenceService {

    private final Database database;
    private final Repository repository;
    private final GroupRegistry groups;
    private final NodeIndex nodes;
    private final TrustService trust;
    private final Consumer<Runnable> mainThread;
    private final IntSupplier maxPerTick;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ChestsPlusPlus-persistence-io");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Long, Pending> dirty = new LinkedHashMap<>();
    /** Groups with a row in the database; any other group is saved in full, whatever changed. */
    private final Set<Long> persisted = new HashSet<>();
    private final Set<Long> deleted = new LinkedHashSet<>();
    private final Map<UUID, Long> dirtyTrust = new LinkedHashMap<>();
    private final Set<Long> inFlight = new HashSet<>();
    private final Set<Long> hopperTouched = new HashSet<>();
    /**
     * SHA-256 of each group's contents as last committed. Hashing the bytes, not the items, catches every change to item data. Only
     * updated once a write commits, so a failed write is never skipped on retry.
     */
    private final Map<Long, byte[]> savedDigests = new HashMap<>();
    private final Map<Long, byte[]> writingDigests = new HashMap<>();
    private long generation;
    private boolean flushing;
    private boolean closed;

    /** What changed in a group since its last save, and the generation of its latest change. */
    private static final class Pending {
        private long generation;
        private final EnumSet<Change> changes = EnumSet.noneOf(Change.class);
    }

    /**
     * @param mainThread runs a task on the server thread (the scheduler); used for I/O completion callbacks
     * @param maxPerTick current {@code storage.max-serialisations-per-tick}
     */
    public PersistenceService(Database database, GroupRegistry groups, NodeIndex nodes, TrustService trust,
            Consumer<Runnable> mainThread, IntSupplier maxPerTick) {
        this.database = database;
        this.repository = database.repository();
        this.groups = groups;
        this.nodes = nodes;
        this.trust = trust;
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
    public int load(Consumer<LoadedGroup> attach) {
        LoadedData data = loadAllOnIoThread();
        for (GroupRecord record : data.groups()) {
            StorageGroup group = ModelMapper.fromRecord(record);
            groups.add(group);
            record.members().forEach(member -> groups.addMember(group, member));
            ModelMapper.nodes(record).forEach(nodes::put);
            @Nullable ItemStack[] contents = record.inventory() == null ? null : ModelMapper.deserialize(record.inventory());
            attach.accept(new LoadedGroup(group, contents));
            persisted.add(group.id());
            byte[] digest = digest(record);
            if (digest != null) savedDigests.put(group.id(), digest);
        }
        trust.load(data.trust());
        return data.groups().size();
    }

    private LoadedData loadAllOnIoThread() {
        try {
            return CompletableFuture.supplyAsync(repository::loadAll, io).join();
        } catch (CompletionException e) {
            throw e.getCause() instanceof JdbiException jdbi ? jdbi : e;
        }
    }

    /** A freshly loaded group and its stored ChestLink contents (null for AutoCraft groups). */
    public record LoadedGroup(StorageGroup group, @Nullable ItemStack @Nullable [] contents) {}

    public void markDirty(StorageGroup group, Change change) {
        if (!closed) mark(group.id(), change);
    }

    private void mark(long id, Change change) {
        Pending pending = dirty.computeIfAbsent(id, k -> new Pending());
        pending.generation = ++generation;
        pending.changes.add(change);
    }

    public void markDeleted(StorageGroup group) {
        if (closed) return;
        long id = group.id();
        dirty.remove(id);
        persisted.remove(id);
        hopperTouched.remove(id);
        savedDigests.remove(id);
        deleted.add(id);
    }

    public void markTrustDirty(UUID owner) {
        if (closed) return;
        dirtyTrust.put(owner, ++generation);
    }

    /**
     * A hopper moved items through this group via the hopper bridge. With Paper's {@code hopper.disable-move-event} no move event fires,
     * so such groups are marked dirty at flush time and their contents compared with the last save.
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
        batch.groups().forEach(save -> generations.put(save.group().id(), dirty.get(save.group().id()).generation));
        Map<UUID, Long> trustGenerations = new HashMap<>();
        batch.trust().keySet().forEach(owner -> trustGenerations.put(owner, dirtyTrust.get(owner)));
        inFlight.addAll(generations.keySet());
        CompletableFuture.runAsync(() -> repository.write(batch), io)
                .whenComplete((ok, error) -> mainThread.accept(() -> onWritten(batch, generations, trustGenerations, error)));
    }

    /** Clears the dirty flags the batch covered, unless they changed again meanwhile. On failure, re-queues the batch. */
    private void onWritten(SaveBatch batch, Map<Long, Long> generations, Map<UUID, Long> trustGenerations, @Nullable Throwable error) {
        inFlight.removeAll(generations.keySet());
        if (error != null) {
            log.error("Failed to save {} ChestsPlusPlus group(s); will retry", batch.groups().size(), error);
            generations.keySet().forEach(writingDigests::remove);
            deleted.addAll(batch.deletedGroups());
            batch.trust().keySet().forEach(owner -> dirtyTrust.putIfAbsent(owner, ++generation));
            flushing = true;
            return;
        }
        generations.forEach(this::onGroupSaved);
        trustGenerations.forEach(dirtyTrust::remove);
    }

    private void onGroupSaved(long id, long savedGeneration) {
        byte[] digest = writingDigests.remove(id);
        if (groups.byId(id) == null) return;
        persisted.add(id);
        if (digest != null) savedDigests.put(id, digest);
        Pending pending = dirty.get(id);
        if (pending != null && pending.generation == savedGeneration) dirty.remove(id);
    }

    /** Stops the I/O thread, writes everything still dirty on the calling thread, and closes the database. */
    public void close() {
        if (closed) return;
        closed = true;
        stopIoThread();
        writeEverythingNow();
        try {
            database.close();
        } catch (JdbiException e) {
            log.warn("Closing the ChestsPlusPlus database failed", e);
        }
    }

    private void stopIoThread() {
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Persistence I/O thread did not finish in 30s; continuing with the final flush");
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeEverythingNow() {
        inFlight.clear();
        promoteHopperTouched();
        SaveBatch batch = snapshot(Integer.MAX_VALUE, false);
        try {
            if (batch != null) repository.write(batch);
            dirty.clear();
            deleted.clear();
            dirtyTrust.clear();
        } catch (JdbiException e) {
            log.error("Final save of ChestsPlusPlus data failed; recent changes may be lost", e);
        }
    }

    /**
     * Builds a batch from up to {@code limit} dirty groups (skipping in-flight ones), or null if there is nothing. The limit counts groups
     * snapshotted, not saved, since snapshotting is the main-thread cost. Groups left with nothing to save are no longer dirty.
     */
    private @Nullable SaveBatch snapshot(int limit, boolean consumeDeletes) {
        List<GroupSave> saves = new ArrayList<>();
        int snapshotted = 0;
        for (Iterator<Map.Entry<Long, Pending>> it = dirty.entrySet().iterator(); it.hasNext() && snapshotted < limit;) {
            Map.Entry<Long, Pending> entry = it.next();
            if (inFlight.contains(entry.getKey())) continue;
            StorageGroup group = groups.byId(entry.getKey());
            if (group != null) snapshotted++;
            GroupSave save = group == null ? null : snapshotGroup(group, entry.getValue());
            if (save == null) {
                it.remove();
                continue;
            }
            saves.add(save);
        }
        List<Long> deletes = List.copyOf(deleted);
        Map<UUID, Set<UUID>> trustChanges = new LinkedHashMap<>();
        dirtyTrust.keySet().forEach(owner -> trustChanges.put(owner, Set.copyOf(trust.trustedBy(owner))));
        if (consumeDeletes) deleted.clear();
        SaveBatch batch = new SaveBatch(saves, deletes, trustChanges);
        return batch.isEmpty() ? null : batch;
    }

    /** Everything for a group not yet in the database; otherwise what changed, minus contents that match the last save. Null if nothing. */
    private @Nullable GroupSave snapshotGroup(StorageGroup group, Pending pending) {
        long id = group.id();
        boolean inDatabase = persisted.contains(id);
        EnumSet<Change> changes = inDatabase ? EnumSet.copyOf(pending.changes) : EnumSet.allOf(Change.class);
        GroupRecord record = ModelMapper.toRecord(group, nodes.nodesOf(id), changes.contains(Change.CONTENTS));
        byte[] digest = digest(record);
        if (digest != null && inDatabase && Arrays.equals(digest, savedDigests.get(id))) {
            changes.remove(Change.CONTENTS);
        } else if (digest != null) {
            writingDigests.put(id, digest);
        }
        return changes.isEmpty() ? null : new GroupSave(record, changes);
    }

    private void promoteHopperTouched() {
        for (long id : hopperTouched) {
            if (groups.byId(id) != null) mark(id, Change.CONTENTS);
        }
        hopperTouched.clear();
    }

    /** SHA-256 of the record's serialised contents (inventory, or recipe key and matrix), or null when it carries none. */
    private static byte @Nullable [] digest(GroupRecord record) {
        byte[] contents = record.inventory() != null ? record.inventory() : record.matrix();
        if (contents == null) return null;
        MessageDigest sha256 = sha256();
        sha256.update(contents);
        if (record.recipeKey() != null) sha256.update(record.recipeKey().getBytes(StandardCharsets.UTF_8));
        return sha256.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM must provide SHA-256", e);
        }
    }
}
