package com.jamesdpeters.chestsplusplus.autocraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Match;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter.Mode;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.SyntheticMoveEvent;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AutoCraftIntegrationTest extends PluginTestBase {

    /**
     * Torch: coal directly above a stick, anywhere in the grid → 4 torches. MockBukkit has no recipe matching. A {@code special} torch
     * exposes no recipe choices, like vanilla's special recipes.
     */
    static final class TorchBackend implements CraftingBackend {
        private final boolean special;

        TorchBackend(boolean special) {
            this.special = special;
        }

        @Override
        public @Nullable ResolvedRecipe resolve(@Nullable ItemStack[] matrix, World world) {
            int coal = -1;
            int count = 0;
            for (int i = 0; i < 9; i++) {
                if (matrix[i] == null) continue;
                count++;
                if (matrix[i].getType() == Material.COAL) coal = i;
            }
            if (count != 2 || coal < 0 || coal + 3 > 8 || matrix[coal + 3] == null || matrix[coal + 3].getType() != Material.STICK) {
                return null;
            }
            List<@Nullable Predicate<ItemStack>> slots = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                Material type = matrix[i] == null ? null : matrix[i].getType();
                slots.add(type == null || special ? null : item -> item.getType() == type);
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
        autoCraft = autoCraft(false);
    }

    private AutoCraftService autoCraft(boolean special) {
        return new AutoCraftService(plugin.services(), plugin.services().get(DisplayService.class), new TorchBackend(special));
    }

    private static ItemStack namedCoal() {
        ItemStack coal = ItemStack.of(Material.COAL);
        coal.editMeta(meta -> meta.displayName(Component.text("Lump")));
        return coal;
    }

    private RecipeEditorHolder editor(AutoCraftGroup group) {
        return new RecipeEditorHolder(group, Component.text(group.name()), autoCraft, plugin.services().messages());
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
        AutoCraftGroup group = (AutoCraftGroup) plugin.services().groups().find(GroupType.AUTOCRAFT, alice.getUniqueId(), "torches");
        autoCraft.setMatrix(group, torchMatrix(), null);
        out[0] = group;
        return table;
    }

    private Inventory container(Block block, Material type) {
        block.setType(type);
        return ((Container) block.getState(false)).getInventory();
    }

    @Test
    void namedTagOnCraftingTableCreatesAutoCraft() {
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        ItemStack tag = ItemStack.of(Material.NAME_TAG);
        tag.setData(DataComponentTypes.CUSTOM_NAME, Component.text("torches"));
        alice.getInventory().setItemInMainHand(tag);

        server.getPluginManager()
                .callEvent(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, tag, table, BlockFace.NORTH, EquipmentSlot.HAND));

        assertThat(plugin.services().groups().find(GroupType.AUTOCRAFT, alice.getUniqueId(), "torches")).isNotNull();
        assertThat(plugin.services().nodes().get(BlockPos.of(table))).isNotNull();
        assertThat(tag.isEmpty()).isTrue();
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
        assertThat(CraftPlanner.fits(output, List.of(ItemStack.of(Material.TORCH, 4)))).isTrue();
        assertThat(CraftPlanner.fits(output, List.of(ItemStack.of(Material.TORCH, 11)))).isFalse();
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
        container(table.getRelative(BlockFace.UP), Material.CHEST).addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
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
        ChestLinkGroup mats = (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, alice.getUniqueId(), "mats");
        mats.inventory().addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table)))).isTrue();
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
        assertThat(mats.inventory().isEmpty()).isTrue();
        assertThat(plugin.services().groupStore().isDirty(mats)).isTrue();
    }

    @Test
    void settingTheRecipeCraftsOnTheNextTickThenOncePerInterval() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        container(table.getRelative(BlockFace.UP), Material.CHEST).addItem(ItemStack.of(Material.COAL, 2), ItemStack.of(Material.STICK, 2));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);

        ticks(1);
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
        ticks(19);
        assertThat(output.contains(Material.TORCH, 8)).isFalse();
        ticks(1);
        assertThat(output.contains(Material.TORCH, 8)).isTrue();
    }

    @Test
    void aFailingCrafterBacksOffUntilItsInputChanges() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory input = container(table.getRelative(BlockFace.UP), Material.CHEST);
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);

        ticks(1);
        assertThat(autoCraft.isBackingOff(BlockPos.of(table))).isTrue();
        input.addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        ticks(5);
        assertThat(output.isEmpty()).isTrue();

        autoCraft.inventoryChanged(input);
        ticks(1);
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
        assertThat(autoCraft.isBackingOff(BlockPos.of(table))).isFalse();
    }

    @Test
    void clickingItemsIntoAnOpenInputChestWakesAWaitingCrafter() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory input = container(table.getRelative(BlockFace.UP), Material.CHEST);
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        ticks(1);

        InventoryView view = alice.openInventory(input);
        input.addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        AutoCraftListener listener = new AutoCraftListener(plugin.services(), plugin.services().get(LinkService.class), autoCraft);
        listener.onClick(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL));
        ticks(1);
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
    }

    @Test
    void placingTheOutputHopperWakesAWaitingCrafter() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        container(table.getRelative(BlockFace.UP), Material.CHEST).addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        ticks(1);

        Block below = table.getRelative(BlockFace.DOWN);
        Inventory output = container(below, Material.HOPPER);
        autoCraft.blockChanged(below);
        ticks(1);
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
    }

    @Test
    void itemsReachingAChestLinkInputWakeAWaitingCrafter() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Block side = table.getRelative(BlockFace.EAST);
        side.setType(Material.CHEST);
        plugin.services().get(LinkService.class).link(alice, GroupType.CHESTLINK, "mats", side, BlockFace.EAST, true);
        ChestLinkGroup mats = (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, alice.getUniqueId(), "mats");
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        ticks(1);

        mats.inventory().addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        autoCraft.inventoryChanged(mats.inventory());
        ticks(1);
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
    }

    @Test
    void theSweepFindsCraftersNothingScheduled() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        container(table.getRelative(BlockFace.UP), Material.CHEST).addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        autoCraft = autoCraft(false);

        ticks(19);
        assertThat(output.isEmpty()).isTrue();
        ticks(1);
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) autoCraft.tick();
    }

    /** Stands in for a container-lock plugin: refuses every hopper move into or out of one inventory. */
    static final class Lock implements Listener {
        private final Inventory locked;
        final List<InventoryMoveItemEvent> seen = new ArrayList<>();

        Lock(Inventory locked) {
            this.locked = locked;
        }

        @EventHandler
        void onMove(InventoryMoveItemEvent event) {
            seen.add(event);
            if (event.getSource().equals(locked) || event.getDestination().equals(locked)) event.setCancelled(true);
        }
    }

    private Lock lock(Inventory locked) {
        Lock lock = new Lock(locked);
        server.getPluginManager().registerEvents(lock, plugin);
        return lock;
    }

    @Test
    void aLockedInputIsNotDrained() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory locked = container(table.getRelative(BlockFace.UP), Material.CHEST);
        locked.addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        Lock lock = lock(locked);

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table)))).isFalse();
        assertThat(locked.contains(Material.COAL, 1)).isTrue();
        assertThat(locked.contains(Material.STICK, 1)).isTrue();
        assertThat(output.isEmpty()).isTrue();
        assertThat(lock.seen).singleElement().satisfies(event -> {
            assertThat(event.getSource()).isEqualTo(locked);
            assertThat(event.getDestination()).isEqualTo(output);
        });
    }

    @Test
    void aLockedInputIsSkippedForAnAllowedOne() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory locked = container(table.getRelative(BlockFace.UP), Material.CHEST);
        locked.addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory allowed = container(table.getRelative(BlockFace.EAST), Material.CHEST);
        allowed.addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory output = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        Lock lock = lock(locked);

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table)))).isTrue();
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
        assertThat(allowed.isEmpty()).isTrue();
        assertThat(locked.contains(Material.COAL, 1)).isTrue();
        assertThat(locked.contains(Material.STICK, 1)).isTrue();
        assertThat(lock.seen).hasSize(2);
    }

    @Test
    void aLockedOutputIsNotFilled() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory input = container(table.getRelative(BlockFace.UP), Material.CHEST);
        input.addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        Inventory locked = container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        lock(locked);

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table)))).isFalse();
        assertThat(locked.isEmpty()).isTrue();
        assertThat(input.contains(Material.COAL, 1)).isTrue();
    }

    @Test
    void ourOwnListenersIgnoreTheProtectionCheck() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory input = container(table.getRelative(BlockFace.UP), Material.CHEST);
        input.addItem(ItemStack.of(Material.COAL, 2), ItemStack.of(Material.STICK, 2));
        Block below = table.getRelative(BlockFace.DOWN);
        Inventory output = container(below, Material.HOPPER);
        // The check's item is the coal ingredient, which this filter would refuse and stall-avoid if it treated the check as a real move.
        plugin.services().get(FilterService.class).write(below, List.of(new HopperFilter(ItemStack.of(Material.TORCH), Mode.ALLOW, Match.TYPE)));
        Lock lock = lock(server.createInventory(null, 9));

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table)))).isTrue();
        assertThat(lock.seen).singleElement().isInstanceOf(SyntheticMoveEvent.class).satisfies(event -> assertThat(event.isCancelled()).isFalse());
        assertThat(output.contains(Material.TORCH, 4)).isTrue();
        assertThat(output.contains(Material.COAL)).isFalse();
        assertThat(input.contains(Material.COAL, 1)).isTrue();
        assertThat(input.contains(Material.STICK, 1)).isTrue();
    }

    @Test
    void aLockedChestLinkInputIsRefusedLikeAVanillaChest() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Block side = table.getRelative(BlockFace.EAST);
        side.setType(Material.CHEST);
        plugin.services().get(LinkService.class).link(alice, GroupType.CHESTLINK, "mats", side, BlockFace.EAST, true);
        ChestLinkGroup mats = (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, alice.getUniqueId(), "mats");
        mats.inventory().addItem(ItemStack.of(Material.COAL), ItemStack.of(Material.STICK));
        container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        Inventory linkedChest = ((Container) side.getState(false)).getInventory();
        Lock lock = lock(linkedChest);

        assertThat(autoCraft.craftAt(group[0], plugin.services().nodes().get(BlockPos.of(table)))).isFalse();
        assertThat(lock.seen).singleElement().satisfies(event -> assertThat(event.getSource()).isEqualTo(linkedChest));
        assertThat(mats.inventory().contains(Material.COAL)).isTrue();
    }

    @Test
    void editorClicksPlaceGhostItemsAndResolveTheRecipe() {
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        plugin.services().get(LinkService.class).link(alice, GroupType.AUTOCRAFT, "t", table, BlockFace.NORTH, true);
        AutoCraftGroup group = (AutoCraftGroup) plugin.services().groups().find(GroupType.AUTOCRAFT, alice.getUniqueId(), "t");
        RecipeEditorHolder editor = editor(group);

        ItemStack[] matrix = editor.click(2, ItemStack.of(Material.COAL, 64));
        autoCraft.setMatrix(group, matrix, null);
        matrix = editor.click(5, ItemStack.of(Material.STICK, 3));
        autoCraft.setMatrix(group, matrix, null);
        editor.render();

        ItemStack result = editor.getInventory().getItem(RecipeEditorHolder.RESULT_SLOT);
        assertThat(result.getType()).isEqualTo(Material.TORCH);
        assertThat(result.getAmount()).isEqualTo(4);
        assertThat(editor.getInventory().getItem(2).getType()).isEqualTo(Material.COAL);
        assertThat(group.recipeKey()).isEqualTo(NamespacedKey.minecraft("torch"));
        assertThat(plugin.services().groupStore().isDirty(group)).isTrue();
        assertThat(editor.click(9, null)).isNull();
    }

    @Test
    void matchModesDecideWhichInputsCraft() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        Inventory input = container(table.getRelative(BlockFace.UP), Material.CHEST);
        input.addItem(namedCoal(), ItemStack.of(Material.STICK));
        container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        Node node = plugin.services().nodes().get(BlockPos.of(table));

        autoCraft.setMatch(group[0], 1, SlotMatch.EXACT);
        assertThat(autoCraft.craftAt(group[0], node)).isFalse();
        autoCraft.setMatch(group[0], 1, SlotMatch.RECIPE);
        assertThat(autoCraft.craftAt(group[0], node)).isTrue();

        input.addItem(namedCoal(), ItemStack.of(Material.STICK));
        autoCraft.setMatch(group[0], 1, SlotMatch.TYPE);
        assertThat(autoCraft.craftAt(group[0], node)).isTrue();
        assertThat(plugin.services().groupStore().isDirty(group[0])).isTrue();
    }

    @Test
    void specialRecipesMatchExactlyUntilASlotIsSetToType() {
        autoCraft = autoCraft(true);
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        Block table = crafter(group);
        container(table.getRelative(BlockFace.UP), Material.CHEST).addItem(namedCoal(), ItemStack.of(Material.STICK));
        container(table.getRelative(BlockFace.DOWN), Material.HOPPER);
        Node node = plugin.services().nodes().get(BlockPos.of(table));

        assertThat(autoCraft.hasRecipeChoice(group[0], 1)).isFalse();
        assertThat(autoCraft.craftAt(group[0], node)).isFalse();
        autoCraft.setMatch(group[0], 1, SlotMatch.TYPE);
        assertThat(autoCraft.craftAt(group[0], node)).isTrue();
    }

    @Test
    void rightClickChangesMatchForManagersAndReplacingAGhostResetsIt() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        crafter(group);
        RecipeEditorHolder editor = editor(group[0]);

        editor.onClick(alice, 2, null, ClickType.RIGHT);
        assertThat(group[0].matches()[1]).isEqualTo(SlotMatch.EXACT);

        editor.onClick(server.addPlayer("Bob"), 2, null, ClickType.RIGHT);
        assertThat(group[0].matches()[1]).isEqualTo(SlotMatch.EXACT);

        editor.onClick(alice, 6, null, ClickType.RIGHT);
        assertThat(group[0].matches()[5]).isEqualTo(SlotMatch.RECIPE);

        editor.onClick(alice, 2, ItemStack.of(Material.CHARCOAL), ClickType.LEFT);
        assertThat(group[0].matches()[1]).isEqualTo(SlotMatch.RECIPE);
    }

    @Test
    void draggingAnItemPlacesAGhostInEveryMatrixSlotItCovers() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        crafter(group);
        RecipeEditorHolder editor = editor(group[0]);
        ItemStack coal = ItemStack.of(Material.COAL, 32);

        InventoryDragEvent drag = drag(alice, editor, coal, 0, 7, 8, 9, 20);
        assertThat(drag.isCancelled()).isTrue();
        @Nullable ItemStack[] matrix = group[0].matrix();
        assertThat(matrix[6]).isEqualTo(ItemStack.of(Material.COAL));
        assertThat(matrix[7]).isEqualTo(ItemStack.of(Material.COAL));
        assertThat(matrix[8]).isEqualTo(ItemStack.of(Material.COAL));
        assertThat(matrix[4]).isEqualTo(ItemStack.of(Material.STICK));
        assertThat(coal.getAmount()).isEqualTo(32);

        drag(server.addPlayer("Bob"), editor, ItemStack.of(Material.DIRT), 1, 2);
        assertThat(group[0].matrix()[0]).isNull();
    }

    @Test
    void doubleClickingInYourOwnInventoryCannotCollectTheResultIcon() {
        AutoCraftGroup[] group = new AutoCraftGroup[1];
        crafter(group);
        RecipeEditorHolder editor = editor(group[0]);
        InventoryView view = alice.openInventory(editor.getInventory());
        view.setCursor(ItemStack.of(Material.TORCH));

        InventoryClickEvent collect = click(view, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);
        assertThat(collect.isCancelled()).isTrue();
        assertThat(editor.getInventory().getItem(RecipeEditorHolder.RESULT_SLOT).getType()).isEqualTo(Material.TORCH);

        assertThat(click(view, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isFalse();
    }

    /** Fires a click on the first slot of the player's own inventory below {@code view} through the real listeners. */
    private InventoryClickEvent click(InventoryView view, ClickType click, InventoryAction action) {
        int rawSlot = view.getTopInventory().getSize();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, click, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Fires a drag of {@code cursor} over raw {@code slots} of the player's open editor through the real listeners. */
    private InventoryDragEvent drag(PlayerMock player, RecipeEditorHolder editor, ItemStack cursor, int... slots) {
        InventoryView view = player.openInventory(editor.getInventory());
        Map<Integer, ItemStack> placed = new HashMap<>();
        for (int slot : slots) placed.put(slot, cursor.asOne());
        InventoryDragEvent event = new InventoryDragEvent(view, null, cursor, false, placed);
        server.getPluginManager().callEvent(event);
        return event;
    }
}
