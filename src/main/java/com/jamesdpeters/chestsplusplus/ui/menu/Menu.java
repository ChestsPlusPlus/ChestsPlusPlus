package com.jamesdpeters.chestsplusplus.ui.menu;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A minimal chest-GUI menu (replaces SmartInvs): a custom holder with clickable buttons. Every click and drag in a
 * menu is cancelled by {@link MenuListener}; clicks on a button run its handler.
 */
public class Menu implements InventoryHolder {

    /** What a button does when clicked. */
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(Player player, ClickType click);
    }

    public record Button(ItemStack icon, ClickHandler handler) {}

    private final Inventory inventory;
    private final Map<Integer, Button> buttons = new HashMap<>();

    @SuppressWarnings("this-escape") // a custom holder must pass itself to createInventory
    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public final void set(int slot, ItemStack icon, ClickHandler handler) {
        buttons.put(slot, new Button(icon, handler));
        inventory.setItem(slot, icon);
    }

    public final void clear() {
        buttons.clear();
        inventory.clear();
    }

    public final @Nullable Button button(int slot) {
        return buttons.get(slot);
    }

    public final int size() {
        return inventory.getSize();
    }

    public final void open(Player player) {
        player.openInventory(inventory);
    }

    /** Called when a viewer closes the menu (not when it is replaced by another inventory). */
    public void onClose(Player player) {}

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
