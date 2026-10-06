package com.jamesdpeters.chestsplusplus.migration;

import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.ALICE;
import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
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

        assertThat(services.groups().find(GroupType.CHESTLINK, ALICE, "Iron_Ore")).isNotNull();
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
