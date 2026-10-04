package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * AutoCraft (plan §5.9): group type handler, display content, recipe editing, and the single central crafting ticker
 * with per-node backoff so idle crafters cost close to nothing.
 */
public final class AutoCraftService implements GroupTypeHandler, DisplayService.Content {

    static final BlockFace[] INPUT_FACES = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    static final int MAX_BACKOFF_TICKS = 200;

    private final Services services;
    private final DisplayService displays;
    private final CraftingBackend backend;
    /** Per-recipe slot substitutes, cached per group until its recipe changes. */
    private final Map<Long, List<@Nullable Predicate<ItemStack>>> slotChoices = new HashMap<>();

    private final Map<BlockPos, Backoff> backoff = new HashMap<>();
    private long tick;

    private static final class Backoff {
        int failures;
        long nextAttempt;
    }

    public AutoCraftService(Services services, DisplayService displays, CraftingBackend backend) {
        this.services = services;
        this.displays = displays;
        this.backend = backend;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // GroupTypeHandler
    // ---------------------------------------------------------------------------------------------------------------

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
        return 0;
    }

    @Override
    public void onRemoved(StorageGroup group, Location dropAt) {
        slotChoices.remove(group.id());
        for (Node node : services.nodes().nodesOf(group.id())) backoff.remove(node.pos());
        List.copyOf(openEditors(group)).forEach(Player::closeInventory);
    }

    @Override
    public void onRenamed(StorageGroup group) {
        List.copyOf(openEditors(group)).forEach(Player::closeInventory);
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
        return result.getAmount() + "x " + result.getType().getKey().getKey().replace('_', ' ');
    }

    // ---------------------------------------------------------------------------------------------------------------
    // DisplayService.Content
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public @Nullable ItemStack item(StorageGroup group) {
        return group instanceof AutoCraftGroup craft ? craft.result() : null;
    }

    @Override
    public Component label(StorageGroup group) {
        return services.messages().get(Message.AUTOCRAFT_DISPLAY_LABEL, Messages.text("group", group.name()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Recipes
    // ---------------------------------------------------------------------------------------------------------------

    public void openEditor(Player player, AutoCraftGroup group) {
        player.openInventory(new RecipeEditorHolder(group, services.messages().get(Message.MENU_RECIPE_TITLE, Messages.text("group", group.name())))
                .getInventory());
    }

    /** Re-resolves a loaded group's stored matrix against the live recipe list (results aren't persisted). */
    public void resolveLoaded(AutoCraftGroup group) {
        if (group.matrixIsEmpty()) return;
        World world = firstWorld();
        if (world == null) return;
        CraftingBackend.ResolvedRecipe resolved = backend.resolve(group.matrix(), world);
        group.setRecipe(group.matrix(), resolved == null ? null : resolved.key(), resolved == null ? null : resolved.result());
        if (resolved != null) slotChoices.put(group.id(), resolved.slots());
    }

    /**
     * Sets a new ghost matrix (from the editor): resolves it once, stores key, matrix and result, refreshes displays,
     * persists, and resets backoff. Returns the result, or null when the matrix isn't a recipe.
     */
    public @Nullable ItemStack setMatrix(AutoCraftGroup group, @Nullable ItemStack[] matrix, @Nullable Player editor) {
        World world = editor != null ? editor.getWorld() : firstWorld();
        CraftingBackend.ResolvedRecipe resolved = world == null ? null : backend.resolve(matrix, world);
        group.setRecipe(matrix, resolved == null ? null : resolved.key(), resolved == null ? null : resolved.result());
        if (resolved != null) slotChoices.put(group.id(), resolved.slots());
        else slotChoices.remove(group.id());
        for (Node node : services.nodes().nodesOf(group.id())) backoff.remove(node.pos());
        services.persistence().markDirty(group);
        displays.requestUpdate(group);
        if (editor != null) {
            if (resolved != null) {
                editor.playSound(editor.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
            }
        }
        for (Player viewer : openEditors(group)) {
            if (editorOf(viewer) instanceof RecipeEditorHolder holder) holder.render();
        }
        return resolved == null ? null : resolved.result();
    }

    private static @Nullable RecipeEditorHolder editorOf(Player player) {
        // The top inventory can be null for players with nothing open on some platforms (MockBukkit).
        @Nullable Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && com.jamesdpeters.chestsplusplus.core.Holders.of(top) instanceof RecipeEditorHolder holder ? holder : null;
    }

    private List<Player> openEditors(StorageGroup group) {
        List<Player> viewers = new ArrayList<>();
        for (Player player : services.plugin().getServer().getOnlinePlayers()) {
            if (editorOf(player) instanceof RecipeEditorHolder holder && holder.group().id() == group.id()) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Crafting ticker
    // ---------------------------------------------------------------------------------------------------------------

    /** Called by the central AutoCraft ticker every {@code autocraft.tick-interval} ticks. */
    public void tick(int intervalTicks) {
        tick += intervalTicks;
        if (!services.settings().features().autocraft()) return;
        for (StorageGroup group : services.groups().all(GroupType.AUTOCRAFT)) {
            if (!(group instanceof AutoCraftGroup craft) || !craft.hasRecipe()) continue;
            for (Node node : services.nodes().nodesOf(group.id())) {
                if (!node.pos().isLoaded()) continue;
                Backoff state = backoff.get(node.pos());
                if (state != null && tick < state.nextAttempt) continue;
                boolean crafted = craftAt(craft, node);
                if (crafted) {
                    backoff.remove(node.pos());
                } else {
                    Backoff next = state != null ? state : new Backoff();
                    next.failures++;
                    next.nextAttempt = tick + Math.min(MAX_BACKOFF_TICKS, (long) intervalTicks << Math.min(next.failures - 1, 8));
                    backoff.put(node.pos(), next);
                }
            }
        }
    }

    /** Something changed next to a crafter (items moved in, a player closed an adjacent container). */
    public void inputChanged(Block container) {
        if (backoff.isEmpty()) return;
        for (BlockFace face : INPUT_FACES) {
            Block table = container.getRelative(face.getOppositeFace());
            backoff.remove(BlockPos.of(table));
        }
    }

    /** True when no crafter is backing off, so input-change hooks can return before doing any work. */
    public boolean noBackoff() {
        return backoff.isEmpty();
    }

    public boolean isBackingOff(BlockPos pos) {
        return backoff.containsKey(pos);
    }

    /** One craft attempt at one node. Returns true if something was crafted. */
    boolean craftAt(AutoCraftGroup group, Node node) {
        Block table = node.pos().block();
        if (table == null || table.getType() != Material.CRAFTING_TABLE) return false;
        Block below = table.getRelative(BlockFace.DOWN);
        Inventory output = inventoryAt(below, group);
        if (output == null) return false;
        if (below.getBlockData() instanceof Hopper hopper) {
            if (!hopper.isEnabled()) return false; // a powered hopper is locked
        } else if (!table.isBlockIndirectlyPowered() && !table.isBlockPowered()) {
            return false; // other containers below: only craft while the table is powered
        }
        List<Inventory> inputs = new ArrayList<>(INPUT_FACES.length);
        for (BlockFace face : INPUT_FACES) {
            Inventory input = inventoryAt(table.getRelative(face), group);
            if (input != null && input != output && !inputs.contains(input)) inputs.add(input);
        }
        if (inputs.isEmpty()) return false;
        List<@Nullable Predicate<ItemStack>> slots = slotChoices.get(group.id());
        if (slots == null) {
            resolveLoaded(group);
            slots = slotChoices.get(group.id());
            if (slots == null) return false;
        }
        CraftPlanner.Plan plan = CraftPlanner.plan(slots, inputs);
        if (plan == null) return false;
        CraftingBackend.Crafted crafted = backend.craft(plan.matrix(), table.getWorld());
        if (crafted == null) return false;
        List<ItemStack> produced = new ArrayList<>();
        produced.add(crafted.result());
        for (ItemStack leftover : crafted.remaining()) {
            if (leftover != null && !leftover.isEmpty()) produced.add(leftover);
        }
        if (!CraftPlanner.fits(output, produced)) return false;
        CraftPlanner.commit(plan);
        output.addItem(produced.toArray(ItemStack[]::new));
        markChanged(output);
        inputs.forEach(this::markChanged);
        return true;
    }

    /**
     * The inventory at a block: a ChestLink's shared inventory if it is linked and the crafter's owner may use it,
     * else the container's own inventory.
     */
    private @Nullable Inventory inventoryAt(Block block, AutoCraftGroup crafter) {
        Node node = services.nodes().at(block);
        if (node != null) {
            if (services.groups().byId(node.groupId()) instanceof ChestLinkGroup chest
                    && services.access().canAccess(crafter.owner(), false, chest)) {
                return chest.inventory();
            }
            return null;
        }
        return block.getState(false) instanceof Container container ? container.getInventory() : null;
    }

    private void markChanged(Inventory inventory) {
        if (com.jamesdpeters.chestsplusplus.core.Holders.of(inventory) instanceof com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder holder) {
            services.persistence().markDirty(holder.group());
            displays.requestUpdate(holder.group());
        }
    }

    private @Nullable World firstWorld() {
        List<World> worlds = services.plugin().getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.getFirst();
    }
}
