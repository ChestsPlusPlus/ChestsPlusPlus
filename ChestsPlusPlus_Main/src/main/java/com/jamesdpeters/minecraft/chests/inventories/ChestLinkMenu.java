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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ChestLinkMenu implements InventoryProvider {

    public static HashMap<Player, ChestLinkMenu> menus;

    private final Collection<ChestLinkStorage> storages;
    private final SmartInventory menu;
    private int lastPage; // Store the last page the player was on.

    private ChestLinkMenu(Player player) {
        this.storages = Config.getChestLink().getStorageMap(player.getUniqueId()).values();
        menu = SmartInventory.builder()
                .id("chestLinkMenu")
                .title("Inventory Storage")
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .size(6, 9)
                .build();
        lastPage = 0;
        //menu.setInsertable(true);
    }

    public static ChestLinkMenu getMenu(Player player) {
        if (menus == null) menus = new HashMap<>();

        if (menus.containsKey(player)) {
            return menus.get(player);
        } else {
            menus.put(player, new ChestLinkMenu(player));
            return menus.get(player);
        }
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();

        List<ClickableItem> itemList = new ArrayList<>();
        for (ChestLinkStorage storage : storages) {
            ClickableItem item = storage.getClickableItem(player);
            itemList.add(item);
        }
        List<ChestLinkStorage> memberOfStorage = Config.getChestLink().getStorageMemberOf(player);
        for (ChestLinkStorage storage : memberOfStorage) {
            ClickableItem item = storage.getClickableItem(player);
            itemList.add(item);
        }

        pagination.setItems(itemList.toArray(new ClickableItem[0]));
        pagination.setItemsPerPage(28);

        contents.fillBorders(ClickableItem.empty(Utils.getNamedItem(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ")));
        for (ClickableItem item : pagination.getPageItems()) {
            contents.add(item);
        }

        contents.set(5, 2, ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.ARROW), "Previous"),
                e -> {
                    lastPage = pagination.previous().getPage();
                    menu.open(player, lastPage);
                }));
        contents.set(5, 6, ClickableItem.from(Utils.getNamedItem(new ItemStack(Material.ARROW), "Next"),
                e -> {
                    lastPage = pagination.next().getPage();
                    menu.open(player, lastPage);
                }));
    }

    public void openLastPage(Player player){
        menu.open(player, lastPage);
    }

    public void open(Player player){
        menu.open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }

    public SmartInventory getMenu() {
        return menu;
    }
}
