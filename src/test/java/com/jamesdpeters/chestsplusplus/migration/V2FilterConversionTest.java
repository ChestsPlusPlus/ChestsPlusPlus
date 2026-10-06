package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Paper's asynchronous chunks and tickets aren't implemented by MockBukkit; delegate the rest to real mocks. */
class V2FilterConversionTest extends PluginTestBase {

    @TempDir Path dir;
    private Services services;
    private World world;
    private Chunk chunk;
    private Chunk backing;
    private V2FilterConversion conversion;
    private MigrationState state;
    private CompletableFuture<Chunk> load;
    private boolean entitiesLoaded;
    private int tickets;
    private int entityReads;
    private ItemFrame frame;
    private Block hopper;
    private PlayerMock sender;

    @BeforeEach
    void setUp() throws Exception {
        services = plugin.services();
        state = services.get(MigrationState.class);
        World actual = server.addSimpleWorld("world");
        actual.loadChunk(0, 0);
        sender = server.addPlayer();
        backing = actual.getChunkAt(0, 0);
        hopper = actual.getBlockAt(1, 64, 1);
        hopper.setType(Material.HOPPER);
        frame = actual.spawn(hopper.getRelative(BlockFace.NORTH).getLocation(), ItemFrame.class);
        frame.setFacingDirection(BlockFace.NORTH, true);
        frame.setItem(ItemStack.of(Material.COAL));
        world = World.class.cast(
                Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (_, method, args) -> switch (method.getName()) {
                    case "getWorldFolder" -> dir.toFile();
                    case "getLoadedChunks" -> new Chunk[]{chunk};
                    case "getChunkAtAsync" -> load;
                    case "equals" -> args[0] == world;
                    case "hashCode" -> 1;
                    default -> invoke(actual, method, args);
                }));
        chunk = Chunk.class.cast(
                Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[]{Chunk.class}, (_, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "isEntitiesLoaded" -> entitiesLoaded;
                    case "getEntities" -> entities();
                    case "addPluginChunkTicket" -> {
                        tickets++;
                        yield true;
                    }
                    case "removePluginChunkTicket" -> {
                        tickets--;
                        yield true;
                    }
                    default -> invoke(backing, method, args);
                }));
        load = CompletableFuture.completedFuture(chunk);
        Files.createDirectories(dir.resolve("entities"));
        ByteBuffer header = ByteBuffer.allocate(8192);
        header.putInt(0, 2 << 8 | 1);
        Files.write(dir.resolve("entities/r.0.0.mca"), header.array());
        conversion = new V2FilterConversion(plugin, services, state, services.get(V2FilterMigration.class), services.get(V2Cleanup.class),
                services.get(V2WorldCleanup.class), services.get(V2PendingLocations.class));
    }

    private Entity[] entities() {
        assertThat(entitiesLoaded).as("must never force synchronous entity loading").isTrue();
        entityReads++;
        return backing.getEntities();
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void start(boolean allWorlds) throws InterruptedException {
        conversion.start(sender, List.of(world), allWorlds);
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String message = null;
        while (message == null && System.nanoTime() < deadline) {
            server.getScheduler().performOneTick();
            message = nextPlain(sender);
            Thread.sleep(5);
        }
        assertThat(message).contains("Converting v2 hopper filters");
        conversion.tick();
    }

    private void finish() {
        for (int i = 0; i < 205 && conversion.isRunning(); i++) conversion.tick();
        assertThat(conversion.isRunning()).isFalse();
        assertThat(tickets).isZero();
        while (nextPlain(sender) != null) {}
    }

    @Test
    void singleWorldSuccessLeavesOnLoadWhileGlobalSuccessFinishes() throws Exception {
        entitiesLoaded = true;
        start(false);
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
        assertThat(services.get(FilterService.class).read(hopper)).hasSize(1);
        start(true);
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.DONE);
        assertThat(services.get(FilterService.class).read(hopper)).hasSize(1);
        assertThat(hopper.getWorld().getEntitiesByClass(Item.class)).hasSize(2);
    }

    @Test
    void entityTimeoutDefersWithoutReadingOrMarkingAndTheLoadEventConvertsOnce() throws Exception {
        start(true);
        finish();
        assertThat(entityReads).isZero();
        assertThat(frame.isDead()).isFalse();
        assertThat(backing.getPersistentDataContainer().has(new NamespacedKey(plugin, "v2_filters_scanned"))).isFalse();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
        entitiesLoaded = true;
        services.get(V2FilterMigration.class).onEntitiesLoad(new EntitiesLoadEvent(chunk, List.of(frame)));
        start(true);
        finish();
        assertThat(frame.isDead()).isTrue();
        assertThat(services.get(FilterService.class).read(hopper)).hasSize(1);
        assertThat(hopper.getWorld().getEntitiesByClass(Item.class)).hasSize(2);
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.DONE);
    }

    @Test
    void delayedEntitiesCanFinishBeforeTimeout() throws Exception {
        start(true);
        for (int i = 0; i < 20; i++) conversion.tick();
        assertThat(entityReads).isZero();
        assertThat(tickets).isEqualTo(1);
        entitiesLoaded = true;
        finish();
        assertThat(frame.isDead()).isTrue();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.DONE);
    }

    @Test
    void failedChunkLoadRetainsOnLoadAndCanBeRetried() throws Exception {
        load = CompletableFuture.failedFuture(new IllegalStateException("load failed"));
        start(true);
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
        assertThat(entityReads).isZero();
        load = CompletableFuture.completedFuture(chunk);
        entitiesLoaded = true;
        start(true);
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.DONE);
    }

    @Test
    void cancellingReleasesCurrentAndLaterTicketsAndKeepsOnLoad() throws Exception {
        start(true);
        assertThat(tickets).isEqualTo(1);
        conversion.cancel(server.getConsoleSender());
        assertThat(tickets).isZero();
        load = new CompletableFuture<>();
        start(true);
        conversion.cancel(server.getConsoleSender());
        load.complete(chunk);
        assertThat(tickets).isZero();
        assertThat(conversion.isRunning()).isFalse();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
    }

    @Test
    void unresolvedWorldsPreventGlobalCompletion() throws Exception {
        entitiesLoaded = true;
        services.get(V2PendingLocations.class).add(new V2PendingLocations.LocationRow(1, "missing", 0, 64, 0));
        start(true);
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
    }
    @Test
    void worldLoadedDuringTheRunPreventsGlobalCompletion() throws Exception {
        start(true);
        server.addSimpleWorld("later");
        entitiesLoaded = true;
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
    }

    @Test
    void worldsMissingAtScanStartPreventCompletionEvenIfPendingRowsDisappear() throws Exception {
        V2PendingLocations pending = services.get(V2PendingLocations.class);
        var row = new V2PendingLocations.LocationRow(1, "missing", 0, 64, 0);
        pending.add(row);
        start(true);
        pending.remove(row);
        entitiesLoaded = true;
        finish();
        assertThat(state.filters()).isEqualTo(MigrationState.Filters.ON_LOAD);
    }

}
