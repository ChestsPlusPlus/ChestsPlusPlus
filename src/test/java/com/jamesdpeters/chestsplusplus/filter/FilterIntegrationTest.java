package com.jamesdpeters.chestsplusplus.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Match;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Mode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FilterIntegrationTest extends PluginTestBase {

    private final ItemGrouping grouping = new ItemGrouping(List.of(Set.of(Material.OAK_LOG, Material.BIRCH_LOG)));
    private World world;
    private FilterService filters;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        filters = plugin.services().get(FilterService.class);
    }

    private static HopperFilter filter(Material material, Mode mode, Match match) {
        return new HopperFilter(ItemStack.of(material), mode, match);
    }

    private Block hopperAt(int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.HOPPER);
        return block;
    }

    private Inventory inventoryOf(Block block) {
        return ((Container) block.getState(false)).getInventory();
    }

    @Test
    void semanticsDenyWinsAllowRestrictsEmptyPasses() {
        assertThat(new CompiledFilter(List.of(), grouping).accepts(ItemStack.of(Material.DIRT)))
                .isTrue();

        CompiledFilter allowStone =
                new CompiledFilter(List.of(filter(Material.STONE, Mode.ALLOW, Match.TYPE)), grouping);
        assertThat(allowStone.accepts(ItemStack.of(Material.STONE, 5))).isTrue();
        assertThat(allowStone.accepts(ItemStack.of(Material.DIRT))).isFalse();

        CompiledFilter both = new CompiledFilter(
                List.of(filter(Material.STONE, Mode.ALLOW, Match.TYPE), filter(Material.STONE, Mode.DENY, Match.TYPE)),
                grouping);
        assertThat(both.accepts(ItemStack.of(Material.STONE))).isFalse();

        CompiledFilter denyLogs =
                new CompiledFilter(List.of(filter(Material.OAK_LOG, Mode.DENY, Match.SIMILAR)), grouping);
        assertThat(denyLogs.accepts(ItemStack.of(Material.BIRCH_LOG))).isFalse();
        assertThat(denyLogs.accepts(ItemStack.of(Material.DIRT))).isTrue();
    }

    @Test
    void exactMatchesComponentsTypeDoesNot() {
        ItemStack named = ItemStack.of(Material.DIAMOND);
        named.editMeta(meta -> meta.displayName(Component.text("special")));
        CompiledFilter exact = new CompiledFilter(List.of(new HopperFilter(named, Mode.ALLOW, Match.EXACT)), grouping);
        CompiledFilter type = new CompiledFilter(List.of(new HopperFilter(named, Mode.ALLOW, Match.TYPE)), grouping);

        assertThat(exact.accepts(named.asQuantity(3))).isTrue();
        assertThat(exact.accepts(ItemStack.of(Material.DIAMOND))).isFalse();
        assertThat(type.accepts(ItemStack.of(Material.DIAMOND))).isTrue();
    }

    @Test
    void filtersRoundTripThroughHopperPdcAndIndex() {
        Block hopper = hopperAt(0);
        List<HopperFilter> stored = List.of(
                filter(Material.STONE, Mode.ALLOW, Match.EXACT), filter(Material.OAK_LOG, Mode.DENY, Match.SIMILAR));

        filters.write(hopper, stored);

        assertThat(filters.read(hopper)).isEqualTo(stored);
        assertThat(filters.get(hopper)).isNotNull();
        FilterCodec codec = new FilterCodec(plugin);
        assertThat(codec.read(((Hopper) hopper.getState(false)).getPersistentDataContainer()))
                .isEqualTo(stored);
        assertThat(filters.displayCount()).isEqualTo(2);

        filters.write(hopper, List.of());
        assertThat(filters.get(hopper)).isNull();
        assertThat(filters.displayCount()).isZero();
    }

    @Test
    void rejectedItemsAreCancelledAndTheNextAcceptableOneMoves() {
        Block hopper = hopperAt(0);
        filters.write(hopper, List.of(filter(Material.STONE, Mode.ALLOW, Match.TYPE)));
        Block chest = world.getBlockAt(0, 65, 0);
        chest.setType(Material.CHEST);
        Inventory source = inventoryOf(chest);
        source.setItem(0, ItemStack.of(Material.DIRT, 5));
        source.setItem(1, ItemStack.of(Material.STONE, 5));

        Inventory destination = inventoryOf(hopper);
        InventoryMoveItemEvent event =
                new InventoryMoveItemEvent(source, ItemStack.of(Material.DIRT), destination, false);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        // Stall avoidance moved one stone instead.
        assertThat(destination.contains(Material.STONE, 1)).isTrue();
        assertThat(source.getItem(1).getAmount()).isEqualTo(4);
        assertThat(source.getItem(0).getAmount()).isEqualTo(5);

        InventoryMoveItemEvent accepted =
                new InventoryMoveItemEvent(source, ItemStack.of(Material.STONE), destination, false);
        server.getPluginManager().callEvent(accepted);
        assertThat(accepted.isCancelled()).isFalse();
    }

    @Test
    void editorAddsCyclesRemovesAndClears() {
        Block hopper = hopperAt(0);
        FilterEditorHolder editor =
                new FilterEditorHolder(hopper, List.of(), plugin.services().messages());

        assertThat(editor.click(0, ItemStack.of(Material.STONE, 32), false)).isTrue();
        assertThat(editor.click(9, ItemStack.of(Material.DIRT), false)).isTrue();
        assertThat(editor.filters())
                .containsExactly(
                        filter(Material.STONE, Mode.ALLOW, Match.EXACT), filter(Material.DIRT, Mode.DENY, Match.EXACT));

        editor.click(0, null, false);
        editor.click(0, null, false);
        assertThat(editor.filters().getFirst().match()).isEqualTo(Match.SIMILAR);
        assertThat(editor.getInventory().getItem(0).getType()).isEqualTo(Material.STONE);

        assertThat(editor.click(9, null, true)).isTrue();
        assertThat(editor.filters()).hasSize(1);
        assertThat(editor.click(5, null, false)).isFalse();
        assertThat(editor.click(FilterEditorHolder.CLEAR_SLOT, null, false)).isTrue();
        assertThat(editor.filters()).isEmpty();
    }

    @Test
    void sneakClickWithEmptyHandOpensTheEditor() {
        Block hopper = hopperAt(0);
        PlayerMock player = server.addPlayer();
        player.setSneaking(true);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, hopper, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        assertThat(event.useInteractedBlock()).isEqualTo(org.bukkit.event.Event.Result.DENY);
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(FilterEditorHolder.class);
    }

    @Test
    void breakingAHopperDropsItsIndexEntryAndDisplays() {
        Block hopper = hopperAt(0);
        filters.write(hopper, List.of(filter(Material.STONE, Mode.ALLOW, Match.TYPE)));

        server.getPluginManager().callEvent(new BlockBreakEvent(hopper, server.addPlayer()));

        assertThat(filters.get(hopper)).isNull();
        assertThat(filters.displayCount()).isZero();
    }
}
