package com.jamesdpeters.chestsplusplus.autocraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AutoCraftIntegrationTest extends PluginTestBase {

    /** Torch: coal directly above a stick, anywhere in the grid → 4 torches. MockBukkit has no recipe matching. */
    static final class TorchBackend implements CraftingBackend {
        @Override
        public @Nullable ResolvedRecipe resolve(@Nullable ItemStack[] matrix, World world) {
            int coal = -1;
            int count = 0;
            for (int i = 0; i < 9; i++) {
                if (matrix[i] == null) continue;
                count++;
                if (matrix[i].getType() == Material.COAL) coal = i;
            }
            if (count != 2
                    || coal < 0
                    || coal + 3 > 8
                    || matrix[coal + 3] == null
                    || matrix[coal + 3].getType() != Material.STICK) {
                return null;
            }
            List<@Nullable Predicate<ItemStack>> slots = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                Material type = matrix[i] == null ? null : matrix[i].getType();
                slots.add(type == null ? null : item -> item.getType() == type);
            }
            return new ResolvedRecipe(NamespacedKey.minecraft("torch"), ItemStack.of(Material.TORCH, 4), slots);
        }

        @Override
        public @Nullable Crafted craft(@Nullable ItemStack[] matrix, World world) {
            ResolvedRecipe recipe = resolve(matrix, world);
            return recipe == null ? null : new Crafted(recipe.result(), new ItemStack[9]);
        }
    }

    private World world;
    private PlayerMock alice;
    private AutoCraftService autoCraft;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        alice = server.addPlayer("Alice");
        autoCraft = new AutoCraftService(
                plugin.services(), plugin.services().get(DisplayService.class), new TorchBackend());
    }

    private static @Nullable ItemStack[] torchMatrix() {
        @Nullable ItemStack[] matrix = new ItemStack[9];
        matrix[1] = ItemStack.of(Material.COAL);
        matrix[4] = ItemStack.of(Material.STICK);
        return matrix;
    }

    /** Crafting table at (0,64,0) with a group and the torch recipe; returns the table. */
    private Block crafter(AutoCraftGroup[] out) {
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        LinkService links = plugin.services().get(LinkService.class);
        links.link(alice, GroupType.AUTOCRAFT, "torches", table, BlockFace.NORTH, true);
        AutoCraftGroup group =
                (AutoCraftGroup) plugin.services().groups().find(GroupType.AUTOCRAFT, alice.getUniqueId(), "torches");
        autoCraft.setMatrix(group, torchMatrix(), null);
        out[0] = group;
        return table;
    }

    private Inventory container(Block block, Material type) {
        block.setType(type);
        return ((Container) block.getState(false)).getInventory();
    }

    @Test
    void plannerRespectsStackCountsAndCapacity() {
        Inventory input = container(world.getBlockAt(5, 64, 5), Material.CHEST);
        input.setItem(0, ItemStack.of(Material.COAL, 1));
        Predicate<ItemStack> coal = item -> item.getType() == Material.COAL;
        List<@Nullable Predicate<ItemStack>> twoCoal = new ArrayList<>(java.util.Collections.nCopies(9, null));
        twoCoal.set(0, coal);
        twoCoal.set(1, coal);

        assertThat(CraftPlanner.plan(twoCoal, List.of(input))).isNull();
        input.setItem(5, ItemStack.of(Material.COAL, 1));
        assertThat(CraftPlanner.plan(twoCoal, List.of(input))).isNotNull();

        Inventory output = container(world.getBlockAt(6, 64, 5), Material.HOPPER);
        for (int i = 0; i < 5; i++) output.setItem(i, ItemStack.of(Material.TORCH, 62));
        assertThat(CraftPlanner.fits(output, List.of(ItemStack.of(Material.TORCH, 4))))
                .isTrue();
        assertThat(CraftPlanner.fits(output, List.of(ItemStack.of(Material.TORCH, 11))))
                .isFalse();
    }

    @Test
    void craftsFromAboveIntoHopperBelow() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory input = container(table.getRelative(BlockFace.UP), Material.CHEST);
        input.addItem(ItemStack.of(Material.COAL, 2), ItemStack.of(Material.STICK, 2));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);

        Node node = plugin.services().nodes().get(BlockPos.of(table));
        assertThat(autoCraft.craftAt(group[0], node)).isTrue();
        assertThat(autoCraft.craftAt(group[0], node)).isTrue();
        assertThat(autoCraft.craftAt(group[0], node)).isFalse();

        assertThat(output.contains(Material.TORCH, 8)).isTrue();
        assertThat(input.contains(Material.COAL)).isFalse();
        assertThat(group[0].result()).isEqualTo(ItemStack.of(Material.TORCH, 4));
    }

    @Test
    void lockedHopperStopsCrafting() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        container(table.getRelative(BlockFace.UP), Material.CHEST)
                .addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Block below = table.getRelative(BlockFace.DOWN);
        container(below, Material.HOPPER);
        Hopper data = (Hopper) below.getBlockData();
        data.setEnabled(false);
        below.setBlockData(data);
        Node node = plugin.services().nodes().get(BlockPos.of(table));

        assertThat(autoCraft.craftAt(group[0], node)).isFalse();
        // The "container below crafts only while powered" rule needs redstone, which MockBukkit doesn't implement;
        // it is covered by the AutoCraft E2E test.
    }

    @Test
    void usesAccessibleChestLinkInputs() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Block side = table.getRelative(BlockFace.EAST);
        side.setType(Material.CHEST);
        plugin.services().get(LinkService.class).link(alice, GroupType.CHESTLINK, "mats", side, BlockFace.EAST, true);
        ChestLinkGroup mats =
                (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, alice.getUniqueId(), "mats");
        mats.inventory().addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table))))
                .isTrue();
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
        assertThat(mats.inventory().isEmpty()).isTrue();
        assertThat(plugin.services().persistence().isDirty(mats)).isTrue();
    }

    @Test
    void failuresBackOffUntilInputsChange() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Block above = table.getRelative(BlockFace.UP);
        container(above, Material.CHEST);
        container(table.getRelative(BlockFace.DOWN), Material.HOPPER);

        autoCraft.tick(20);
        assertThat(autoCraft.isBackingOff(BlockPos.of(table))).isTrue();

        autoCraft.inputChanged(above);
        assertThat(autoCraft.isBackingOff(BlockPos.of(table))).isFalse();
    }

    @Test
    void editorClicksPlaceGhostItemsAndResolveTheRecipe() {
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        plugin.services().get(LinkService.class).link(alice, GroupType.AUTOCRAFT, "t", table, BlockFace.NORTH, true);
        AutoCraftGroup group =
                (AutoCraftGroup) plugin.services().groups().find(GroupType.AUTOCRAFT, alice.getUniqueId(), "t");
        RecipeEditorHolder editor = new RecipeEditorHolder(group, net.kyori.adventure.text.Component.text("t"));

        ItemStack[] matrix = editor.click(2, ItemStack.of(Material.COAL, 64));
        autoCraft.setMatrix(group, matrix, null);
        matrix = editor.click(5, ItemStack.of(Material.STICK, 3));
        autoCraft.setMatrix(group, matrix, null);
        editor.render();

        assertThat(editor.getInventory().getItem(RecipeEditorHolder.RESULT_SLOT))
                .isEqualTo(ItemStack.of(Material.TORCH, 4));
        assertThat(editor.getInventory().getItem(2)).isEqualTo(ItemStack.of(Material.COAL));
        assertThat(group.recipeKey()).isEqualTo(NamespacedKey.minecraft("torch"));
        assertThat(plugin.services().persistence().isDirty(group)).isTrue();
        assertThat(editor.click(9, null)).isNull();
    }
}
