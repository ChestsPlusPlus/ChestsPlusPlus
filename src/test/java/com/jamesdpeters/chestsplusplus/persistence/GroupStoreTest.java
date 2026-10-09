package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupStoreTest extends PluginTestBase {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();

    @TempDir Path dir;

    private final ConcurrentLinkedQueue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();

    /** One plugin lifetime: a model, its stores and the engine, loaded from the shared database file. */
    private final class Instance {
        final GroupRegistry groups = new GroupRegistry();
        final NodeIndex nodes = new NodeIndex();
        final TrustService trust = new TrustService();
        final Persistence persistence = new Persistence(Database.open(url()), mainQueue::add);
        final GroupStore groupStore = new GroupStore(persistence, groups, nodes, loaded -> {
            if (loaded.group() instanceof ChestLinkGroup chest) ChestLinkHolder.attach(chest, Component.text("t"), loaded.items());
        });
        final TrustStore trustStore = new TrustStore(persistence, trust);

        Instance() {
            persistence.register(groupStore);
            persistence.register(trustStore);
            trust.onChange(trustStore::markDirty);
            persistence.load();
        }

        ChestLinkGroup chest(String name) {
            ChestLinkGroup chest = new ChestLinkGroup(groups.nextId(), OWNER, name, 1000);
            groups.add(chest);
            ChestLinkHolder.attach(chest, Component.text("t"), null);
            groupStore.markDirty(chest);
            return chest;
        }

        void flushAndWait() throws InterruptedException {
            persistence.flush();
            drainUntil(() -> persistence.pending() == 0);
        }
    }

    private String url() {
        return "jdbc:sqlite:" + dir.resolve("data.db");
    }

    private void drainUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Runnable task;
            while ((task = mainQueue.poll()) != null) task.run();
            Thread.sleep(5);
        }
        assertThat(done.getAsBoolean()).as("condition reached within 10s").isTrue();
    }

    private int count(String table) {
        try (Database db = Database.open(url())) {
            return db.handle().createQuery("SELECT count(*) FROM " + table).mapTo(int.class).one();
        }
    }

    @Test
    void writeBehindSurvivesRestart() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup chest = first.chest("Ores");
        chest.inventory().setItem(0, ItemStack.of(Material.DIAMOND, 7));
        chest.inventory().setItem(53, ItemStack.of(Material.OAK_LOG, 64));
        chest.setSortMode(SortMode.AMOUNT_DESC);
        chest.setPublic(true);
        chest.setV2Source("chestlink:owner:Ores");
        first.groups.addMember(chest, FRIEND);
        first.nodes.put(new Node(new BlockPos(WORLD, 1, 2, 3), BlockFace.EAST, chest.id()));
        AutoCraftGroup craft = new AutoCraftGroup(first.groups.nextId(), OWNER, "torches", 2000);
        @Nullable ItemStack[] matrix = new ItemStack[9];
        matrix[1] = ItemStack.of(Material.COAL);
        matrix[4] = ItemStack.of(Material.STICK, 5);
        craft.setRecipe(matrix, NamespacedKey.minecraft("torch"), ItemStack.of(Material.TORCH, 4));
        craft.setMatch(1, SlotMatch.TYPE);
        first.groups.add(craft);
        first.groupStore.markDirty(craft);
        first.trust.trust(OWNER, FRIEND);
        first.flushAndWait();

        // A change after the flush is written by the final flush on close.
        chest.inventory().setItem(1, ItemStack.of(Material.EMERALD, 2));
        first.groupStore.markDirty(chest);
        first.persistence.close();

        Instance second = new Instance();
        ChestLinkGroup loaded = (ChestLinkGroup) second.groups.find(GroupType.CHESTLINK, OWNER, "ores");
        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(chest.id());
        assertThat(loaded.isPublic()).isTrue();
        assertThat(loaded.v2Source()).isEqualTo("chestlink:owner:Ores");
        assertThat(loaded.sortMode()).isEqualTo(SortMode.AMOUNT_DESC);
        assertThat(loaded.members()).containsExactly(FRIEND);
        assertThat(loaded.inventory().getItem(0)).isEqualTo(ItemStack.of(Material.DIAMOND, 7));
        assertThat(loaded.inventory().getItem(1)).isEqualTo(ItemStack.of(Material.EMERALD, 2));
        assertThat(loaded.inventory().getItem(2)).isNull();
        assertThat(loaded.inventory().getItem(53)).isEqualTo(ItemStack.of(Material.OAK_LOG, 64));
        assertThat(second.nodes.nodesOf(chest.id())).containsExactly(new Node(new BlockPos(WORLD, 1, 2, 3), BlockFace.EAST, chest.id()));
        AutoCraftGroup loadedCraft = (AutoCraftGroup) second.groups.find(GroupType.AUTOCRAFT, OWNER, "Torches");
        assertThat(loadedCraft.recipeKey()).isEqualTo(NamespacedKey.minecraft("torch"));
        assertThat(loadedCraft.matrix()[4]).isEqualTo(ItemStack.of(Material.STICK));
        assertThat(loadedCraft.matrix()[0]).isNull();
        assertThat(loadedCraft.matches()[1]).isEqualTo(SlotMatch.TYPE);
        assertThat(loadedCraft.matches()[4]).isEqualTo(SlotMatch.RECIPE);
        assertThat(second.trust.isTrusted(OWNER, FRIEND)).isTrue();
        assertThat(second.groups.nextId()).isGreaterThan(craft.id());

        second.trust.untrust(OWNER, FRIEND);
        second.persistence.close();
        assertThat(count("trust")).isZero();
    }

    @Test
    void deletingAGroupRemovesItsMembersAndNodes() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup doomed = first.chest("doomed");
        first.groups.addMember(doomed, FRIEND);
        first.nodes.put(new Node(new BlockPos(WORLD, 0, 0, 0), BlockFace.NORTH, doomed.id()));
        ChestLinkGroup kept = first.chest("kept");
        first.nodes.put(new Node(new BlockPos(WORLD, 1, 0, 0), BlockFace.NORTH, kept.id()));
        first.flushAndWait();

        first.nodes.removeGroup(doomed.id());
        first.groups.remove(doomed);
        first.groupStore.markDirty(doomed);
        first.persistence.close();

        assertThat(count("groups")).isEqualTo(1);
        assertThat(count("group_members")).isZero();
        assertThat(count("nodes")).isEqualTo(1);
    }

    @Test
    void deletedGroupsIdIsNotReusedAfterRestart() throws Exception {
        Instance first = new Instance();
        first.chest("kept");
        ChestLinkGroup last = first.chest("last");
        first.flushAndWait();
        first.groups.remove(last);
        first.groupStore.markDirty(last);
        first.persistence.close();

        Instance second = new Instance();
        assertThat(second.groups.byId(last.id())).isNull();
        assertThat(second.groups.nextId()).isGreaterThan(last.id());
        second.persistence.close();
    }

    @Test
    void idOfAGroupDeletedBeforeItWasEverSavedIsNotReusedAfterRestart() throws Exception {
        Instance first = new Instance();
        first.chest("kept");
        ChestLinkGroup fleeting = first.chest("fleeting");
        first.groups.remove(fleeting);
        first.persistence.close();

        Instance second = new Instance();
        assertThat(second.groups.nextId()).isGreaterThan(fleeting.id());
        second.persistence.close();
    }

    @Test
    void nodeMovedBetweenGroupsInOneFlushStaysWithItsNewGroup() throws Exception {
        Instance first = new Instance();
        Node a = new Node(new BlockPos(WORLD, 0, 0, 0), BlockFace.NORTH, 1);
        BlockPos b = new BlockPos(WORLD, 1, 0, 0);
        ChestLinkGroup from = first.chest("from");
        first.nodes.put(a);
        first.nodes.put(new Node(b, BlockFace.NORTH, from.id()));
        first.flushAndWait();

        ChestLinkGroup to = first.chest("to");
        first.nodes.put(new Node(b, BlockFace.SOUTH, to.id()));
        first.groupStore.markDirty(from);
        first.persistence.close();

        Instance second = new Instance();
        assertThat(second.nodes.nodesOf(from.id())).containsExactly(a);
        assertThat(second.nodes.nodesOf(to.id())).containsExactly(new Node(b, BlockFace.SOUTH, to.id()));
        second.persistence.close();
    }

    @Test
    void groupTakingAnotherGroupsOldNameInOneFlushIsSaved() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup ores = first.chest("ores");
        ChestLinkGroup stuff = first.chest("stuff");
        first.flushAndWait();

        // stuff is dirty first, so it is written first, while the database still has ores under its old name.
        first.groupStore.markDirty(stuff);
        first.groups.rename(ores, "old-ores");
        first.groupStore.markDirty(ores);
        first.groups.rename(stuff, "ores");
        first.flushAndWait();
        assertThat(first.groupStore.isDirty(stuff)).isFalse();
        first.persistence.close();

        Instance second = new Instance();
        assertThat(second.groups.byId(ores.id()).name()).isEqualTo("old-ores");
        assertThat(second.groups.byId(stuff.id()).name()).isEqualTo("ores");
        second.persistence.close();
    }

    @Test
    void renamedSwordInTheSameSlotSurvivesRestart() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup chest = first.chest("g");
        chest.inventory().setItem(0, ItemStack.of(Material.DIAMOND_SWORD));
        first.flushAndWait();

        ItemStack named = ItemStack.of(Material.DIAMOND_SWORD);
        named.editMeta(meta -> meta.displayName(Component.text("Excalibur")));
        chest.inventory().setItem(0, named);
        first.groupStore.markDirty(chest);
        first.persistence.close();

        Instance second = new Instance();
        ChestLinkGroup loaded = (ChestLinkGroup) second.groups.byId(chest.id());
        assertThat(loaded.inventory().getItem(0)).isEqualTo(named);
        second.persistence.close();
    }

    @Test
    void touchedGroupIsSavedOnlyWhenItsContentsChanged() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup chest = first.chest("g");
        chest.inventory().setItem(0, ItemStack.of(Material.DIAMOND_SWORD));
        chest.inventory().setItem(1, ItemStack.of(Material.DIAMOND, 7));
        chest.inventory().setItem(2, ItemStack.of(Material.OAK_LOG, 64));
        first.flushAndWait();

        // The first touch saves it, as nothing was kept to compare against.
        assertThat(savedOnTouch(first, chest)).isTrue();
        assertThat(savedOnTouch(first, chest)).isFalse();
        chest.inventory().setItem(1, ItemStack.of(Material.OAK_LOG, 64));
        chest.inventory().setItem(2, ItemStack.of(Material.DIAMOND, 7));
        assertThat(savedOnTouch(first, chest)).isTrue();
        chest.inventory().setItem(1, ItemStack.of(Material.OAK_LOG, 63));
        assertThat(savedOnTouch(first, chest)).isTrue();
        ItemStack named = ItemStack.of(Material.DIAMOND_SWORD);
        named.editMeta(meta -> meta.displayName(Component.text("Excalibur")));
        chest.inventory().setItem(0, named);
        first.groupStore.touch(chest);
        first.persistence.close();

        Instance second = new Instance();
        ChestLinkGroup loaded = (ChestLinkGroup) second.groups.byId(chest.id());
        assertThat(loaded.inventory().getContents()).startsWith(named, ItemStack.of(Material.OAK_LOG, 63), ItemStack.of(Material.DIAMOND, 7));
        assertThat(savedOnTouch(second, loaded)).isTrue();
        assertThat(savedOnTouch(second, loaded)).isFalse();
        second.persistence.close();
    }

    @Test
    void hopperDoesNotRetryAGroupThatWasGivenUpOnUntilItChanges() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup stuck = first.chest("stuck");
        ChestLinkGroup fine = first.chest("fine");
        first.flushAndWait();
        try (Database db = Database.open(url())) {
            db.handle()
                    .execute("CREATE TRIGGER stuck BEFORE UPDATE ON groups WHEN NEW.id = " + stuck.id() + " BEGIN SELECT RAISE(ABORT, 'stuck'); END");
        }

        // Another group saves in each flush, so the failures count against the stuck one rather than the database.
        for (int attempt = 0; attempt <= Persistence.MAX_ATTEMPTS; attempt++) {
            first.groupStore.touch(stuck);
            first.groupStore.markDirty(fine);
            flushAndDrain(first);
        }
        assertThat(first.groupStore.isDirty(stuck)).isFalse();

        first.groupStore.touch(stuck);
        first.persistence.flush();
        assertThat(first.persistence.pending()).isZero();

        stuck.inventory().setItem(0, ItemStack.of(Material.DIRT));
        first.groupStore.touch(stuck);
        flushAndDrain(first);
        assertThat(first.groupStore.isDirty(stuck)).isTrue();
        first.persistence.close();
    }

    /** Flushes and waits for that flush alone, which {@link Instance#flushAndWait} can't when a key keeps failing. */
    private void flushAndDrain(Instance instance) throws InterruptedException {
        CompletableFuture<Void> flushed = instance.persistence.flush();
        drainUntil(flushed::isDone);
    }

    /** Touches the group as a hopper would and flushes; true if that wrote it. */
    private boolean savedOnTouch(Instance instance, ChestLinkGroup chest) throws InterruptedException {
        instance.groupStore.touch(chest);
        instance.persistence.flush();
        boolean saved = instance.persistence.pending() > 0;
        instance.flushAndWait();
        return saved;
    }
}
