package com.jamesdpeters.minecraft.chests.menus;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.ItemBuilder;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.party.PartyUtils;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PartyMenu implements InventoryProvider {

    public static HashMap<Player, PartyMenu> menus;

    private SmartInventory menu;

    ClickableItem CREATE;
    ClickableItem INVITE;
    ClickableItem REMOVE_PLAYER;
    ClickableItem LIST;
    ClickableItem DELETE;
    ClickableItem INVITES;
    ClickableItem LEAVE_PARTY;

    private List<ClickableItem> itemList;
    private final Player player;

    private PartyMenu(Player player) {
        this.player = player;
        updateIcons();
        rebuildMenu();
    }

    public void rebuildMenu() {
        menu = SmartInventory.builder()
                .id("partyMenu")
                .title("Party Menu")
                .provider(this)
                .manager(ChestsPlusPlus.INVENTORY_MANAGER)
                .size(3, 9)
                .build();
    }

    public void updateIcons() {
        // Enchant invites icon if player has invites to accept.
        ItemStack INVITES_ICON = ItemBuilder
                .getInstance(Material.FILLED_MAP)
                .setEnchanted(PartyUtils.hasInvites(player))
                .setName("Party Invites")
                .get();

        INVITES = ClickableItem.from(INVITES_ICON, itemClickData -> partyInvites(itemClickData.getPlayer()));

        CREATE = ClickableItem.from(ItemBuilder
                        .getInstance(Material.ANVIL)
                        .setName("Create a party").get(),
                itemClickData -> create(itemClickData.getPlayer()));

        INVITE = ClickableItem.from(ItemBuilder
                .getInstance(Material.WRITABLE_BOOK)
                .setName( "Invite a player to a party").get(),
                itemClickData -> invite(itemClickData.getPlayer()));

        REMOVE_PLAYER = ClickableItem.from(ItemBuilder
                .getInstance(Material.SKELETON_SKULL)
                .setName( "Remove a player from a party").get(),
                itemClickData -> removePlayer(itemClickData.getPlayer()));

        LIST = ClickableItem.from(ItemBuilder
                .getInstance(Material.MAP)
                .setName("List all parties you're a member of").get(),
                itemClickData -> listParties(itemClickData.getPlayer()));

        DELETE = ClickableItem.from(ItemBuilder
                        .getInstance(Material.BARRIER)
                        .setName( "Delete a party").get(),
                itemClickData -> deleteParty(itemClickData.getPlayer()));

        LEAVE_PARTY = ClickableItem.from(ItemBuilder
                        .getInstance(Material.WITHER_SKELETON_SKULL)
                .setName("Leave party").get(), itemClickData -> leaveParty(player)
        );

        itemList = Arrays.asList(CREATE, INVITE, REMOVE_PLAYER, LEAVE_PARTY, LIST, DELETE, INVITES);
    }

    public static PartyMenu getMenu(Player player) {
        if (menus == null) menus = new HashMap<>();

        if (!menus.containsKey(player)) {
            menus.put(player, new PartyMenu(player));
        }
        return menus.get(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        ItemStack border = ItemBuilder.getInstance(Material.GRAY_STAINED_GLASS_PANE).setName(" ").get();
        contents.fillBorders(ClickableItem.empty(border));
        for (ClickableItem item : itemList) {
            contents.add(item);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }

    public SmartInventory getMenu() {
        updateIcons();
        rebuildMenu();
        return menu;
    }

    /*
     * Party Methods
     */

    public void create(Player player) {
        TextInputUI.getInput(player, Message.PARTY_ENTER_NAME.getString(), (p, partyName) -> {
            boolean result = PartyUtils.createParty(player, partyName);
            if (result){
                player.sendMessage(ChatColor.GREEN+ Message.PARTY_CREATED.getString(ChatColor.WHITE+partyName+ChatColor.GREEN));
                getMenu().open(player);
                return AnvilGUI.Response.close();
            } else {
                player.sendMessage(ChatColor.RED+Message.PARTY_ALREADY_EXISTS.getString(ChatColor.WHITE+partyName+ChatColor.RED));
                return AnvilGUI.Response.text(Message.ALREADY_EXISTS_ANVIL.getString());
            }
        });
    }

    public void invite(Player player) {
        PartySelectorMenu.open(player, getMenu(), PartySelectorMenu.Type.OWNED, (party, menu) -> {
            List<OfflinePlayer> inviteablePlayers = Utils.getOnlinePlayersNotInList(party.getMembers());
            inviteablePlayers.remove(party.getOwner());
            PlayerSelectorMenu.open(player, Message.PARTY_INVITE_PLAYER.getString(), menu, inviteablePlayers, (player1, itemStack) -> itemStack, (playerToInvite, menu2) -> {
                PartyUtils.invitePlayer(party, playerToInvite);
                getMenu().open(player);
            });
        });
    }

    public void removePlayer(Player player) {
        PartySelectorMenu.open(player, getMenu(),  PartySelectorMenu.Type.OWNED, (party, menu) -> {
            PlayerSelectorMenu.open(player, Message.PARTY_REMOVE_PLAYER.getString(), menu, party.getMembers(), (player1, itemStack) -> itemStack, (selectedPlayer, menu2) -> {
                AcceptDialogMenu.open(player, Message.PARTY_REMOVE_PLAYER_DIALOG.getString(selectedPlayer.getName()), Message.YES.getString(), Message.NO.getString(), aBoolean -> {
                    if (aBoolean) {
                        party.removeMember(selectedPlayer);
                    }
                    getMenu().open(player);
                });
            });
        });
    }

    public void listParties(Player player) {
        PartySelectorMenu.open(player, getMenu(),  PartySelectorMenu.Type.ALL, (party, smartInventory) -> {
            PlayerSelectorMenu.open(player, Message.PARTY_MEMBERS.getString(party.getPartyName()), smartInventory, party.getAllPlayers(),
                    // Change player head to enchanted if owner.
                    (offlinePlayer, itemStack) -> {
                        if (party.getOwner().getUniqueId().equals(offlinePlayer.getUniqueId())) return ItemBuilder.fromInstance(itemStack).addLore(Message.PARTY_OWNER.getString()).get();
                        return itemStack;
                    } ,
                    (playerSelected, smartInventory1) -> {
                        // Do nothing.
            });
        });
    }

    public void deleteParty(Player player) {
        PartySelectorMenu.open(player, getMenu(),  PartySelectorMenu.Type.OWNED, (party, smartInventory) -> {
                AcceptDialogMenu.open(player, Message.PARTY_DELETE.getString(party.getPartyName()), Message.YES.getString(), Message.NO.getString(), aBoolean -> {
                    // If user accepts
                    if (aBoolean) {
                        boolean result = PartyUtils.deleteParty(party);
                        if (result) {
                            player.sendMessage(ChatColor.GREEN + Message.PARTY_DELETED.getString(ChatColor.WHITE + party.getPartyName() + ChatColor.GREEN));
                        } else {
                            player.sendMessage(ChatColor.RED+Message.PARTY_DOESNT_EXIST.getString(ChatColor.WHITE+party.getPartyName()+ChatColor.RED));
                        }
                        // Call deleteParty() to refresh party list.
                        deleteParty(player);
                    } else {
                        // If select no return to last menu
                        smartInventory.open(player);
                    }
                });
        });
    }

    public void partyInvites(Player player) {
        InvitesMenu.open(player, getMenu(), (invite, smartInventory) -> {
            AcceptDialogMenu.open(player, Message.PARTY_JOIN.getString(invite.getParty().getOwner().getName(), invite.getParty().getPartyName()),  Message.YES.getString(), Message.NO.getString(), aBoolean -> {
                if (aBoolean) {
                    PartyUtils.acceptInvite(player, invite);
                    getMenu().open(player);
                } else {
                    PartyUtils.rejectInvite(player, invite);
                    getMenu().open(player);
                }
            });
        });
    }

    public void leaveParty(Player player) {
        PartySelectorMenu.open(player, getMenu(), PartySelectorMenu.Type.MEMBER_OF, (party, smartInventory) -> {
            AcceptDialogMenu.open(player, Message.PARTY_LEAVE.getString(party.getPartyName()), Message.YES.getString(), Message.NO.getString(), aBoolean -> {
                if (aBoolean) {
                    party.removeMember(player);
                }
                smartInventory.open(player);
            });
        });
    }
}
