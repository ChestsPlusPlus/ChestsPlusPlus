package com.jamesdpeters.minecraft.chests.menus;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.ItemBuilder;
import com.jamesdpeters.minecraft.chests.party.PartyInvite;
import com.jamesdpeters.minecraft.chests.party.PartyUtils;
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
import java.util.function.BiConsumer;

public class InvitesMenu implements InventoryProvider {

    private final SmartInventory menu;
    BiConsumer<PartyInvite, SmartInventory> onPlayerSelect;
    private int lastPage; // Store the last page the player was on.
    private SmartInventory previousInv;

    private InvitesMenu() {
        menu = SmartInventory.builder()
                .id("partyMenu")
                .title("Select an invite to accept!")
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .size(6, 9)
                .build();
        //menu.setInsertable(true);
    }

    public static void open(Player player, SmartInventory previousInventory, BiConsumer<PartyInvite, SmartInventory> onPlayerSelect) {
        InvitesMenu playerSelectorMenu = new InvitesMenu();
        playerSelectorMenu.onPlayerSelect = onPlayerSelect;
        playerSelectorMenu.menu.open(player);
        playerSelectorMenu.previousInv = previousInventory;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();

        List<ClickableItem> itemList = new ArrayList<>();

        for (PartyInvite invite : PartyUtils.getPartyInvites(player)){
            // Pass through click to the menus onPlayerSelect function.
            ItemStack inviteBook = ItemBuilder.getInstance(Material.ENCHANTED_BOOK).setName(invite.getParty().getName()).get();
            ClickableItem clickableItem = ClickableItem.from(inviteBook, itemClickData -> onPlayerSelect.accept(invite, menu));
            itemList.add(clickableItem);
        }

        pagination.setItems(itemList.toArray(new ClickableItem[0]));
        pagination.setItemsPerPage(28);

        ItemStack border = ItemBuilder.getInstance(Material.GRAY_STAINED_GLASS_PANE).setName(" ").get();
        contents.fillBorders(ClickableItem.empty(border));
        for (ClickableItem item : pagination.getPageItems()) {
            contents.add(item);
        }

        ItemStack previous = ItemBuilder.getInstance(Material.ARROW).setName("Previous").get();
        contents.set(5, 2, ClickableItem.from(previous,
                e -> {
                    lastPage = pagination.previous().getPage();
                    menu.open(player, lastPage);
                }));

        ItemStack next = ItemBuilder.getInstance(Material.ARROW).setName("Next").get();
        contents.set(5, 6, ClickableItem.from(next,
                e -> {
                    lastPage = pagination.next().getPage();
                    menu.open(player, lastPage);
                }));

        ItemStack ret = ItemBuilder.getInstance(Material.BARRIER).setName("Return").get();
        contents.set(5, 4, ClickableItem.from(ret, itemClickData -> {
            if (previousInv != null) {
                previousInv.open(player);
            }
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }

    public SmartInventory getMenu() {
        return menu;
    }
}
