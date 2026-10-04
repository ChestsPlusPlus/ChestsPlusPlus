package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The sneak-click filter editor (plan §5.5): row 1 holds Allow entries, row 2 Deny entries, row 3 controls. Clicking
 * a row with an item on the cursor adds a ghost copy (the item isn't consumed); clicking an entry cycles its match;
 * shift-click removes it; the barrier clears everything. Every change is saved straight to the hopper.
 */
public final class FilterEditorHolder implements InventoryHolder {

    static final int ROW = 9;
    static final int CLEAR_SLOT = 22;
    static final int HELP_SLOT = 18;

    private final Block hopper;
    private final Messages messages;
    private final List<HopperFilter> allows = new ArrayList<>();
    private final List<HopperFilter> denies = new ArrayList<>();
    private final Inventory inventory;

    @SuppressWarnings("this-escape") // a custom holder must pass itself to createInventory
    public FilterEditorHolder(Block hopper, List<HopperFilter> filters, Messages messages) {
        this.hopper = hopper;
        this.messages = messages;
        for (HopperFilter filter : filters) {
            List<HopperFilter> row = filter.mode() == HopperFilter.Mode.ALLOW ? allows : denies;
            if (row.size() < ROW) row.add(filter);
        }
        this.inventory = Bukkit.createInventory(this, ROW * 3, messages.get(Message.FILTER_TITLE));
        render();
    }

    public Block hopper() {
        return hopper;
    }

    /** All entries in priority order (allows, then denies), as saved. */
    public List<HopperFilter> filters() {
        List<HopperFilter> all = new ArrayList<>(allows);
        all.addAll(denies);
        return all;
    }

    /**
     * Applies a click on a top-inventory slot. Returns true if the filters changed (and should be saved).
     *
     * @param cursor the item on the player's cursor (null/empty for none)
     */
    public boolean click(int slot, @Nullable ItemStack cursor, boolean shift) {
        if (slot == CLEAR_SLOT) {
            boolean changed = !allows.isEmpty() || !denies.isEmpty();
            allows.clear();
            denies.clear();
            render();
            return changed;
        }
        if (slot < 0 || slot >= ROW * 2) return false;
        HopperFilter.Mode mode = slot < ROW ? HopperFilter.Mode.ALLOW : HopperFilter.Mode.DENY;
        List<HopperFilter> row = mode == HopperFilter.Mode.ALLOW ? allows : denies;
        int index = slot % ROW;
        boolean hasCursor = cursor != null && !cursor.isEmpty();
        if (hasCursor) {
            HopperFilter added = new HopperFilter(cursor, mode, HopperFilter.Match.EXACT);
            if (index < row.size()) row.set(index, added);
            else if (row.size() < ROW) row.add(added);
            else return false;
        } else if (index < row.size()) {
            if (shift) row.remove(index);
            else row.set(index, row.get(index).withMatch(row.get(index).match().next()));
        } else {
            return false;
        }
        render();
        return true;
    }

    private void render() {
        inventory.clear();
        for (int i = 0; i < allows.size(); i++) inventory.setItem(i, icon(allows.get(i)));
        for (int i = 0; i < denies.size(); i++) inventory.setItem(ROW + i, icon(denies.get(i)));
        inventory.setItem(HELP_SLOT, named(Material.PAPER, Message.FILTER_HELP));
        inventory.setItem(CLEAR_SLOT, named(Material.BARRIER, Message.FILTER_CLEAR));
        inventory.setItem(20, named(Material.LIME_STAINED_GLASS_PANE, Message.FILTER_ALLOW));
        inventory.setItem(24, named(Material.RED_STAINED_GLASS_PANE, Message.FILTER_DENY));
    }

    private ItemStack icon(HopperFilter filter) {
        ItemStack icon = filter.template().clone();
        Message match = switch (filter.match()) {
            case EXACT -> Message.FILTER_MATCH_EXACT;
            case TYPE -> Message.FILTER_MATCH_TYPE;
            case SIMILAR -> Message.FILTER_MATCH_SIMILAR;
        };
        icon.setData(
                DataComponentTypes.LORE,
                ItemLore.lore(List.of(
                        messages.lines(
                                        filter.mode() == HopperFilter.Mode.ALLOW
                                                ? Message.FILTER_ALLOW
                                                : Message.FILTER_DENY)
                                .getFirst(),
                        messages.lines(match).getFirst())));
        return icon;
    }

    private ItemStack named(Material material, Message name) {
        ItemStack item = ItemStack.of(material);
        item.setData(DataComponentTypes.ITEM_NAME, messages.get(name));
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
