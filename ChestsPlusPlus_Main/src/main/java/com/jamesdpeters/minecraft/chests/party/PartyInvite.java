package com.jamesdpeters.minecraft.chests.party;

import com.jamesdpeters.minecraft.chests.lang.Message;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PartyInvite {

    OfflinePlayer owner, player;
    PlayerParty party;
    boolean pending;

    public PartyInvite(OfflinePlayer owner, OfflinePlayer player, PlayerParty party) {
        this.owner = owner;
        this.player = player;
        this.party = party;
    }

    public void sendInvite() {
        Player onlinePlayer = player.getPlayer();
        if(onlinePlayer != null) {
            onlinePlayer.sendMessage(ChatColor.GREEN+Message.PARTY_INVITE.getString(ChatColor.WHITE+player.getName()+ChatColor.GREEN, ChatColor.WHITE+party.getPartyName()+ChatColor.GREEN));
            String tellraw = "tellraw @p {\"text\":\""+Message.PARTY_ACCEPT_INVITE.getString()+"\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/c++ party view-invites\"}}";
            onlinePlayer.performCommand(tellraw);
        }
        Player onlineOwner = owner.getPlayer();
        if (onlineOwner != null){
            onlineOwner.sendMessage(ChatColor.GREEN+Message.PARTY_INVITE_OWNER.getString(ChatColor.WHITE+player.getName()+ChatColor.GREEN, ChatColor.WHITE+party.getPartyName()+ChatColor.GREEN));
        }
        pending = true;
    }

    public void acceptInvite(){
        if (pending){
            party.addMember(player);
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer != null){
                onlinePlayer.sendMessage(ChatColor.GREEN+Message.PARTY_JOINED.getString(ChatColor.WHITE+owner.getName()+ChatColor.GREEN, ChatColor.WHITE+party.getPartyName()+ChatColor.GREEN));
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
