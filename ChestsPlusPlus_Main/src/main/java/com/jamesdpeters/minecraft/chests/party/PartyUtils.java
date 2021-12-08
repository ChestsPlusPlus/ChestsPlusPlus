package com.jamesdpeters.minecraft.chests.party;

import com.jamesdpeters.minecraft.chests.database.DBUtil;
import com.jamesdpeters.minecraft.chests.database.dao.PlayerDatabase;
import com.jamesdpeters.minecraft.chests.database.dao.PlayerPartyDatabase;
import com.jamesdpeters.minecraft.chests.database.entities.PlayerParty;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.BukkitFuture;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PartyUtils {

    public enum PARTY_STATUS {
        PARTY_ALREADY_EXISTS,
        PARTY_CREATED
    }

    private static final PlayerPartyDatabase partyDb = DBUtil.PARTIES;
    private static final PlayerDatabase playerDb = DBUtil.PLAYER;

    // Stores party invites sent to this cppPlayer.
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
     * Invites a cppPlayer to the owners given party. The last pending invite is overwritten.
     * @param owner
     * @param playerToInvite
     * @param partyName
     */
    public static void invitePlayer(OfflinePlayer owner, OfflinePlayer playerToInvite, String partyName){
        playerDb.findPlayer(owner).thenAccept(player -> {
            var party = player.getParty(partyName);
            if (party.isPresent()) {
                invitePlayer(party.get(), playerToInvite);
            } else {
                var onlinePlayer = owner.getPlayer();
                if (onlinePlayer != null) {
                    onlinePlayer.sendMessage(Message.PARTY_DOESNT_EXIST.getString(partyName));
                }
            }
        });
    }

    private static void addPlayerInvite(OfflinePlayer player, PartyInvite invite) {
        List<PartyInvite> invites = partyInvites.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        invites.add(invite);
    }

    public static void invitePlayer(PlayerParty party, OfflinePlayer playerToInvite) {
        playerDb.findPlayer(playerToInvite).thenAccept(player -> {
            PartyInvite invite = new PartyInvite(party.getOwner(), player, party);
            addPlayerInvite(playerToInvite, invite);
            invite.sendInvite();
        });
    }

    public static void removePlayer(OfflinePlayer owner, OfflinePlayer playerToRemove, String partyName){
        partyDb
            .removePlayerFromParty(owner, partyName, playerToRemove)
            .thenAccept(partyResponse -> {
                var onlinePlayer = owner.getPlayer();
                if (onlinePlayer != null) {
                    if (partyResponse == PlayerPartyDatabase.PartyResponse.NO_PARTY_EXISTS) {
                        onlinePlayer.sendMessage(Message.PARTY_DOESNT_EXIST.getString(partyName));
                    }
                }
            });
    }

    /**
     * Accepts the current pending invite for this cppPlayer.
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
    public static CompletableFuture<Boolean> createParty(OfflinePlayer owner, String partyName){
        return BukkitFuture.supplyAsync(() -> {
            var party = DBUtil.PARTIES.findParty(owner, partyName).join();
            if (party.isPresent())
                return false;

            DBUtil.PARTIES.createParty(owner, partyName);
            return true;
        });
    }

    public static boolean deleteParty(PlayerParty party){
        DBUtil.PARTIES.remove(party);
        return true;
    }
}
