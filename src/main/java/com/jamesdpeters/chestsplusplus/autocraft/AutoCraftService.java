package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.link.SyntheticMoveEvent;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * AutoCraft: group type handler, display content, recipe editing, and crafting. One ticker runs every tick but only touches the nodes
 * {@link CraftScheduler} says are due, so idle crafters cost close to nothing while a change next to one makes it craft straight away.
 */
@RequiredArgsConstructor
public final class AutoCraftService implements GroupTypeHandler, DisplayService.Content {

    static final BlockFace[] INPUT_FACES = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    /** The inputs plus the output below: a change at any of them can let a failing crafter succeed. */
    static final BlockFace[] WATCHED_FACES = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};

    private final Services services;
    private final DisplayService displays;
    private final CraftingBackend backend;
    /** Each group's recipe choices per slot, cached until its recipe changes. */
    private final Map<Long, List<@Nullable Predicate<ItemStack>>> recipeChoices = new HashMap<>();
    /** What each slot accepts once the group's match modes are applied; what the planner uses. */
    private final Map<Long, List<@Nullable Predicate<ItemStack>>> slotRules = new HashMap<>();

    private final CraftScheduler scheduler = new CraftScheduler();
    private long tick;

    @Override
    public GroupType type() {
        return GroupType.AUTOCRAFT;
    }

    @Override
    public boolean isValidBlock(Block block) {
        return block.getType() == Material.CRAFTING_TABLE;
    }

    @Override
    public AutoCraftGroup create(long id, UUID owner, String name) {
        return new AutoCraftGroup(id, owner, name, System.currentTimeMillis());
    }

    @Override
    public int onLinked(StorageGroup group, Block block) {
        scheduler.soon(BlockPos.of(block));
        return 0;
    }

    @Override
    public void onRemoved(StorageGroup group, Location dropAt) {
        recipeChoices.remove(group.id());
        slotRules.remove(group.id());
        for (Node node : services.nodes().nodesOf(group.id())) scheduler.forget(node.pos());
        openEditors(group).forEach(Player::closeInventory);
    }

    @Override
    public void onRenamed(StorageGroup group) {
        openEditors(group).forEach(Player::closeInventory);
    }

    @Override
    public void openRemote(Player player, StorageGroup group) {
        if (group instanceof AutoCraftGroup craft) openEditor(player, craft);
    }

    @Override
    public ItemStack icon(StorageGroup group) {
        ItemStack result = group instanceof AutoCraftGroup craft ? craft.result() : null;
        return result == null ? ItemStack.of(Material.CRAFTING_TABLE) : result.asOne();
    }

    @Override
    public String summary(StorageGroup group) {
        ItemStack result = group instanceof AutoCraftGroup craft ? craft.result() : null;
        if (result == null) return "no recipe";
        return result.getAmount() + "x " + itemName(result);
    }

    static String itemName(ItemStack item) {
        return item.getType().getKey().getKey().replace('_', ' ');
    }

    @Override
    public @Nullable ItemStack item(StorageGroup group) {
        return group instanceof AutoCraftGroup craft ? craft.result() : null;
    }

    @Override
    public Component label(StorageGroup group) {
        return services.messages().get(Message.AUTOCRAFT_DISPLAY_LABEL, Messages.group(group));
    }

    public void openEditor(Player player, AutoCraftGroup group) {
        Component title = services.messages().get(Message.MENU_RECIPE_TITLE, Messages.group(group));
        player.openInventory(new RecipeEditorHolder(group, title, this, services.messages()).getInventory());
    }

    /**
     * A click on a recipe editor slot. Anyone with access may look, but only managers may change the recipe. Right-clicking a ghost with an
     * empty cursor changes what that slot accepts; any other click places or clears a ghost.
     */
    void edit(Player player, RecipeEditorHolder editor, int slot, @Nullable ItemStack cursor, ClickType click) {
        AutoCraftGroup group = editor.group();
        if (!mayEdit(player, group)) return;
        if (click.isRightClick() && (cursor == null || cursor.isEmpty())) {
            int index = RecipeEditorHolder.matrixIndex(slot);
            if (index >= 0 && group.matrix()[index] != null) setMatch(group, index, group.matches()[index].next(hasRecipeChoice(group, index)));
            return;
        }
        applyEdit(player, editor, editor.click(slot, cursor));
    }

    /** Dragging an item across the editor places a ghost in each matrix slot it covered, resolved once. */
    void drag(Player player, RecipeEditorHolder editor, List<Integer> slots, ItemStack cursor) {
        if (mayEdit(player, editor.group())) applyEdit(player, editor, editor.place(slots, cursor));
    }

    /** True if the player may change this group's recipe; otherwise tells them why not. */
    private boolean mayEdit(Player player, AutoCraftGroup group) {
        if (services.access().canManage(player.getUniqueId(), player, group)) return true;
        services.send(player, Message.ERROR_NOT_OWNER, Messages.group(group));
        return false;
    }

    private void applyEdit(Player player, RecipeEditorHolder editor, @Nullable ItemStack @Nullable [] matrix) {
        if (matrix == null) return;
        AutoCraftGroup group = editor.group();
        ItemStack result = setMatrix(group, matrix, player);
        editor.render();
        if (result != null) services.send(player, Message.AUTOCRAFT_RECIPE_SET, Messages.group(group), Messages.text("item", itemName(result)));
    }

    /** Re-resolves a loaded group's stored matrix against the live recipe list (results aren't persisted). */
    public void resolveLoaded(AutoCraftGroup group) {
        if (group.matrixIsEmpty()) return;
        World world = firstWorld();
        if (world == null) return;
        applyRecipe(group, group.matrix(), backend.resolve(group.matrix(), world));
    }

    /**
     * Sets a new ghost matrix (from the editor): resolves it once, stores key, matrix and result, refreshes displays, persists, and tries
     * the group's crafters on the next tick. Returns the result, or null when the matrix isn't a recipe.
     */
    public @Nullable ItemStack setMatrix(AutoCraftGroup group, @Nullable ItemStack[] matrix, @Nullable Player editor) {
        World world = editor != null ? editor.getWorld() : firstWorld();
        CraftingBackend.ResolvedRecipe resolved = world == null ? null : backend.resolve(matrix, world);
        applyRecipe(group, matrix, resolved);
        craftSoon(group);
        services.groupStore().markDirty(group);
        displays.requestUpdate(group);
        if (editor != null && resolved != null) editor.playSound(editor.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
        renderEditors(group);
        return resolved == null ? null : resolved.result();
    }

    /** Sets what one matrix slot accepts, then re-applies the rules, persists and refreshes open editors. */
    public void setMatch(AutoCraftGroup group, int index, SlotMatch match) {
        group.setMatch(index, match);
        List<@Nullable Predicate<ItemStack>> choices = recipeChoices.get(group.id());
        if (choices != null) slotRules.put(group.id(), SlotRules.of(group.matrix(), group.matches(), choices));
        craftSoon(group);
        services.groupStore().markDirty(group);
        renderEditors(group);
    }

    /** Whether the recipe itself accepts more than the ghost item in this matrix slot (e.g. any planks). */
    public boolean hasRecipeChoice(AutoCraftGroup group, int index) {
        List<@Nullable Predicate<ItemStack>> choices = recipeChoices.get(group.id());
        return choices != null && choices.get(index) != null;
    }

    private void applyRecipe(AutoCraftGroup group, @Nullable ItemStack[] matrix, CraftingBackend.@Nullable ResolvedRecipe resolved) {
        group.setRecipe(matrix, resolved == null ? null : resolved.key(), resolved == null ? null : resolved.result());
        if (resolved == null) {
            recipeChoices.remove(group.id());
            slotRules.remove(group.id());
            return;
        }
        recipeChoices.put(group.id(), resolved.choices());
        slotRules.put(group.id(), SlotRules.of(group.matrix(), group.matches(), resolved.choices()));
    }

    private void renderEditors(AutoCraftGroup group) {
        for (Player viewer : openEditors(group)) {
            if (editorOf(viewer) instanceof RecipeEditorHolder holder) holder.render();
        }
    }

    private static @Nullable RecipeEditorHolder editorOf(Player player) {
        // The top inventory can be null for players with nothing open on some platforms (MockBukkit).
        @Nullable Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && Holders.of(top) instanceof RecipeEditorHolder holder ? holder : null;
    }

    private List<Player> openEditors(StorageGroup group) {
        return services.plugin().getServer().getOnlinePlayers().stream()
                .filter(player -> editorOf(player) instanceof RecipeEditorHolder holder && holder.group().id() == group.id())
                .map(Player.class::cast)
                .toList();
    }

    /**
     * Called every tick: attempts the nodes due now, and every {@code autocraft.tick-interval} ticks sweeps for crafters nothing has
     * scheduled yet (just loaded, or relinked with Silk Touch).
     */
    public void tick() {
        if (!services.settings().features().autocraft()) return;
        int interval = services.settings().autocraft().tickInterval();
        for (BlockPos pos : scheduler.advance()) attempt(pos, interval);
        if (++tick % interval == 0) sweep(interval);
    }

    private void sweep(int interval) {
        for (StorageGroup group : services.groups().all(GroupType.AUTOCRAFT)) {
            if (!(group instanceof AutoCraftGroup craft) || !craft.hasRecipe()) continue;
            for (Node node : services.nodes().nodesOf(group.id())) {
                if (!scheduler.tracks(node.pos()) && node.pos().isLoaded()) attempt(craft, node, interval);
            }
        }
    }

    /** A due node that is no longer a loaded crafter with a recipe is dropped; the sweep finds it again if that changes. */
    private void attempt(BlockPos pos, int interval) {
        Node node = services.nodes().get(pos);
        if (node != null && pos.isLoaded() && services.groups().byId(node.groupId()) instanceof AutoCraftGroup craft && craft.hasRecipe()) {
            attempt(craft, node, interval);
        } else {
            scheduler.forget(pos);
        }
    }

    private void attempt(AutoCraftGroup group, Node node, int interval) {
        if (craftAt(group, node)) scheduler.crafted(node.pos(), interval);
        else scheduler.failed(node.pos(), interval, watchedBlocks(node.pos()), watchedGroups(node.pos()));
    }

    private static List<BlockPos> watchedBlocks(BlockPos table) {
        List<BlockPos> blocks = new ArrayList<>(WATCHED_FACES.length);
        for (BlockFace face : WATCHED_FACES) blocks.add(table.offset(face.getModX(), face.getModY(), face.getModZ()));
        return blocks;
    }

    /** ChestLink groups next to the table: their inventory can change through any of their nodes. */
    private List<Long> watchedGroups(BlockPos table) {
        List<Long> groups = new ArrayList<>(1);
        for (BlockFace face : WATCHED_FACES) {
            Node neighbour = services.nodes().get(table.offset(face.getModX(), face.getModY(), face.getModZ()));
            if (neighbour != null && services.groups().byId(neighbour.groupId()) instanceof ChestLinkGroup) groups.add(neighbour.groupId());
        }
        return groups;
    }

    private void craftSoon(StorageGroup group) {
        for (Node node : services.nodes().nodesOf(group.id())) scheduler.soon(node.pos());
    }

    /**
     * Items moved in or out of {@code inventory}, or a viewer closed it: crafters waiting on it try again next tick. Runs for every hopper
     * transfer, so it returns at once unless a crafter is waiting, and otherwise costs a holder read (no snapshot) and a hash lookup.
     */
    public void inventoryChanged(Inventory inventory) {
        if (!scheduler.anyWaiting()) return;
        if (inventory instanceof DoubleChestInventory chest) {
            locationChanged(chest.getLeftSide().getLocation());
            locationChanged(chest.getRightSide().getLocation());
            return;
        }
        switch (Holders.of(inventory)) {
            case ChestLinkHolder holder -> scheduler.groupChanged(holder.group().id());
            case BlockInventoryHolder holder -> scheduler.blockChanged(BlockPos.of(holder.getBlock()));
            case null, default -> {}
        }
    }

    /** A block was placed or broken: crafters waiting on that spot (e.g. for an output hopper) try again next tick. */
    public void blockChanged(Block block) {
        if (scheduler.anyWaiting()) scheduler.blockChanged(BlockPos.of(block));
    }

    private void locationChanged(@Nullable Location at) {
        if (at != null && at.getWorld() != null) scheduler.blockChanged(BlockPos.of(at));
    }

    public boolean isBackingOff(BlockPos pos) {
        return scheduler.isBackingOff(pos);
    }

    /** One craft attempt at one node. Returns true if something was crafted. */
    boolean craftAt(AutoCraftGroup group, Node node) {
        Block table = node.pos().block();
        if (table == null || table.getType() != Material.CRAFTING_TABLE) return false;
        Inventory output = outputOf(table, group);
        if (output == null) return false;
        List<Inventory> inputs = inputsOf(table, group, output);
        if (inputs.isEmpty()) return false;
        List<@Nullable Predicate<ItemStack>> slots = slotsFor(group);
        if (slots == null) return false;
        Craft craft = permittedCraft(slots, inputs, output, table);
        if (craft == null) return false;

        CraftPlanner.commit(craft.plan());
        output.addItem(craft.produced());
        markChanged(output);
        inputs.forEach(this::markChanged);
        return true;
    }

    private record Craft(CraftPlanner.Plan plan, ItemStack[] produced) {}

    /** A craft whose every move other plugins allow: a refused input is dropped and the craft planned again without it. */
    private @Nullable Craft permittedCraft(List<@Nullable Predicate<ItemStack>> slots, List<Inventory> inputs, Inventory output, Block table) {
        List<Inventory> usable = new ArrayList<>(inputs);
        List<Inventory> allowed = new ArrayList<>(inputs.size());
        while (true) {
            Craft craft = craft(slots, usable, output, table.getWorld());
            if (craft == null) return null;
            Inventory refused = refusedSource(craft.plan(), output, table, allowed);
            if (refused == null) return craft;
            usable.remove(refused);
        }
    }

    private @Nullable Craft craft(List<@Nullable Predicate<ItemStack>> slots, List<Inventory> inputs, Inventory output, World world) {
        CraftPlanner.Plan plan = CraftPlanner.plan(slots, inputs);
        if (plan == null) return null;
        CraftingBackend.Crafted crafted = backend.craft(plan.matrix(), world);
        if (crafted == null) return null;
        List<ItemStack> produced = new ArrayList<>();
        produced.add(crafted.result());
        for (ItemStack leftover : crafted.remaining()) {
            if (leftover != null && !leftover.isEmpty()) produced.add(leftover);
        }
        return CraftPlanner.fits(output, produced) ? new Craft(plan, produced.toArray(ItemStack[]::new)) : null;
    }

    /**
     * The first input the plan takes from that another plugin refuses, asked as a hopper move from the input's block into the output's,
     * so a crafter can do no more than a hopper could there. Each input is asked once per attempt.
     */
    private @Nullable Inventory refusedSource(CraftPlanner.Plan plan, Inventory output, Block table, List<Inventory> allowed) {
        Inventory into = blockSide(output, table, BlockFace.DOWN);
        for (int i = 0; i < plan.sources().size(); i++) {
            CraftPlanner.Source source = plan.sources().get(i);
            if (source == null || allowed.contains(source.inventory())) continue;
            Inventory input = source.inventory();
            if (!SyntheticMoveEvent.allows(blockSide(input, table, INPUT_FACES), Objects.requireNonNull(plan.matrix()[i]), into)) return input;
            allowed.add(input);
        }
        return null;
    }

    /** What a lock plugin can recognise: for a ChestLink, its linked block's own container beside the table rather than the shared inventory. */
    private Inventory blockSide(Inventory inventory, Block table, BlockFace... faces) {
        if (!(Holders.of(inventory) instanceof ChestLinkHolder holder)) return inventory;
        for (BlockFace face : faces) {
            Block block = table.getRelative(face);
            Inventory container = services.groupAt(block) == holder.group() ? Holders.containerAt(block) : null;
            if (container != null) return container;
        }
        return inventory;
    }

    /**
     * The inventory below the table, if it may receive crafts now: a hopper unless it's locked by redstone, any other container only while
     * the table is powered.
     */
    private @Nullable Inventory outputOf(Block table, AutoCraftGroup group) {
        Block below = table.getRelative(BlockFace.DOWN);
        Inventory output = inventoryAt(below, group);
        if (output == null) return null;
        boolean canOutput = below.getBlockData() instanceof Hopper hopper
                ? hopper.isEnabled()
                : table.isBlockIndirectlyPowered() || table.isBlockPowered();
        return canOutput ? output : null;
    }

    private List<Inventory> inputsOf(Block table, AutoCraftGroup group, Inventory output) {
        List<Inventory> inputs = new ArrayList<>(INPUT_FACES.length);
        for (BlockFace face : INPUT_FACES) {
            Inventory input = inventoryAt(table.getRelative(face), group);
            if (input != null && input != output && !inputs.contains(input)) inputs.add(input);
        }
        return inputs;
    }

    private @Nullable List<@Nullable Predicate<ItemStack>> slotsFor(AutoCraftGroup group) {
        if (!slotRules.containsKey(group.id())) resolveLoaded(group);
        return slotRules.get(group.id());
    }

    /**
     * The inventory at a block: a ChestLink's shared inventory if it is linked and the crafter's owner may use it, else the container's own
     * inventory.
     */
    private @Nullable Inventory inventoryAt(Block block, AutoCraftGroup crafter) {
        Node node = services.nodes().at(block);
        if (node == null) return block.getState(false) instanceof Container container ? container.getInventory() : null;
        return services.groups().byId(node.groupId()) instanceof ChestLinkGroup chest && services.access().canAccess(crafter.owner(), false, chest)
                ? chest.inventory()
                : null;
    }

    private void markChanged(Inventory inventory) {
        if (Holders.of(inventory) instanceof ChestLinkHolder holder) {
            services.groupStore().markDirty(holder.group());
            displays.requestUpdate(holder.group());
        }
    }

    private @Nullable World firstWorld() {
        List<World> worlds = services.plugin().getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.getFirst();
    }
}
