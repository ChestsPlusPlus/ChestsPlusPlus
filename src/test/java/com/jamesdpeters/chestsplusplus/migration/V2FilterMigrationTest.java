package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Match;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Mode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.List;
import org.bukkit.Material;
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
        ItemFrame frame = world.spawn(hopper.getRelative(side).getLocation(), ItemFrame.class);
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
        MigrationState state = plugin.services().get(MigrationState.class);
        state.setFilters(MigrationState.Filters.PENDING);

        entitiesLoad(frames);
        assertThat(frames).noneMatch(ItemFrame::isDead);

        state.setFilters(MigrationState.Filters.ON_LOAD);
        entitiesLoad(frames);

        assertThat(plugin.services().get(FilterService.class).read(hopper)).containsExactly(
                new HopperFilter(ItemStack.of(Material.COAL), Mode.ALLOW, Match.EXACT),
                new HopperFilter(ItemStack.of(Material.DIRT), Mode.DENY, Match.EXACT));
        assertThat(frames).allMatch(ItemFrame::isDead);
        assertThat(world.getEntitiesByClass(Item.class)).extracting(item -> item.getItemStack().getType())
                .containsExactlyInAnyOrder(Material.COAL, Material.DIRT, Material.ITEM_FRAME, Material.ITEM_FRAME);
    }

    @Test
    void aChunkIsScannedOnlyOnce() {
        plugin.services().get(MigrationState.class).setFilters(MigrationState.Filters.ON_LOAD);
        entitiesLoad(List.of(frame(BlockFace.NORTH, Material.COAL, Rotation.NONE)));

        ItemFrame later = frame(BlockFace.SOUTH, Material.DIRT, Rotation.NONE);
        entitiesLoad(List.of(later));

        assertThat(later.isDead()).isFalse();
        assertThat(plugin.services().get(FilterService.class).read(hopper)).hasSize(1);
    }
}
