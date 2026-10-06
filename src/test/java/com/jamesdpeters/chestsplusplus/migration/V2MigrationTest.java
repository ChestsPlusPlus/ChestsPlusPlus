package com.jamesdpeters.chestsplusplus.migration;

import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.ALICE;
import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.Database;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class V2MigrationTest extends PluginTestBase {

    private World world;
    private Services services;
    private Path storage;

    @BeforeEach
    void setUp() throws IOException {
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        services = plugin.services();
        storage = plugin.getDataFolder().toPath().resolve("data").resolve("storage.yml");
        Files.createDirectories(storage.getParent());
        Files.writeString(storage, onlyChestLinks(), StandardCharsets.UTF_8);
    }

    /**
     * The fixture without AutoCrafters (the live AutoCraft service resolves recipes, which MockBukkit can't) and with the unknown world
     * loaded (looking for its folder needs the world container, which MockBukkit doesn't have). {@link V2ImporterTest} covers both.
     */
    private static String onlyChestLinks() {
        String full = V2Fixtures.storage().replace("lost_world", "world");
        return full.substring(0, full.indexOf("  autocraftingtables:")) + full.substring(full.indexOf("  parties:"));
    }

    private V2Migration migration() {
        return services.get(V2Migration.class);
    }

    private void tickUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        assertThat(done.getAsBoolean()).as("condition reached within 10s").isTrue();
    }

    @Test
    void startupImportsBacksUpAndRenamesTheFileOnceSaved() throws Exception {
        migration().importOnStartup();

        assertThat(services.groups().find(GroupType.CHESTLINK, ALICE, "Iron Ore")).isNotNull();
        assertThat(services.get(MigrationState.class).filters()).isEqualTo(MigrationState.Filters.PENDING);
        Path migrated = storage.resolveSibling("storage.yml.v2-migrated");
        tickUntil(() -> Files.exists(migrated));
        assertThat(storage).doesNotExist();
        try (Stream<Path> files = Files.list(plugin.getDataFolder().toPath())) {
            List<String> names = files.map(path -> path.getFileName().toString()).toList();
            assertThat(names).anyMatch(name -> name.startsWith("v2-backup-"))
                    .anyMatch(name -> name.startsWith("v2-migration-") && name.endsWith(".log"));
        }

        int groups = services.groups().size();
        migration().importOnStartup();
        migration().importFile(server.getConsoleSender(), true, null);
        assertThat(services.groups().size()).isEqualTo(groups);
    }

    @Test
    void aPreviewOnlyReports() {
        PlayerMock admin = server.addPlayer();

        migration().importFile(admin, false, null);

        assertThat(services.groups().size()).isZero();
        assertThat(nextPlain(admin)).contains("v2 import preview: 4 ChestLink(s)");
        assertThat(storage).exists();
    }

    @Test
    void aFileOutsideTheDataFolderIsRefused() {
        PlayerMock admin = server.addPlayer();

        migration().importFile(admin, true, "../../secret.yml");

        assertThat(nextPlain(admin)).contains("No v2 data found");
        assertThat(services.groups().size()).isZero();
    }

    @Test
    void opsAreToldAboutPendingFiltersWhenTheyJoin() {
        services.get(MigrationState.class).setFilters(MigrationState.Filters.PENDING);
        PlayerMock player = server.addPlayer("Player");
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);

        server.getPluginManager().callEvent(new PlayerJoinEvent(admin, Component.empty()));

        assertThat(nextPlain(admin)).contains("Hopper filters from ChestsPlusPlus v2");
        assertThat(nextPlain(player)).isNull();
    }

    @Test
    void cleanupTakesFacingFromTheV2SignAndRemovesV2Leftovers() {
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        Block v2Sign = wallSign(table, BlockFace.EAST, "[ChestLink]");
        Block playerSign = wallSign(table, BlockFace.WEST, "Keep out");
        Block chest = world.getBlockAt(6, 64, 0);
        chest.setType(Material.CHEST);
        Directional facing = (Directional) chest.getBlockData();
        facing.setFacing(BlockFace.SOUTH);
        chest.setBlockData(facing);
        ArmorStand stand = world.spawn(table.getLocation(), ArmorStand.class);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "chestsplusplus"), PersistentDataType.INTEGER, 1);

        // The chunk is already loaded, so the import finishes it straight away.
        migration().importOnStartup();

        BlockPos tablePos = BlockPos.of(table);
        assertThat(services.nodes().get(tablePos).facing()).isEqualTo(BlockFace.EAST);
        assertThat(services.nodes().get(BlockPos.of(chest)).facing()).isEqualTo(BlockFace.SOUTH);
        assertThat(v2Sign.getType()).isEqualTo(Material.AIR);
        assertThat(playerSign.getType()).isEqualTo(Material.OAK_WALL_SIGN);
        assertThat(stand.isDead()).isTrue();
        assertThat(services.get(V2Cleanup.class).contains(tablePos)).isFalse();
    }

    @Test
    void cleanupSplitsALinkedDoubleChest() {
        Block left = world.getBlockAt(2, 64, 0);
        Block right = world.getBlockAt(3, 64, 0);
        // Facing north, a LEFT half's partner is to its east.
        doubleChestHalf(left, Chest.Type.LEFT);
        doubleChestHalf(right, Chest.Type.RIGHT);
        migration().importOnStartup();

        services.get(V2WorldCleanup.class).finish(left.getChunk());
        server.getPluginManager().callEvent(new EntitiesLoadEvent(left.getChunk(), List.of()));

        assertThat(((Chest) left.getBlockData()).getType()).isEqualTo(Chest.Type.SINGLE);
        assertThat(((Chest) right.getBlockData()).getType()).isEqualTo(Chest.Type.SINGLE);
        Node node = services.nodes().get(BlockPos.of(left));
        assertThat(node.facing()).isEqualTo(BlockFace.NORTH);
    }

    @Test
    void deletedItemsAndRevokedTrustAreNeverReimportedEvenFromRestoredYaml() throws Exception {
        migration().importOnStartup();
        tickUntil(() -> Files.exists(storage.resolveSibling("storage.yml.v2-migrated")));
        ChestLinkGroup ore = (ChestLinkGroup) services.groups().find(GroupType.CHESTLINK, ALICE, "Iron Ore");
        services.get(LinkService.class).removeGroup(ore, world.getBlockAt(0, 64, 0).getLocation());
        services.trust().untrust(ALICE, BOB);
        int dropped = world.getEntitiesByClass(Item.class).stream().mapToInt(item -> item.getItemStack().getAmount()).sum();
        assertThat(dropped).isEqualTo(5);
        Files.copy(storage.resolveSibling("storage.yml.v2-migrated"), storage);
        migration().importOnStartup();
        migration().importFile(server.getConsoleSender(), true, null);
        migration().importFile(server.getConsoleSender(), true, "data/storage.yml.v2-migrated");
        assertThat(services.groups().byId(ore.id())).isNull();
        assertThat(services.groups().find(GroupType.CHESTLINK, ALICE, "Iron Ore")).isNull();
        assertThat(services.trust().isTrusted(ALICE, BOB)).isFalse();
        assertThat(world.getEntitiesByClass(Item.class).stream().mapToInt(item -> item.getItemStack().getAmount()).sum()).isEqualTo(dropped);
    }

    @Test
    void renameFailureStillCommitsCompletionAndNeverReplays() throws Exception {
        Path destination = storage.resolveSibling("storage.yml.v2-migrated");
        Files.createDirectories(destination);
        Files.writeString(destination.resolve("keep"), "occupied");
        // Introduce the obstruction after startup's legacy-file check; importFile exercises the normal commit-then-rename path.
        migration().importFile(server.getConsoleSender(), true, null);
        tickUntil(() -> services.persistence().pending() == 0);
        assertThat(storage).exists();
        try (Database database = Database.open("jdbc:sqlite:" + plugin.getDataFolder().toPath().resolve("data.db"))) {
            assertThat(database.handle().createQuery("SELECT value FROM migration_state WHERE key = 'v2_import'").mapTo(String.class).one())
                    .isEqualTo("COMPLETE");
        }
        int count = services.groups().size();
        migration().importFile(server.getConsoleSender(), true, null);
        assertThat(services.groups().size()).isEqualTo(count);
    }

    @Test
    void migratedBackupsAreNotAcceptedEvenBeforeAnImport() throws IOException {
        Files.move(storage, storage.resolveSibling("storage.yml.v2-migrated"));
        PlayerMock admin = server.addPlayer();
        migration().importFile(admin, true, "data/storage.yml.v2-migrated");
        assertThat(nextPlain(admin)).contains("No v2 data found");
        migration().importFile(admin, false, null);
        assertThat(nextPlain(admin)).contains("No v2 data found");
        assertThat(services.groups().size()).isZero();
    }

    private ChestLinkGroup importUnresolved(String worldName, int x) {
        V2Importer importer = new V2Importer(services.groups(), services.nodes(), services.trust(), services.groupStore(),
                services.get(V2Cleanup.class),
                new WorldUids(server, plugin::getDataFolder), services.get(MigrationState.class), services.get(V2PendingLocations.class),
                services::settings, services.get(DisplayService.class)::nodeAdded);
        importer.run(new V2Data(List.of(new V2Data.Group(GroupType.CHESTLINK, ALICE, "Late World", false, List.of(), SortMode.OFF,
                new ItemStack[]{ItemStack.of(Material.DIAMOND, 5)}, null, List.of(new V2Data.Location(worldName, x, 64, 0)))), List.of(), List.of()),
                true);
        return (ChestLinkGroup) services.groups().find(GroupType.CHESTLINK, ALICE, "Late World");
    }

    @Test
    void unresolvedLocationsAttachToRenamedGroupsCurrentStateOnWorldLoad() throws Exception {
        ChestLinkGroup group = importUnresolved("later", 0);
        services.groups().rename(group, "Renamed");
        group.inventory().setItem(0, ItemStack.of(Material.COAL, 2));
        var saved = services.persistence().flush();
        tickUntil(saved::isDone);
        saved.join();
        World later = server.addSimpleWorld("later");
        Block block = later.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        later.loadChunk(0, 0);
        server.getPluginManager().callEvent(new WorldLoadEvent(later));
        assertThat(services.nodes().at(block).groupId()).isEqualTo(group.id());
        assertThat(group.inventory().getItem(0)).isEqualTo(ItemStack.of(Material.COAL, 2));
        assertThat(group.name()).isEqualTo("Renamed");
        assertThat(services.get(V2PendingLocations.class).size()).isZero();
        var attached = services.persistence().flush();
        tickUntil(attached::isDone);
        attached.join();
        try (Database database = Database.open("jdbc:sqlite:" + plugin.getDataFolder().toPath().resolve("data.db"))) {
            assertThat(database.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isZero();
            assertThat(database.handle().createQuery("SELECT count(*) FROM nodes WHERE group_id = :id").bind("id", group.id()).mapTo(int.class).one())
                    .isEqualTo(1);
        }
    }

    @Test
    void pendingRemovalAndAttachmentRetryAsOneTransaction() throws Exception {
        ChestLinkGroup group = importUnresolved("later", 0);
        var initial = services.persistence().flush();
        tickUntil(initial::isDone);
        initial.join();
        String url = "jdbc:sqlite:" + plugin.getDataFolder().toPath().resolve("data.db");
        try (Database database = Database.open(url)) {
            database.handle()
                    .execute("CREATE TRIGGER fail_pending BEFORE DELETE ON v2_pending_locations BEGIN SELECT RAISE(FAIL, 'simulated failure'); END");
            World later = server.addSimpleWorld("later");
            later.loadChunk(0, 0);
            Block block = later.getBlockAt(0, 64, 0);
            block.setType(Material.CHEST);
            services.get(V2LocationRecovery.class).finishLoaded();
            var failed = services.persistence().flush();
            tickUntil(failed::isDone);
            assertThat(failed.isCompletedExceptionally()).isTrue();
            assertThat(database.handle().createQuery("SELECT count(*) FROM nodes WHERE group_id = :id").bind("id", group.id()).mapTo(int.class).one())
                    .isZero();
            assertThat(database.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isEqualTo(1);
            database.handle().execute("DROP TRIGGER fail_pending");
            var retry = services.persistence().flush();
            tickUntil(retry::isDone);
            retry.join();
            assertThat(database.handle().createQuery("SELECT count(*) FROM nodes WHERE group_id = :id").bind("id", group.id()).mapTo(int.class).one())
                    .isEqualTo(1);
            assertThat(database.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isZero();
        }
    }

    @Test
    void deletedGroupAndSameNameReplacementCannotInheritPendingLocations() throws Exception {
        ChestLinkGroup group = importUnresolved("later", 0);
        var initial = services.persistence().flush();
        tickUntil(initial::isDone);
        services.get(LinkService.class).removeGroup(group, world.getBlockAt(0, 64, 0).getLocation());
        StorageGroup replacement = new ChestLinkGroup(services.groups().nextId(), ALICE, "Late World", 0);
        services.groups().add(replacement);
        services.groupStore().markDirty(replacement);
        World later = server.addSimpleWorld("later");
        later.getBlockAt(0, 64, 0).setType(Material.CHEST);
        server.getPluginManager().callEvent(new WorldLoadEvent(later));
        assertThat(services.nodes().nodesOf(replacement.id())).isEmpty();
        assertThat(services.get(V2PendingLocations.class).size()).isZero();
        var deleted = services.persistence().flush();
        tickUntil(deleted::isDone);
        deleted.join();
        try (Database database = Database.open("jdbc:sqlite:" + plugin.getDataFolder().toPath().resolve("data.db"))) {
            assertThat(database.handle().createQuery("SELECT count(*) FROM v2_pending_locations").mapTo(int.class).one()).isZero();
        }
    }

    @Test
    void stalePendingLocationIsDiscardedPermanentlyOnChunkLoad() {
        ChestLinkGroup group = importUnresolved("later", 32);
        World later = server.addSimpleWorld("later");
        Block block = later.getBlockAt(32, 64, 0);
        block.setType(Material.STONE);
        services.get(V2LocationRecovery.class).onChunkLoad(new ChunkLoadEvent(block.getChunk(), false));
        assertThat(services.get(V2PendingLocations.class).size()).isZero();
        block.setType(Material.CHEST);
        services.get(V2LocationRecovery.class).onChunkLoad(new ChunkLoadEvent(block.getChunk(), false));
        assertThat(services.nodes().nodesOf(group.id())).isEmpty();
    }

    @Test
    void occupiedPendingLocationIsDiscardedWithoutTouchingTheNewOwner() {
        ChestLinkGroup group = importUnresolved("later", 0);
        World later = server.addSimpleWorld("later");
        Block block = later.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        ChestLinkGroup other = new ChestLinkGroup(services.groups().nextId(), BOB, "New Owner", 0);
        services.groups().add(other);
        services.nodes().put(new Node(BlockPos.of(block), BlockFace.EAST, other.id()));
        services.get(V2LocationRecovery.class).onChunkLoad(new ChunkLoadEvent(block.getChunk(), false));
        assertThat(services.nodes().at(block).groupId()).isEqualTo(other.id());
        assertThat(services.get(V2PendingLocations.class).size()).isZero();
        services.nodes().remove(BlockPos.of(block));
        services.get(V2LocationRecovery.class).finishLoaded();
        assertThat(services.nodes().nodesOf(group.id())).isEmpty();
    }

    private static void doubleChestHalf(Block block, Chest.Type type) {
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        data.setType(type);
        block.setBlockData(data);
    }

    private static Block wallSign(Block on, BlockFace side, String firstLine) {
        Block block = on.getRelative(side);
        block.setType(Material.OAK_WALL_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).line(0, Component.text(firstLine));
        sign.update();
        // MockBukkit's update() resets the block data, so the facing goes on afterwards.
        WallSign data = (WallSign) block.getBlockData();
        data.setFacing(side);
        block.setBlockData(data);
        return block;
    }
}
