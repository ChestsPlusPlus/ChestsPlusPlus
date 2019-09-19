package com.jamesdpeters.minecraft.chests.inventories;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.Config;
import com.jamesdpeters.minecraft.chests.Utils;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ChestLinkMenu implements InventoryProvider {

    public static HashMap<Player, SmartInventory> menus;

    private Collection<InventoryStorage> storages;
    private SmartInventory menu;

    private ChestLinkMenu(Player player){
        this.storages = Config.getPlayer(player).values();
        menu = SmartInventory.builder()
                .id("chestLinkMenu")
                .title("Inventory Storage")
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .build();
        //menu.setInsertable(true);
    }

    public static SmartInventory getMenu(Player player){
        if(menus == null) menus = new HashMap<>();

        if(menus.containsKey(player)){
            return menus.get(player);
        } else {
            menus.put(player, new ChestLinkMenu(player).getMenu());
            return menus.get(player);
        }
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();
        List<ClickableItem> itemList = new ArrayList<>();
        for(InventoryStorage storage : storages){
            ClickableItem item = storage.getClickableItem(player);
            //item.setRemoveable(true);
            itemList.add(item);
        }

        pagination.setItems(itemList.toArray(new ClickableItem[0]));
        pagination.setItemsPerPage(28);

        contents.fillBorders(ClickableItem.empty(Utils.getNamedItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)," ")));
        for(ClickableItem item : pagination.getPageItems()){
            contents.add(item);
        }

        contents.set(5, 2, ClickableItem.of(Utils.getNamedItem(new ItemStack(Material.ARROW),"Previous"),
                e -> menu.open(player, pagination.previous().getPage())));
        contents.set(5, 6, ClickableItem.of(Utils.getNamedItem(new ItemStack(Material.ARROW),"Next"),
                e -> menu.open(player, pagination.next().getPage())));
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }

    public SmartInventory getMenu() {
        return menu;
    }
}
