package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.display.MarkerDisplays;
import com.jamesdpeters.chestsplusplus.filter.FilterCodec;
import com.jamesdpeters.chestsplusplus.filter.FilterDisplays;
import com.jamesdpeters.chestsplusplus.filter.FilterIndex;
import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Match;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Mode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V2FilterMigrationTest extends PluginTestBase {

    private World world;
    private Block hopper;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        hopper = world.getBlockAt(1, 64, 1);
        hopper.setType(Material.HOPPER);
    }

    private ItemFrame frame(BlockFace side, Material item, Rotation rotation) {
        return frame(hopper, side, item, rotation);
    }

    private ItemFrame frame(Block on, BlockFace side, Material item, Rotation rotation) {
        ItemFrame frame = world.spawn(on.getRelative(side).getLocation(), ItemFrame.class);
        frame.setFacingDirection(side, true);
        frame.setItem(ItemStack.of(item));
        frame.setRotation(rotation);
        return frame;
    }

    private void entitiesLoad(List<ItemFrame> frames) {
        server.getPluginManager().callEvent(new EntitiesLoadEvent(hopper.getChunk(), List.copyOf(frames)));
    }

    @Test
    void rotationChoosesModeAndMatchAsInV2() {
        ItemStack coal = ItemStack.of(Material.COAL);

        assertThat(V2FilterMigration.filter(coal, Rotation.NONE)).isEqualTo(new HopperFilter(coal, Mode.ALLOW, Match.EXACT));
        assertThat(V2FilterMigration.filter(coal, Rotation.CLOCKWISE_45)).isEqualTo(new HopperFilter(coal, Mode.ALLOW, Match.EXACT));
        assertThat(V2FilterMigration.filter(coal, Rotation.FLIPPED)).isEqualTo(new HopperFilter(coal, Mode.ALLOW, Match.SIMILAR));
        assertThat(V2FilterMigration.filter(coal, Rotation.CLOCKWISE)).isEqualTo(new HopperFilter(coal, Mode.DENY, Match.EXACT));
        assertThat(V2FilterMigration.filter(coal, Rotation.COUNTER_CLOCKWISE)).isEqualTo(new HopperFilter(coal, Mode.DENY, Match.SIMILAR));
    }

    @Test
    void framesConvertOnlyOnceTheAdminHasChosenTo() {
        List<ItemFrame> frames = List.of(frame(BlockFace.NORTH, Material.COAL, Rotation.NONE),
                frame(BlockFace.EAST, Material.DIRT, Rotation.CLOCKWISE));
        MigrationState state = migration().models().state();
        state.setFilters(MigrationState.Filters.PENDING);

        entitiesLoad(frames);
        assertThat(frames).noneMatch(ItemFrame::isDead);

        state.setFilters(MigrationState.Filters.ON_LOAD);
        entitiesLoad(frames);

        assertThat(features().filters().read(hopper)).containsExactly(
                new HopperFilter(ItemStack.of(Material.COAL), Mode.ALLOW, Match.EXACT),
                new HopperFilter(ItemStack.of(Material.DIRT), Mode.DENY, Match.EXACT));
        assertThat(frames).allMatch(ItemFrame::isDead);
        assertThat(world.getEntitiesByClass(Item.class)).extracting(item -> item.getItemStack().getType())
                .containsExactlyInAnyOrder(Material.COAL, Material.DIRT, Material.ITEM_FRAME, Material.ITEM_FRAME);
    }

    @Test
    void aChunkIsScannedOnlyOnce() {
        migration().models().state().setFilters(MigrationState.Filters.ON_LOAD);
        entitiesLoad(List.of(frame(BlockFace.NORTH, Material.COAL, Rotation.NONE)));

        ItemFrame later = frame(BlockFace.SOUTH, Material.DIRT, Rotation.NONE);
        entitiesLoad(List.of(later));

        assertThat(later.isDead()).isFalse();
        assertThat(features().filters().read(hopper)).hasSize(1);
    }

    @Test
    void aFailedHopperKeepsItsFramesForARetry() {
        Block second = world.getBlockAt(5, 64, 1);
        second.setType(Material.HOPPER);
        FilterService real = features().filters();
        AtomicBoolean failing = new AtomicBoolean(true);
        Supplier<Settings> settings = () -> {
            if (failing.get() && !real.read(second).isEmpty()) throw new IllegalStateException("injected after the second hopper's write");
            return services().settings();
        };
        FilterService filters = new FilterService(plugin, new FilterCodec(plugin), real.grouping(), settings, new FilterIndex(),
                new FilterDisplays(new MarkerDisplays(plugin, "filter_display"), settings));
        V2FilterMigration migration = new V2FilterMigration(migration().models().state(), filters, new NamespacedKey(plugin, "test"));
        ItemFrame first = frame(BlockFace.NORTH, Material.COAL, Rotation.NONE);
        ItemFrame pending = frame(second, BlockFace.NORTH, Material.DIRT, Rotation.CLOCKWISE);

        assertThatThrownBy(() -> migration.convert(hopper.getChunk(), List.of(first, pending))).hasMessageContaining("injected");
        assertThat(first.isDead()).isTrue();
        assertThat(pending.isDead()).isFalse();

        failing.set(false);
        migration.convert(hopper.getChunk(), List.of(pending));

        assertThat(pending.isDead()).isTrue();
        assertThat(real.read(hopper)).containsExactly(new HopperFilter(ItemStack.of(Material.COAL), Mode.ALLOW, Match.EXACT));
        assertThat(real.read(second)).containsExactly(new HopperFilter(ItemStack.of(Material.DIRT), Mode.DENY, Match.EXACT));
    }
}
