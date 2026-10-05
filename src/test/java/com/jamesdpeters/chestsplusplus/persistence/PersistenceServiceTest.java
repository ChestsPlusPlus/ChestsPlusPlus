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
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceServiceTest extends PluginTestBase {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();

    @TempDir Path dir;

    private final ConcurrentLinkedQueue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();

    private final class Instance {
        final GroupRegistry groups = new GroupRegistry();
        final NodeIndex nodes = new NodeIndex();
        final TrustService trust = new TrustService();
        final PersistenceService persistence;

        Instance() throws Exception {
            persistence = new PersistenceService(Database.open("jdbc:sqlite:" + dir.resolve("data.db")), groups, nodes, trust, mainQueue::add,
                    () -> 1);
            persistence.load(loaded -> {
                if (loaded.group() instanceof ChestLinkGroup chest) {
                    ChestLinkHolder.attach(chest, Component.text("t"), loaded.contents());
                }
            });
        }
    }

    private void drainUntil(java.util.function.BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Runnable task;
            while ((task = mainQueue.poll()) != null) task.run();
            Thread.sleep(5);
        }
    }

    @Test
    void writeBehindSurvivesRestart() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup chest = new ChestLinkGroup(first.groups.nextId(), OWNER, "Ores", 1000);
        first.groups.add(chest);
        ChestLinkHolder.attach(chest, Component.text("t"), null);
        chest.inventory().setItem(0, ItemStack.of(Material.DIAMOND, 7));
        chest.inventory().setItem(53, ItemStack.of(Material.OAK_LOG, 64));
        chest.setSortMode(SortMode.AMOUNT_DESC);
        chest.setPublic(true);
        first.groups.addMember(chest, FRIEND);
        first.nodes.put(new Node(new BlockPos(WORLD, 1, 2, 3), BlockFace.EAST, chest.id()));
        AutoCraftGroup craft = new AutoCraftGroup(first.groups.nextId(), OWNER, "torches", 2000);
        @Nullable ItemStack[] matrix = new ItemStack[9];
        matrix[1] = ItemStack.of(Material.COAL);
        matrix[4] = ItemStack.of(Material.STICK, 5);
        craft.setRecipe(matrix, NamespacedKey.minecraft("torch"), ItemStack.of(Material.TORCH, 4));
        first.groups.add(craft);
        first.trust.trust(OWNER, FRIEND);

        first.persistence.markDirty(chest, Change.CONTENTS);
        first.persistence.markDirty(craft, Change.CONTENTS);
        first.persistence.requestFlush();
        // One group per tick (maxPerTick = 1), written asynchronously, acknowledged on the "main thread".
        for (int i = 0; i < 3; i++) first.persistence.tick();
        drainUntil(() -> first.persistence.pendingCount() == 0);
        assertThat(first.persistence.pendingCount()).isZero();

        // A change after the flush is written by the final synchronous flush on close.
        chest.inventory().setItem(1, ItemStack.of(Material.EMERALD, 2));
        first.persistence.markDirty(chest, Change.CONTENTS);
        first.persistence.close();

        Instance second = new Instance();
        ChestLinkGroup loaded = (ChestLinkGroup) second.groups.find(GroupType.CHESTLINK, OWNER, "ores");
        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(chest.id());
        assertThat(loaded.isPublic()).isTrue();
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
        assertThat(second.trust.isTrusted(OWNER, FRIEND)).isTrue();
        assertThat(second.groups.nextId()).isGreaterThan(craft.id());

        second.groups.remove(loaded);
        second.persistence.markDeleted(loaded);
        second.persistence.close();
        Instance third = new Instance();
        assertThat(third.groups.size()).isEqualTo(1);
        third.persistence.close();
    }

    @Test
    void hopperTouchedGroupsAreSavedOnlyWhenContentsChanged() throws Exception {
        Instance instance = new Instance();
        ChestLinkGroup chest = new ChestLinkGroup(instance.groups.nextId(), OWNER, "g", 0);
        instance.groups.add(chest);
        ChestLinkHolder.attach(chest, Component.text("t"), null);
        instance.persistence.markDirty(chest, Change.CONTENTS);
        instance.persistence.requestFlush();
        instance.persistence.tick();
        drainUntil(() -> instance.persistence.pendingCount() == 0);

        instance.persistence.markHopperTouched(chest);
        instance.persistence.requestFlush();
        instance.persistence.tick();
        assertThat(instance.persistence.isDirty(chest)).isFalse();

        chest.inventory().addItem(ItemStack.of(Material.COBBLESTONE, 3));
        instance.persistence.markHopperTouched(chest);
        instance.persistence.requestFlush();
        instance.persistence.tick();
        // Still dirty while the write is in flight; cleared once it commits.
        assertThat(instance.persistence.isDirty(chest)).isTrue();
        drainUntil(() -> !instance.persistence.isDirty(chest));
        assertThat(instance.persistence.isDirty(chest)).isFalse();
        instance.persistence.close();
    }

    @Test
    void unchangedContentsAreNotRewritten() throws Exception {
        Instance instance = new Instance();
        ChestLinkGroup chest = savedChest(instance);

        instance.persistence.markDirty(chest, Change.CONTENTS);
        instance.persistence.requestFlush();
        instance.persistence.tick();

        assertThat(instance.persistence.isDirty(chest)).isFalse();
        instance.persistence.close();
    }

    @Test
    void contentsWithSameTypeAndAmountButDifferentDataAreRewritten() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup chest = savedChest(first);
        ItemStack named = ItemStack.of(Material.DIAMOND_SWORD);
        named.editMeta(meta -> meta.displayName(Component.text("Excalibur")));

        chest.inventory().setItem(0, named);
        first.persistence.markDirty(chest, Change.CONTENTS);
        first.persistence.close();

        Instance second = new Instance();
        ChestLinkGroup loaded = (ChestLinkGroup) second.groups.byId(chest.id());
        assertThat(loaded.inventory().getItem(0)).isEqualTo(named);
        second.persistence.close();
    }

    @Test
    void metaOnlyChangeKeepsNodesAndContents() throws Exception {
        Instance first = new Instance();
        ChestLinkGroup chest = savedChest(first);

        chest.setPublic(true);
        first.persistence.markDirty(chest, Change.META);
        first.persistence.close();

        Instance second = new Instance();
        ChestLinkGroup loaded = (ChestLinkGroup) second.groups.byId(chest.id());
        assertThat(loaded.isPublic()).isTrue();
        assertThat(loaded.inventory().getItem(0)).isEqualTo(ItemStack.of(Material.DIAMOND_SWORD));
        assertThat(second.nodes.nodesOf(chest.id())).hasSize(1);
        second.persistence.close();
    }

    /** A ChestLink with one node and a plain sword in slot 0, already written to the database. */
    private ChestLinkGroup savedChest(Instance instance) throws InterruptedException {
        ChestLinkGroup chest = new ChestLinkGroup(instance.groups.nextId(), OWNER, "g", 0);
        instance.groups.add(chest);
        ChestLinkHolder.attach(chest, Component.text("t"), null);
        chest.inventory().setItem(0, ItemStack.of(Material.DIAMOND_SWORD));
        instance.nodes.put(new Node(new BlockPos(WORLD, 1, 2, 3), BlockFace.EAST, chest.id()));
        instance.persistence.markDirty(chest, Change.NODES);
        instance.persistence.requestFlush();
        instance.persistence.tick();
        drainUntil(() -> instance.persistence.pendingCount() == 0);
        return chest;
    }
}
