package com.jamesdpeters.minecraft.chests.party;

import com.jamesdpeters.minecraft.chests.database.entities.CppPlayer;
import com.jamesdpeters.minecraft.chests.database.entities.PlayerParty;
import com.jamesdpeters.minecraft.chests.lang.Message;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PartyInvite {

    CppPlayer owner, player;
    PlayerParty party;
    boolean pending;

    public PartyInvite(CppPlayer owner, CppPlayer player, PlayerParty party) {
        this.owner = owner;
        this.player = player;
        this.party = party;
    }

    public void sendInvite() {
        OfflinePlayer offlinePlayer = player.getOfflinePlayer();
        Player onlinePlayer = player.getOfflinePlayer().getPlayer();
        if(onlinePlayer != null) {
            onlinePlayer.sendMessage(ChatColor.GREEN+Message.PARTY_INVITE.getString(ChatColor.WHITE+ player.getName()+ChatColor.GREEN, ChatColor.WHITE+party.getName()+ChatColor.GREEN));
            String tellraw = "tellraw @p {\"text\":\""+Message.PARTY_ACCEPT_INVITE.getString()+"\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/c++ party view-invites\"}}";
            onlinePlayer.performCommand(tellraw);
        }
        Player onlineOwner = owner.getOfflinePlayer().getPlayer();
        if (onlineOwner != null){
            onlineOwner.sendMessage(ChatColor.GREEN+Message.PARTY_INVITE_OWNER.getString(ChatColor.WHITE+ player.getName()+ChatColor.GREEN, ChatColor.WHITE+party.getName()+ChatColor.GREEN));
        }
        pending = true;
    }

    public void acceptInvite(){
        if (pending){
            party.addMember(player);
            Player onlinePlayer = player.getOfflinePlayer().getPlayer();
            if (onlinePlayer != null){
                onlinePlayer.sendMessage(ChatColor.GREEN+Message.PARTY_JOINED.getString(ChatColor.WHITE+owner.getName()+ChatColor.GREEN, ChatColor.WHITE+party.getName()+ChatColor.GREEN));
            }
            pending = false;
        }
    }

    public void rejectInvite(){
        if (pending) {
            pending = false;
        }
    }

    public PlayerParty getParty() {
        return party;
    }
}
