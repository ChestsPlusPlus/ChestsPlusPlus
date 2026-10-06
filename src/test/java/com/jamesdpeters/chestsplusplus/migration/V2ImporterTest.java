package com.jamesdpeters.chestsplusplus.migration;

import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.ALICE;
import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.Database;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.persistence.TrustStore;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The importer against its own model and database, so AutoCraft groups don't need recipe matching (MockBukkit has none). */
class V2ImporterTest extends PluginTestBase {

    @TempDir Path dir;

    private World world;
    private final GroupRegistry groups = new GroupRegistry();
    private final NodeIndex nodes = new NodeIndex();
    private final TrustService trust = new TrustService();
    private final MigrationState state = new MigrationState();
    private final V2PendingLocations pending = new V2PendingLocations();
    private final V2Cleanup cleanup = new V2Cleanup();
    private final List<Node> spawned = new ArrayList<>();
    private Database database;
    private Persistence persistence;
    private GroupStore groupStore;
    private V2Importer importer;
    private boolean failAttach;
    private Settings settings = Settings.DEFAULTS;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        database = Database.open("jdbc:sqlite:" + dir.resolve("data.db"));
        persistence = new Persistence(database, Runnable::run);
        groupStore = new GroupStore(persistence, groups, nodes, loaded -> {
            if (failAttach && loaded.group().name().equals("iron")) throw new IllegalStateException("simulated attach failure");
            if (loaded.group() instanceof ChestLinkGroup chest) ChestLinkHolder.attach(chest, Component.text("t"), loaded.items());
        });
        persistence.register(groupStore);
        V2CleanupStore cleanupStore = new V2CleanupStore(persistence, cleanup);
        MigrationStateStore stateStore = new MigrationStateStore(persistence, state);
        V2PendingLocationStore pendingStore = new V2PendingLocationStore(persistence, groups, pending);
        TrustStore trustStore = new TrustStore(persistence,
                trust);
        persistence.register(trustStore);
        persistence.register(cleanupStore);
        persistence.register(stateStore);
        persistence.register(pendingStore);
        trust.onChange(trustStore::markDirty);
        cleanup.onChange(cleanupStore::markDirty);
        state.onChange(stateStore::markDirty);
        pending.onChange(pendingStore::markDirty);
        groups.onRemove(pending::removeGroup);
        persistence.load();
        importer = new V2Importer(groups, nodes, trust, groupStore, cleanup, new WorldUids(server, () -> dir.toFile()), state, pending,
                () -> settings, spawned::add);
        server.addRecipe(new ShapedRecipe(NamespacedKey.minecraft("torch"), ItemStack.of(Material.TORCH, 4)).shape("C", "S")
                .setIngredient('C', Material.COAL)
                .setIngredient('S', Material.STICK));
    }

    @AfterEach
    void close() {
        persistence.close();
    }

    private ImportReport importFixture(boolean apply) {
        return importer.run(V2Storage.parse(V2Fixtures.storage()), apply);
    }

    private ChestLinkGroup chest(UUID owner, String name) {
        return (ChestLinkGroup) groups.find(GroupType.CHESTLINK, owner, name);
    }

    private BlockPos at(int x, int y, int z) {
        return new BlockPos(world.getUID(), x, y, z);
    }

    @Test
    void importsGroupsMembersItemsAndTrust() {
        ImportReport report = importFixture(true);

        ChestLinkGroup ore = chest(ALICE, "Iron Ore");
        assertThat(ore).isNotNull();
        assertThat(ore.isPublic()).isTrue();
        assertThat(ore.sortMode()).isEqualTo(SortMode.AMOUNT_DESC);
        assertThat(ore.members()).containsExactly(BOB);
        assertThat(ore.inventory().getSize()).isEqualTo(ChestLinkGroup.SIZE);
        assertThat(ore.inventory().getItem(0)).isEqualTo(ItemStack.of(Material.DIAMOND, 5));
        assertThat(ore.v2Source()).isEqualTo("chestlink:" + ALICE + ":Iron Ore");
        assertThat(nodes.nodesOf(ore.id())).containsExactly(new Node(at(0, 64, 0), BlockFace.NORTH, ore.id()));
        assertThat(chest(BOB, "Old")).isNotNull();
        assertThat(trust.isTrusted(ALICE, BOB)).isTrue();
        assertThat(trust.trustedBy(ALICE)).containsExactly(BOB);
        assertThat(groupStore.isDirty(ore)).isTrue();

        assertThat(cleanup.size()).isEqualTo(5);
        assertThat(spawned).hasSize(5);
        assertThat(report.chestLinks()).isEqualTo(4);
        assertThat(report.autoCrafters()).isEqualTo(2);
        assertThat(report.nodes()).isEqualTo(5);
        assertThat(report.itemStacks()).isEqualTo(1);
        assertThat(report.trusted()).isEqualTo(1);
        assertThat(report.lines()).anyMatch(line -> line.contains("Broken"))
                .anyMatch(line -> line.contains("'lost_world' wasn't found"))
                .anyMatch(line -> line.contains("removedpack:thing"));
    }

    @Test
    void rebuildsTheAutoCraftMatrixFromTheRecipe() {
        importFixture(true);

        AutoCraftGroup torches = (AutoCraftGroup) groups.find(GroupType.AUTOCRAFT, ALICE, "torches");
        assertThat(torches.recipeKey()).isEqualTo(NamespacedKey.minecraft("torch"));
        // Any item the slot's choice accepts will do; the slot keeps RECIPE matching.
        assertThat(torches.matrix()[0].getType()).isIn(Material.COAL, Material.CHARCOAL);
        assertThat(torches.matrix()[3]).isEqualTo(ItemStack.of(Material.STICK));
        AutoCraftGroup gone = (AutoCraftGroup) groups.find(GroupType.AUTOCRAFT, ALICE, "gone");
        assertThat(gone.matrixIsEmpty()).isTrue();
        assertThat(gone.recipeKey()).isEqualTo(NamespacedKey.fromString("removedpack:thing"));
    }

    @Test
    void namesAreCleanedAndClashesGetASuffix() {
        ImportReport report = importFixture(true);

        assertThat(chest(ALICE, "iron").name()).isEqualTo("iron");
        assertThat(groups.ownedBy(ALICE, GroupType.CHESTLINK)).extracting(StorageGroup::name).containsExactlyInAnyOrder("Iron Ore", "iron", "IRON_2");
        assertThat(report.lines()).anyMatch(line -> line.contains("\"IRON\" to IRON_2")).noneMatch(line -> line.contains("\"Iron Ore\" to"));
        assertThat(V2Importer.cleanName("§aGreen §lStuff!")).isEqualTo("Green Stuff");
        assertThat(V2Importer.cleanName("  Iron   Ore? ")).isEqualTo("Iron Ore");
        assertThat(V2Importer.cleanName("[]")).isEqualTo("group");
        assertThat(V2Importer.cleanName("x".repeat(40))).hasSize(32);
    }

    @Test
    void aPreviewChangesNothing() {
        ImportReport report = importFixture(false);

        assertThat(report.groups()).isEqualTo(6);
        assertThat(groups.size()).isZero();
        assertThat(nodes.size()).isZero();
        assertThat(trust.trustedBy(ALICE)).isEmpty();
        assertThat(cleanup.isEmpty()).isTrue();
        assertThat(pending.isEmpty()).isTrue();
        assertThat(state.importCompleted()).isFalse();
        assertThat(persistence.pending()).isZero();
    }

    @Test
    void importingAgainDoesNotReplayAnything() throws IOException {
        importFixture(true);
        ChestLinkGroup ore = chest(ALICE, "Iron Ore");
        int groupCount = groups.size();
        UUID lostWorld = UUID.randomUUID();
        writeUid(dir.resolve("lost_world").resolve("uid.dat"), lostWorld);

        ImportReport again = importFixture(true);

        assertThat(groups.size()).isEqualTo(groupCount);
        assertThat(again.groups()).isZero();
        assertThat(again.trusted()).isZero();
        assertThat(nodes.nodesOf(ore.id())).extracting(Node::pos).containsExactly(at(0, 64, 0));
        assertThat(ore.inventory().getItem(0)).isEqualTo(ItemStack.of(Material.DIAMOND, 5));
        assertThat(ore.inventory().getItem(1)).isNull();
        assertThat(again.lines().getFirst()).contains("one-time v2 import is complete");
    }

    @Test
    void aBlockUnlinkedSinceTheImportIsNotLinkedAgain() {
        importFixture(true);
        ChestLinkGroup iron = chest(ALICE, "iron");
        nodes.remove(at(2, 64, 0));

        ImportReport again = importFixture(true);

        assertThat(nodes.nodesOf(iron.id())).isEmpty();
        assertThat(again.nodes()).isZero();
        assertThat(again.lines().getFirst()).contains("one-time v2 import is complete");
    }

    @Test
    void blocksAndNamesAlreadyUsedInV3AreLeftAlone() {
        ChestLinkGroup existing = new ChestLinkGroup(groups.nextId(), ALICE, "Iron Ore", 0);
        groups.add(existing);
        nodes.put(new Node(at(2, 64, 0), BlockFace.EAST, existing.id()));

        ImportReport report = importFixture(true);

        assertThat(chest(ALICE, "Iron Ore_2").v2Source()).contains("Iron Ore");
        assertThat(nodes.get(at(2, 64, 0)).groupId()).isEqualTo(existing.id());
        assertThat(nodes.nodesOf(chest(ALICE, "iron").id())).isEmpty();
        assertThat(report.lines()).anyMatch(line -> line.contains("2 64 0") && line.contains("already linked"));
    }

    @Test
    void groupsOverTheDefaultLimitAreReported() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("limits.chestlink-default", 2);
        settings = Settings.from(config);

        ImportReport report = importFixture(false);

        assertThat(report.lines()).anyMatch(line -> line.contains("now has 3 ChestLinks, more than the default limit of 2"));
    }

    @Test
    void failedPartialAdoptionRollsBackAndCanRetry() {
        failAttach = true;
        assertThatThrownBy(() -> importFixture(true)).hasMessageContaining("simulated attach failure");
        assertThat(groups.size()).isZero();
        assertThat(nodes.size()).isZero();
        assertThat(pending.size()).isZero();
        assertThat(cleanup.size()).isZero();
        assertThat(state.importCompleted()).isFalse();
        failAttach = false;
        assertThat(importFixture(true).groups()).isEqualTo(6);
        assertThat(chest(ALICE, "Iron Ore").inventory().getItem(0)).isEqualTo(ItemStack.of(Material.DIAMOND, 5));
    }

    @Test
    void completionAndUnresolvedLocationsSurviveDatabaseReload() {
        importFixture(true);
        trust.untrust(ALICE, BOB);
        persistence.flush().join();
        pending.load(List.of());
        state.load(Map.of());
        List.copyOf(groups.all()).forEach(groups::remove);
        List.copyOf(nodes.all()).forEach(node -> nodes.remove(node.pos()));
        persistence.load();
        assertThat(state.importCompleted()).isTrue();
        assertThat(pending.all()).hasSize(1).allMatch(row -> row.groupId() == chest(ALICE, "Iron Ore").id());
        assertThat(importFixture(true).groups()).isZero();
        assertThat(trust.isTrusted(ALICE, BOB)).isFalse();
    }

    @Test
    void failedDatabaseTransactionKeepsCompletionAndPendingWithTheImportForRetry() {
        database.handle()
                .execute("CREATE TRIGGER fail_import BEFORE INSERT ON migration_state BEGIN SELECT RAISE(FAIL, 'simulated disk failure'); END");
        importFixture(true);
        assertThatThrownBy(() -> persistence.flush().join()).hasCauseInstanceOf(RuntimeException.class);
        assertThat(database.handle().createQuery("SELECT count(*) FROM groups").mapTo(int.class).one()).isZero();
        assertThat(database.handle().createQuery("SELECT count(*) FROM migration_state").mapTo(int.class).one()).isZero();
        assertThat(database.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isZero();
        assertThat(importFixture(true).groups()).isZero();
        trust.untrust(ALICE, BOB);
        ChestLinkGroup ore = chest(ALICE, "Iron Ore");
        ore.inventory().setItem(0, ItemStack.of(Material.COAL, 2));
        database.handle().execute("DROP TRIGGER fail_import");
        persistence.flush().join();
        assertThat(database.handle().createQuery("SELECT value FROM migration_state WHERE key = 'v2_import'").mapTo(String.class).one())
                .isEqualTo("COMPLETE");
        assertThat(database.handle().createQuery("SELECT count(*) FROM groups").mapTo(int.class).one()).isEqualTo(6);
        assertThat(database.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isEqualTo(1);
        assertThat(database.handle().createQuery("SELECT count(*) FROM trust").mapTo(int.class).one()).isZero();
    }

    private static void writeUid(Path file, UUID uuid) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file); DataOutputStream data = new DataOutputStream(out)) {
            data.writeLong(uuid.getMostSignificantBits());
            data.writeLong(uuid.getLeastSignificantBits());
        }
    }
}
