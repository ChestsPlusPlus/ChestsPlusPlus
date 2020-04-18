package com.jamesdpeters.minecraft.chests.inventories;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ChestLinkInventory implements InventoryProvider {

    ArrayList<ItemStack> items;
    SmartInventory inventory;
    InventoryStorage storage;

    public ChestLinkInventory(InventoryStorage storage){
        items = new ArrayList<>();
        this.storage = storage;
        inventory = SmartInventory.builder()
                .id("chestLinkInventory")
                .title(storage.getIdentifier())
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .build();

        //inventory.setInsertable(true);
    }



    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();
        List<ClickableItem> itemList = new ArrayList<>();

        pagination.setItems(itemList.toArray(new ClickableItem[0]));
        pagination.setItemsPerPage(28);

        contents.fillBorders(ClickableItem.empty(Utils.getNamedItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)," ")));
        for(ClickableItem item : pagination.getPageItems()){
            contents.add(item);
        }

        contents.set(5, 2, ClickableItem.of(Utils.getNamedItem(new ItemStack(Material.ARROW),"Previous"),
                e -> inventory.open(player, pagination.previous().getPage())));
        contents.set(5, 6, ClickableItem.of(Utils.getNamedItem(new ItemStack(Material.ARROW),"Next"),
                e -> inventory.open(player, pagination.next().getPage())));
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }
}
