package com.jamesdpeters.minecraft.chests.inventories;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class PartyMenu implements InventoryProvider {

    public static HashMap<Player, PartyMenu> menus;

    private final SmartInventory menu;

    private ClickableItem CREATE = ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.ANVIL), "Create a Party"), itemClickData -> {itemClickData.getPlayer().sendMessage("CREATE");});
    private ClickableItem INVITE = ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.WRITABLE_BOOK), "Invite a Player to a Party"), itemClickData -> {itemClickData.getPlayer().sendMessage("INVITE");});
    private ClickableItem REMOVE_PLAYER = ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.SKELETON_SKULL), "Remove a Player from a Party"), itemClickData -> {itemClickData.getPlayer().sendMessage("INVITE");});
    private ClickableItem LIST   = ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.MAP), "List all your parties"), itemClickData -> {itemClickData.getPlayer().sendMessage("LIST");});
    private ClickableItem DELETE = ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.BARRIER), "Delete a Party"), itemClickData -> {itemClickData.getPlayer().sendMessage("DELETE");});

    private List<ClickableItem> itemList = Arrays.asList(CREATE, INVITE, LIST, DELETE);

    private PartyMenu() {
        menu = SmartInventory.builder()
                .id("partyMenu")
                .title("Party Menu")
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .size(3, 9)
                .build();
        //menu.setInsertable(true);
    }

    public static PartyMenu getMenu(Player player) {
        if (menus == null) menus = new HashMap<>();

        if (!menus.containsKey(player)) {
            menus.put(player, new PartyMenu());
        }
        return menus.get(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {

        contents.fillBorders(ClickableItem.empty(Utils.getNamedItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ")));
        for (ClickableItem item : itemList) {
            contents.add(item);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }

    public SmartInventory getMenu() {
        return menu;
    }
}
