package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.chestlink.sort.Sorter;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
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
public final class ChestLinkService implements DisplayService.Content, GroupTypeHandler {

    private final Services services;
    private final DisplayService displays;
    /** Lids currently held open per group, so the last viewer closing can close them. */
    private final Map<Long, Set<BlockPos>> openLids = new HashMap<>();
    /** Node each viewer opened from (lid animation for non-"animate all" setups). */
    private final Map<UUID, BlockPos> openedFrom = new HashMap<>();

    public ChestLinkService(Services services, DisplayService displays) {
        this.services = services;
        this.displays = displays;
    }

    public static boolean isChestLinkBlock(Block block) {
        BlockState state = block.getState(false);
        return state instanceof Chest || state instanceof Barrel;
    }

    @Override
    public GroupType type() {
        return GroupType.CHESTLINK;
    }

    @Override
    public boolean isValidBlock(Block block) {
        return isChestLinkBlock(block);
    }

    @Override
    public int onLinked(StorageGroup group, Block block) {
        return group instanceof ChestLinkGroup chest ? absorbPhysicalContents(chest, block) : 0;
    }

    @Override
    public void onRemoved(StorageGroup group, Location dropAt) {
        if (group instanceof ChestLinkGroup chest) dropContents(chest, dropAt);
    }

    @Override
    public void onRenamed(StorageGroup group) {
        if (group instanceof ChestLinkGroup chest) retitle(chest);
    }

    @Override
    public void openRemote(Player player, StorageGroup group) {
        if (group instanceof ChestLinkGroup chest) open(player, chest, null);
    }

    @Override
    public ItemStack icon(StorageGroup group) {
        ItemStack common = group instanceof ChestLinkGroup chest && chest.hasInventory() ? mostCommon(chest) : null;
        return common == null ? ItemStack.of(Material.CHEST) : common;
    }

    @Override
    public String summary(StorageGroup group) {
        return group instanceof ChestLinkGroup chest && chest.hasInventory()
                ? String.format(Locale.ROOT, "%,d items", itemCount(chest))
                : "0 items";
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
        for (ItemStack item : physical.getContents()) if (item != null && !item.isEmpty()) moving.add(item);
        if (moving.isEmpty()) return 0;
        physical.clear();
        Map<Integer, ItemStack> overflow = group.inventory().addItem(moving.toArray(ItemStack[]::new));
        Location dropAt = block.getLocation().clone().add(0.5, 1.0, 0.5);
        overflow.values().forEach(item -> block.getWorld().dropItemNaturally(dropAt, item));
        services.persistence().markDirty(group);
        displays.requestUpdate(group);
        return overflow.size();
    }

    /** Drops every stack (each exactly once; v2 removed similar stacks too) and empties the inventory. */
    public void dropContents(ChestLinkGroup group, Location at) {
        Inventory inventory = group.inventory();
        List.copyOf(inventory.getViewers()).forEach(HumanEntity::closeInventory);
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.isEmpty() && at.getWorld() != null) at.getWorld().dropItemNaturally(at, item);
        }
        inventory.clear();
    }

    /** Opens the group for {@code player}; {@code from} is the clicked node, or null for a remote open. */
    public void open(Player player, ChestLinkGroup group, @Nullable Node from) {
        sort(group);
        if (from != null) openedFrom.put(player.getUniqueId(), from.pos());
        else openedFrom.remove(player.getUniqueId());
        player.openInventory(group.inventory());
        if (from == null) player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
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
        services.persistence().markDirty(group);
        displays.requestUpdate(group);
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

    public void sort(ChestLinkGroup group) {
        if (group.sortMode() == SortMode.OFF) return;
        Inventory inventory = group.inventory();
        inventory.setContents(Sorter.sort(inventory.getContents(), group.sortMode(), inventory.getSize()));
    }

    public void changed(ChestLinkGroup group) {
        services.persistence().markDirty(group);
        displays.requestUpdate(group);
    }

    /** Total item count, for menus and listings. */
    public static int itemCount(ChestLinkGroup group) {
        return Arrays.stream(group.inventory().getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();
    }

    /** The most common item by total amount (the display item), or null when empty. */
    public static @Nullable ItemStack mostCommon(ChestLinkGroup group) {
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        Material best = null;
        int bestTotal = 0;
        for (ItemStack item : group.inventory().getContents()) {
            if (item == null || item.isEmpty()) continue;
            int total = totals.merge(item.getType(), item.getAmount(), Integer::sum);
            if (total > bestTotal) {
                bestTotal = total;
                best = item.getType();
            }
        }
        return best == null ? null : ItemStack.of(best);
    }

    @Override
    public @Nullable ItemStack item(StorageGroup group) {
        return group instanceof ChestLinkGroup chest && chest.hasInventory() ? mostCommon(chest) : null;
    }

    @Override
    public Component label(StorageGroup group) {
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
