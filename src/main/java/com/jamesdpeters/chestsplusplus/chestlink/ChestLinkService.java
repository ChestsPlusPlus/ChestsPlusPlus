package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.GroupPermission;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.chestlink.sort.Sorter;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.InventoryChanges;
import com.jamesdpeters.chestsplusplus.core.Items;
import com.jamesdpeters.chestsplusplus.core.PaperApi;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.core.Viewers;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** ChestLink-specific behaviour: the shared inventory, opening, lids, sorting, merging and dropping contents. */
public final class ChestLinkService extends GroupTypeHandler<ChestLinkGroup> {

    private final Services services;
    private final InventoryChanges changes;
    /** Lids currently held open per group, so the last viewer closing can close them. */
    private final Map<Long, Set<BlockPos>> openLids = new HashMap<>();
    /** Node each viewer opened from (lid animation for non-"animate all" setups). */
    private final Map<UUID, BlockPos> openedFrom = new HashMap<>();

    public ChestLinkService(Services services, InventoryChanges changes) {
        super(GroupType.CHESTLINK, ChestLinkGroup.class);
        this.services = services;
        this.changes = changes;
    }

    public static boolean isChestLinkBlock(Block block) {
        BlockState state = block.getState(false);
        return state instanceof Chest || state instanceof Barrel;
    }

    @Override
    public boolean isValidBlock(Block block) {
        return isChestLinkBlock(block);
    }

    @Override
    protected int linked(ChestLinkGroup group, Block block) {
        return absorbPhysicalContents(group, block);
    }

    @Override
    protected void removed(ChestLinkGroup group, Location dropAt) {
        dropContents(group, dropAt);
    }

    @Override
    protected void renamed(ChestLinkGroup group) {
        retitle(group);
    }

    @Override
    public void closeAllViews() {
        for (StorageGroup group : services.groups().all(GroupType.CHESTLINK)) {
            if (group instanceof ChestLinkGroup chest && chest.hasInventory()) Viewers.close(chest.inventory());
        }
    }

    @Override
    protected void revokedViews(ChestLinkGroup group) {
        if (!group.hasInventory()) return;
        Viewers.close(group.inventory(), viewer -> !mayKeepViewing(viewer, group)).forEach(viewer -> refuse(viewer, group));
    }

    @Override
    protected void openRemotely(Player player, ChestLinkGroup group) {
        open(player, group, null);
    }

    @Override
    protected ItemStack iconOf(ChestLinkGroup group) {
        ItemStack common = group.hasInventory() ? mostCommon(group) : null;
        return common == null ? ItemStack.of(Material.CHEST) : common;
    }

    @Override
    protected String summaryOf(ChestLinkGroup group) {
        return group.hasInventory() ? String.format(Locale.ROOT, "%,d items", itemCount(group)) : "0 items";
    }

    @Override
    public ChestLinkGroup create(long id, UUID owner, String name) {
        ChestLinkGroup group = new ChestLinkGroup(id, owner, name, System.currentTimeMillis());
        ChestLinkHolder.attach(group, title(group), null);
        return group;
    }

    public void attachLoaded(ChestLinkGroup group, @Nullable ItemStack @Nullable [] contents) {
        ChestLinkHolder.attach(group, title(group), contents);
    }

    public Component title(ChestLinkGroup group) {
        return services.messages().get(Message.CHESTLINK_TITLE, Messages.group(group));
    }

    /** Inventory titles are fixed at creation, so renames recreate the inventory (closing viewers). */
    public void retitle(ChestLinkGroup group) {
        if (Holders.of(group.inventory()) instanceof ChestLinkHolder holder) holder.retitle(title(group));
    }

    /**
     * Moves the physical container's contents into the group, leaving the physical block empty. Returns the number of stacks that did not
     * fit and were dropped at the block.
     */
    public int absorbPhysicalContents(ChestLinkGroup group, Block block) {
        if (!(block.getState(false) instanceof Container container)) return 0;
        Inventory physical = container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
        List<ItemStack> moving = new ArrayList<>();
        for (ItemStack item : PaperApi.contents(physical)) if (Items.present(item)) moving.add(item);
        if (moving.isEmpty()) return 0;
        physical.clear();
        Map<Integer, ItemStack> overflow = group.inventory().addItem(moving.toArray(ItemStack[]::new));
        Location dropAt = block.getLocation().clone().add(0.5, 1.0, 0.5);
        overflow.values().forEach(item -> block.getWorld().dropItemNaturally(dropAt, item));
        changed(group);
        return overflow.size();
    }

    /** Drops every stack (each exactly once; v2 removed similar stacks too) and empties the inventory. */
    public void dropContents(ChestLinkGroup group, Location at) {
        Inventory inventory = group.inventory();
        Viewers.close(inventory);
        for (ItemStack item : PaperApi.contents(inventory)) {
            if (Items.present(item) && at.getWorld() != null) at.getWorld().dropItemNaturally(at, item);
        }
        inventory.clear();
    }

    /** Opens the group for {@code player}; {@code from} is the clicked node, or null for a remote open. */
    public void open(Player player, ChestLinkGroup group, @Nullable Node from) {
        sort(group);
        if (from != null) openedFrom.put(player.getUniqueId(), from.pos());
        else openedFrom.remove(player.getUniqueId());
        player.openInventory(group.inventory());
        if (from == null) player.playSound(PaperApi.location(player), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }

    /** Can {@code viewer} still use the open inventory? It needs the permission it was opened with, as well as access to the group. */
    public boolean mayKeepViewing(HumanEntity viewer, ChestLinkGroup group) {
        GroupPermission action = openedFrom.containsKey(viewer.getUniqueId()) ? GroupPermission.OPEN : GroupPermission.REMOTE;
        return viewer.hasPermission(Permissions.of(GroupType.CHESTLINK, action)) && services.access().canAccess(viewer.getUniqueId(), viewer, group);
    }

    public void revoke(HumanEntity viewer, ChestLinkGroup group) {
        viewer.closeInventory();
        refuse(viewer, group);
    }

    private void refuse(HumanEntity viewer, ChestLinkGroup group) {
        services.send(viewer, Message.ERROR_NO_ACCESS, Messages.group(group));
    }

    /** InventoryOpenEvent for a ChestLink inventory: open lids if this is the first viewer. */
    public void viewerOpened(ChestLinkGroup group, HumanEntity viewer) {
        Set<BlockPos> lids = openLids.computeIfAbsent(group.id(), k -> new HashSet<>());
        List<BlockPos> targets = new ArrayList<>();
        if (services.settings().chestlink().animateAllNodes()) {
            services.nodes().nodesOf(group.id()).forEach(node -> targets.add(node.pos()));
        } else {
            BlockPos from = openedFrom.get(viewer.getUniqueId());
            if (from != null) targets.add(from);
        }
        for (BlockPos pos : targets) {
            if (lids.contains(pos) || !pos.isLoaded()) continue;
            Block block = pos.block();
            if (block != null && block.getState(false) instanceof Lidded lidded) {
                lidded.open();
                lids.add(pos);
                playLidSound(block, Sound.BLOCK_BARREL_OPEN, Sound.BLOCK_CHEST_OPEN);
            }
        }
    }

    /** InventoryCloseEvent for a ChestLink inventory: persist, sort, refresh displays, close lids when last out. */
    public void viewerClosed(ChestLinkGroup group, HumanEntity viewer) {
        openedFrom.remove(viewer.getUniqueId());
        services.groups().changed(group);
        boolean lastViewer = group.inventory().getViewers().stream().allMatch(v -> v.equals(viewer));
        if (!lastViewer) return;
        sort(group);
        Set<BlockPos> lids = openLids.remove(group.id());
        if (lids == null) return;
        for (BlockPos pos : lids) {
            Block block = pos.isLoaded() ? pos.block() : null;
            if (block != null && block.getState(false) instanceof Lidded lidded) {
                lidded.close();
                playLidSound(block, Sound.BLOCK_BARREL_CLOSE, Sound.BLOCK_CHEST_CLOSE);
            }
        }
    }

    public void setSortMode(ChestLinkGroup group, SortMode mode) {
        group.setSortMode(mode);
        sort(group);
        changed(group);
    }

    public void sort(ChestLinkGroup group) {
        if (group.sortMode() == SortMode.OFF) return;
        Inventory inventory = group.inventory();
        inventory.setContents(Sorter.sort(PaperApi.contents(inventory), group.sortMode(), inventory.getSize()));
    }

    /** Its contents changed: saves and redisplays it, and wakes the crafters waiting on it. */
    public void changed(ChestLinkGroup group) {
        services.groups().changed(group);
        changes.changed(group.inventory());
    }

    /** Total item count, for menus and listings. */
    public static int itemCount(ChestLinkGroup group) {
        return Arrays.stream(group.inventory().getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();
    }

    /** The most common item by total amount (the display item), or null when empty. */
    public static @Nullable ItemStack mostCommon(ChestLinkGroup group) {
        Object2IntOpenHashMap<Material> totals = new Object2IntOpenHashMap<>(8);
        Material best = null;
        int bestTotal = 0;
        for (ItemStack item : PaperApi.contents(group.inventory())) {
            if (!Items.present(item)) continue;
            Material type = item.getType();
            int amount = item.getAmount();
            int total = totals.addTo(type, amount) + amount;
            if (total > bestTotal) {
                bestTotal = total;
                best = type;
            }
        }
        return best == null ? null : ItemStack.of(best);
    }

    @Override
    protected @Nullable ItemStack itemOf(ChestLinkGroup group) {
        return group.hasInventory() ? mostCommon(group) : null;
    }

    @Override
    protected Component labelOf(ChestLinkGroup group) {
        return services.messages().get(Message.CHESTLINK_DISPLAY_LABEL, Messages.group(group));
    }

    private static void playLidSound(Block block, Sound barrel, Sound chest) {
        Sound sound = block.getType() == Material.BARREL ? barrel : chest;
        block.getWorld().playSound(block.getLocation().clone().add(0.5, 0.5, 0.5), sound, 0.5f, 1f);
    }

    public void forgetViewer(UUID viewer) {
        openedFrom.remove(viewer);
    }
}
