package com.jamesdpeters.minecraft.chests.party;

import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PartyUtils {

    public enum PARTY_STATUS {
        PARTY_ALREADY_EXISTS,
        PARTY_CREATED
    }

    // Stores party invites sent to this player.
    // Only stored in memory
    private static final HashMap<UUID, List<PartyInvite>> partyInvites = new HashMap<>();

    public static boolean hasInvites(OfflinePlayer player) {
        List<PartyInvite> invites = partyInvites.get(player.getUniqueId());
        if (invites == null) return false;
        return invites.size() > 0;
    }

    public static List<PartyInvite> getPartyInvites(OfflinePlayer player) {
        return partyInvites.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
    }

    /**
     * Invites a player to the owners given party. The last pending invite is overwritten.
     * @param owner
     * @param playerToInvite
     * @param partyName
     */
    public static void invitePlayer(OfflinePlayer owner, OfflinePlayer playerToInvite, String partyName){
        PlayerPartyStorage storage = getPlayerPartyStorage(owner);

        PlayerParty party = storage.getOwnedParties().get(partyName);
        if (party == null){
            Player onlineOwner = owner.getPlayer();
            if(onlineOwner != null) {
                onlineOwner.sendMessage(Message.PARTY_DOESNT_EXIST.getString(partyName));
            }
            return;
        }

        invitePlayer(party, playerToInvite);
    }

    private static void addPlayerInvite(OfflinePlayer player, PartyInvite invite) {
        List<PartyInvite> invites = partyInvites.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        invites.add(invite);
    }

    public static void invitePlayer(PlayerParty party, OfflinePlayer playerToInvite) {
        PartyInvite invite = new PartyInvite(party.getOwner(), playerToInvite, party);
        addPlayerInvite(playerToInvite, invite);
        invite.sendInvite();
    }

    public static void removePlayer(OfflinePlayer owner, OfflinePlayer playerToRemove, String partyName){
        PlayerPartyStorage storage = getPlayerPartyStorage(owner);

        PlayerParty party = storage.getOwnedParties().get(partyName);
        if (party == null){
            Player onlineOwner = owner.getPlayer();
            if(onlineOwner != null) {
                onlineOwner.sendMessage(Message.PARTY_DOESNT_EXIST.getString(partyName));
            }
            return;
        }

        party.removeMember(playerToRemove);
    }

    /**
     * Accepts the current pending invite for this player.
     * @param player
     */
    public static void acceptInvite(OfflinePlayer player, PartyInvite invite){
        List<PartyInvite> invites = partyInvites.get(player.getUniqueId());
        if (invite != null) {
            invite.acceptInvite();
            invites.remove(invite);
        }
    }

    public static void rejectInvite(OfflinePlayer player, PartyInvite invite) {
        List<PartyInvite> invites = partyInvites.get(player.getUniqueId());
        if (invite != null) {
            invite.rejectInvite();
            invites.remove(invite);
        }
    }

    /**
     * Creates a party.
     * @param owner
     * @param partyName
     * @return false if party already exists.
     */
    public static boolean createParty(OfflinePlayer owner, String partyName){
        PlayerPartyStorage storage = getPlayerPartyStorage(owner);

        // Check if party already exists.
        if (storage.getOwnedParties().containsKey(partyName)) return false;

        storage.getOwnedParties().put(partyName, new PlayerParty(owner, partyName));
        return true;
    }

    public static boolean deleteParty(OfflinePlayer owner, String partyName){
        HashMap<String, PlayerPartyStorage> map = Config.getStore().parties;
        PlayerPartyStorage storage = map.get(owner.getUniqueId().toString());
        if (storage == null) {
            storage = new PlayerPartyStorage(owner);
            map.put(owner.getUniqueId().toString(), storage);
        }

        // Check if party already exists.
        if (!storage.getOwnedParties().containsKey(partyName)) {
            Player onlineOwner = owner.getPlayer();
            if (onlineOwner != null) {
                onlineOwner.sendMessage(ChatColor.RED + Message.PARTY_DOESNT_EXIST.getString(partyName));
            }
            return false;
        }

        // Remove party
        storage.getOwnedParties().remove(partyName);
        return true;
    }

    public static boolean deleteParty(PlayerParty party){
        return deleteParty(party.getOwner(), party.getPartyName());
    }

    public static PlayerPartyStorage getPlayerPartyStorage(OfflinePlayer owner) {
        HashMap<String, PlayerPartyStorage> map = Config.getStore().parties;
        PlayerPartyStorage storage = map.get(owner.getUniqueId().toString());
        if (storage == null){
            storage = new PlayerPartyStorage(owner);
            map.put(owner.getUniqueId().toString(), storage);
        }
        return storage;
    }
}
