package com.jamesdpeters.minecraft.chests.menus;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class AcceptDialogMenu implements InventoryProvider {

    private final SmartInventory menu;
    private Consumer<Boolean> booleanConsumer;
    private String acceptString, rejectString;

    private AcceptDialogMenu(String dialogTitle) {
        menu = SmartInventory.builder()
                .id("partyMenu")
                .title(dialogTitle)
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .size(3, 9)
                .build();
        //menu.setInsertable(true);
    }

    public static void open(Player player, String dialogTitle, String acceptString, String rejectString, Consumer<Boolean> booleanConsumer) {
        AcceptDialogMenu acceptDialogMenu = new AcceptDialogMenu(dialogTitle);
        acceptDialogMenu.booleanConsumer = booleanConsumer;
        acceptDialogMenu.acceptString = acceptString;
        acceptDialogMenu.rejectString = rejectString;
        acceptDialogMenu.getMenu().open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {

        ItemStack border = ItemBuilder.getInstance(Material.GRAY_STAINED_GLASS_PANE).setName(" ").get();
        contents.fill(ClickableItem.empty(border));

        // Accept button
        ItemStack diamond = ItemBuilder.getInstance(Material.DIAMOND).setName(acceptString).get();
        ClickableItem accept = ClickableItem.from(diamond, itemClickData -> booleanConsumer.accept(true));
        contents.set(1, 3, accept);

        // Accept button
        ItemStack barrier = ItemBuilder.getInstance(Material.BARRIER).setName(rejectString).get();
        ClickableItem reject = ClickableItem.from(barrier, itemClickData -> booleanConsumer.accept(false));
        contents.set(1, 5, reject);
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }

    public SmartInventory getMenu() {
        return menu;
    }
}
