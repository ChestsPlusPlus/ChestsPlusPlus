package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.ui.menu.GhostEditor;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The sneak-click filter editor. Row 1 is the Allow row and row 2 the Deny row; empty slots are lime/red
 * panes that say what clicking them does. Click a row with an item on the cursor to add a ghost copy (the item isn't
 * consumed). On an entry: left-click cycles exact → type → similar, right-click moves it to the other row, shift-click
 * removes it. Row 3 holds the help item and the clear button. Every change is saved straight to the hopper.
 */
public final class FilterEditorHolder extends GhostEditor {

    static final int ROW = 9;
    static final int HELP_SLOT = 18;
    static final int CLEAR_SLOT = 26;

    /** How the player clicked an editor slot. */
    public enum Click {
        LEFT,
        RIGHT,
        SHIFT
    }

    @Getter private final Block hopper;
    private final Messages messages;
    private final FilterService filterService;
    private final List<HopperFilter> allows = new ArrayList<>();
    private final List<HopperFilter> denies = new ArrayList<>();
    private final Inventory inventory;

    @SuppressWarnings("this-escape") // a custom holder must pass itself to createInventory
    public FilterEditorHolder(Block hopper, List<HopperFilter> filters, Messages messages, FilterService filterService) {
        this.hopper = hopper;
        this.messages = messages;
        this.filterService = filterService;
        for (HopperFilter filter : filters) {
            List<HopperFilter> row = filter.mode() == HopperFilter.Mode.ALLOW ? allows : denies;
            if (row.size() < ROW) row.add(filter);
        }
        this.inventory = Bukkit.createInventory(this, ROW * 3, messages.get(Message.FILTER_TITLE));
        render();
    }

    /** All entries in priority order (allows, then denies), as saved. */
    public List<HopperFilter> filters() {
        List<HopperFilter> all = new ArrayList<>(allows);
        all.addAll(denies);
        return all;
    }

    /** Applies the click and saves the result straight to the hopper. The editor closes if the hopper has gone. */
    @Override
    public void onClick(Player player, int slot, @Nullable ItemStack cursor, ClickType type) {
        if (hopper.getType() != Material.HOPPER) {
            player.closeInventory();
            return;
        }
        Click click = type.isShiftClick() ? Click.SHIFT : type.isRightClick() ? Click.RIGHT : Click.LEFT;
        if (click(slot, cursor, click)) filterService.write(hopper, filters());
    }

    /**
     * Applies a click on a top-inventory slot. Returns true if the filters changed (and should be saved).
     *
     * @param cursor the item on the player's cursor (null/empty for none)
     */
    public boolean click(int slot, @Nullable ItemStack cursor, Click click) {
        if (slot == CLEAR_SLOT) {
            boolean changed = !allows.isEmpty() || !denies.isEmpty();
            allows.clear();
            denies.clear();
            render();
            return changed;
        }
        if (slot < 0 || slot >= ROW * 2) return false;
        HopperFilter.Mode mode = slot < ROW ? HopperFilter.Mode.ALLOW : HopperFilter.Mode.DENY;
        boolean changed = cursor != null && !cursor.isEmpty() ? place(mode, slot % ROW, cursor) : edit(mode, slot % ROW, click);
        if (changed) render();
        return changed;
    }

    /** Puts a ghost copy of {@code cursor} in the slot, replacing any entry there. */
    private boolean place(HopperFilter.Mode mode, int index, ItemStack cursor) {
        List<HopperFilter> row = rowOf(mode);
        HopperFilter added = new HopperFilter(cursor, mode, HopperFilter.Match.EXACT);
        if (index < row.size()) row.set(index, added);
        else if (row.size() < ROW) row.add(added);
        else return false;
        return true;
    }

    private boolean edit(HopperFilter.Mode mode, int index, Click click) {
        List<HopperFilter> row = rowOf(mode);
        if (index >= row.size()) return false;
        HopperFilter entry = row.get(index);
        switch (click) {
            case SHIFT -> row.remove(index);
            case RIGHT -> {
                List<HopperFilter> target = rowOf(mode.opposite());
                if (target.size() >= ROW) return false;
                row.remove(index);
                target.add(new HopperFilter(entry.template(), mode.opposite(), entry.match()));
            }
            case LEFT -> row.set(index, entry.withMatch(entry.match().next()));
        }
        return true;
    }

    private List<HopperFilter> rowOf(HopperFilter.Mode mode) {
        return mode == HopperFilter.Mode.ALLOW ? allows : denies;
    }

    private void render() {
        inventory.clear();
        for (int i = 0; i < ROW; i++) {
            inventory.setItem(i, i < allows.size() ? icon(allows.get(i)) : placeholder(HopperFilter.Mode.ALLOW));
            inventory.setItem(ROW + i, i < denies.size() ? icon(denies.get(i)) : placeholder(HopperFilter.Mode.DENY));
        }
        inventory.setItem(HELP_SLOT, named(Material.BOOK, Message.FILTER_HELP, Message.FILTER_HELP_LORE));
        inventory.setItem(CLEAR_SLOT, named(Material.BARRIER, Message.FILTER_CLEAR, null));
    }

    private ItemStack placeholder(HopperFilter.Mode mode) {
        return mode == HopperFilter.Mode.ALLOW
                ? named(Material.LIME_STAINED_GLASS_PANE, Message.FILTER_ALLOW_EMPTY, Message.FILTER_ALLOW_EMPTY_LORE)
                : named(Material.RED_STAINED_GLASS_PANE, Message.FILTER_DENY_EMPTY, Message.FILTER_DENY_EMPTY_LORE);
    }

    private ItemStack icon(HopperFilter filter) {
        ItemStack icon = filter.template().clone();
        boolean allow = filter.mode() == HopperFilter.Mode.ALLOW;
        Message match = switch (filter.match()) {
            case EXACT -> Message.FILTER_MATCH_EXACT;
            case TYPE -> Message.FILTER_MATCH_TYPE;
            case SIMILAR -> Message.FILTER_MATCH_SIMILAR;
        };
        List<Component> lore = new ArrayList<>();
        lore.addAll(messages.lines(allow ? Message.FILTER_ALLOW : Message.FILTER_DENY));
        lore.addAll(messages.lines(match));
        lore.addAll(messages.lines(Message.FILTER_CONTROLS, Messages.text("other", allow ? "Deny" : "Allow")));
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return icon;
    }

    private ItemStack named(Material material, Message name, @Nullable Message lore) {
        ItemStack item = ItemStack.of(material);
        item.setData(DataComponentTypes.ITEM_NAME, messages.get(name));
        if (lore != null) item.setData(DataComponentTypes.LORE, ItemLore.lore(messages.lines(lore)));
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
