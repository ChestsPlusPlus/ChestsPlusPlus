package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.SyntheticMoveEvent;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Crafter;
import org.bukkit.block.Dropper;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Hoppers ↔ ChestLinks via {@link HopperInventorySearchEvent}: when a hopper looks up a linked
 * block as source or destination, the group's inventory is substituted, so transfers are vanilla. This fires every
 * tick per idle hopper, so the handler is one hash lookup with no allocation, and only touches the group: it is saved at the next flush
 * if its contents changed, which covers servers with {@code hopper.disable-move-event} and moves other plugins cancel and make by hand.
 */
@RequiredArgsConstructor
public final class HopperBridge implements Listener {

    private final Plugin plugin;
    private final Services services;
    private final ChestLinkService chestLinks;
    /** Linked blocks a crafter pushed into this tick, absorbed together on the next. */
    private final Set<BlockPos> pendingAbsorbs = new HashSet<>();

    /** While ChestLinks are disabled the hopper finds no container at all, so nothing reaches the linked block's empty one. */
    @EventHandler(priority = EventPriority.NORMAL)
    void onSearch(HopperInventorySearchEvent event) {
        if (!(services.groupAt(event.getSearchBlock()) instanceof ChestLinkGroup group)) return;
        if (!enabled()) {
            event.setInventory(null);
            return;
        }
        event.setInventory(group.inventory());
        services.groupStore().touch(group);
    }

    private boolean enabled() {
        return services.settings().features().chestlinks();
    }

    /**
     * Lock plugins find a container through its inventory's holder, and a group's inventory has no block, so a hopper move through a
     * ChestLink is asked again as a move to or from the linked block's own container. Refusing that refuses the move, as for a vanilla
     * chest. Runs first so that later listeners see the refusal.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void onLinkedMove(InventoryMoveItemEvent event) {
        if (event instanceof SyntheticMoveEvent) return;
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        boolean fromLink = Holders.of(source) instanceof ChestLinkHolder;
        boolean intoLink = Holders.of(destination) instanceof ChestLinkHolder;
        if (fromLink == intoLink) return;
        Inventory linked = fromLink ? linkedContainer(source, pulledFrom(destination)) : linkedContainer(destination, pushedInto(source));
        if (linked == null) return;
        if (!SyntheticMoveEvent.allows(fromLink ? linked : source, event.getItem(), intoLink ? linked : destination)) event.setCancelled(true);
    }

    /** The block a hopper (or hopper minecart) pulls from: the one above it. */
    private static @Nullable Block pulledFrom(Inventory hopper) {
        Location at = hopper.getLocation();
        return at == null || at.getWorld() == null ? null : at.getBlock().getRelative(BlockFace.UP);
    }

    /** The block a hopper pushes into: the one it faces. */
    private static @Nullable Block pushedInto(Inventory hopper) {
        Location at = hopper.getLocation();
        if (at == null || at.getWorld() == null) return null;
        Block block = at.getBlock();
        return block.getBlockData() instanceof Directional facing ? block.getRelative(facing.getFacing()) : null;
    }

    /** {@code block}'s own container, if it is a node of the ChestLink whose shared inventory is {@code group}. */
    private @Nullable Inventory linkedContainer(Inventory group, @Nullable Block block) {
        if (block == null || !(Holders.of(group) instanceof ChestLinkHolder holder) || services.groupAt(block) != holder.group()) return null;
        return Holders.containerAt(block);
    }

    /**
     * Droppers and crafters don't fire the search event, so what they push into a linked block is redirected here. While ChestLinks are
     * disabled the push is refused: a dropper keeps its item and a crafter ejects its result into the world, as vanilla does.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBlockPush(InventoryMoveItemEvent event) {
        if (event instanceof SyntheticMoveEvent) return;
        InventoryHolder source = Holders.of(event.getSource());
        if (!(source instanceof Dropper) && !(source instanceof Crafter)) return;
        Block destination = destinationBlock(event);
        if (destination == null || !(services.groupAt(destination) instanceof ChestLinkGroup group)) return;
        if (!enabled()) event.setCancelled(true);
        else if (source instanceof Crafter) absorbNextTick(destination);
        else pushFromDropper(event, group);
    }

    private static @Nullable Block destinationBlock(InventoryMoveItemEvent event) {
        Location location = event.getDestination().getLocation();
        return location == null || location.getWorld() == null ? null : location.getBlock();
    }

    /**
     * A dropper keeps its item when the move is cancelled, so it can be moved by hand; a full group leaves it in the dropper. Plugins
     * listening after this one are asked first, since cancelling here hides the move from them.
     */
    private void pushFromDropper(InventoryMoveItemEvent event, ChestLinkGroup group) {
        event.setCancelled(true);
        ItemStack moving = event.getItem();
        if (!SyntheticMoveEvent.allows(event.getSource(), moving, event.getDestination())) return;
        Map<Integer, ItemStack> overflow = group.inventory().addItem(moving.clone());
        int notMoved = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = moving.getAmount() - notMoved;
        if (moved <= 0) return;
        event.getSource().removeItem(moving.asQuantity(moved));
        chestLinks.changed(group);
    }

    /**
     * A crafter ejects its result into the world when the move is cancelled, and its event item is the new result rather than a grid
     * slot, so the move goes ahead into the empty physical container and is absorbed into the group on the next tick.
     */
    private void absorbNextTick(Block block) {
        if (pendingAbsorbs.add(BlockPos.of(block)) && pendingAbsorbs.size() == 1) {
            plugin.getServer().getScheduler().runTask(plugin, this::absorbPending);
        }
    }

    private void absorbPending() {
        List<BlockPos> positions = List.copyOf(pendingAbsorbs);
        pendingAbsorbs.clear();
        for (BlockPos pos : positions) {
            Block block = pos.isLoaded() ? pos.block() : null;
            if (block != null && services.groupAt(block) instanceof ChestLinkGroup group) chestLinks.absorbPhysicalContents(group, block);
        }
    }
}
