package com.jamesdpeters.minecraft.chests.database.dao;

import com.jamesdpeters.minecraft.chests.database.DBUtil;
import com.jamesdpeters.minecraft.chests.database.entities.CppPlayer;
import com.jamesdpeters.minecraft.chests.database.entities.PlayerParty;
import com.jamesdpeters.minecraft.chests.misc.BukkitFuture;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerPartyDatabase extends Database<PlayerParty> {

    public PlayerPartyDatabase() {
        super(PlayerParty.class);
    }

    public enum PartyResponse {
        NO_PARTY_EXISTS,
        MEMBER_NOT_PRESENT,
        COMPLETE
    }

    public CompletableFuture<PlayerParty> createParty(CppPlayer owner, String name) {
        return BukkitFuture.supplyAsync(() -> createPartyOnThread(owner, name));
    }

    public CompletableFuture<PlayerParty> createParty(OfflinePlayer owner, String name) {
        return BukkitFuture.supplyAsync(() -> {
            var ownerPlayer = DBUtil.PLAYER.findPlayer(owner).join();
            return createPartyOnThread(ownerPlayer, name);
        });
    }

    public CompletableFuture<Boolean> deleteParty(CppPlayer owner, String name) {
        return BukkitFuture.supplyAsync(() -> deletePartyOnThread(owner, name));
    }

    public CompletableFuture<Optional<PlayerParty>> findParty(CppPlayer owner, String name) {
        return BukkitFuture.supplyAsync(() -> findPartyOnThread(owner, name));
    }

    public CompletableFuture<Optional<PlayerParty>> findParty(OfflinePlayer owner, String name) {
        return BukkitFuture.supplyAsync(() -> {
            var ownerPlayer = DBUtil.PLAYER.findPlayer(owner).join();
            return findPartyOnThread(ownerPlayer, name);
        });
    }

    public CompletableFuture<PartyResponse> removePlayerFromParty(OfflinePlayer owner, String partyName, OfflinePlayer playerToRemove) {
        return BukkitFuture.supplyAsync(() -> {
            var ownerPlayer = DBUtil.PLAYER.findPlayer(owner).join();
            var party = ownerPlayer.getParty(partyName);
            if (party.isPresent()) {
                var p = party.get();
                var didRemove = p.removeMember(playerToRemove);
                if (didRemove) {
                    DBUtil.PLAYER.saveEntity(ownerPlayer);
                }
                return didRemove ? PartyResponse.COMPLETE : PartyResponse.MEMBER_NOT_PRESENT;
            }
            return PartyResponse.NO_PARTY_EXISTS;
        });
    }

    /* *******************
     * MAIN THREAD METHODS
     * *******************/

    private Optional<PlayerParty> findPartyOnThread(CppPlayer owner, String name) {
        var query = getQuery("from PlayerParty where owner.playerUUID = :ownerId and name = :name");
        query.setParameter("ownerId", owner.getPlayerUUID());
        query.setParameter("name", name);
        query.setMaxResults(1);
        var results = query.getResultList();
        if (results.size() == 1)
            return Optional.of(results.get(0));
        return Optional.empty();
    }

    private PlayerParty createPartyOnThread(CppPlayer owner, String name) {
        var playerPartyOptional = findPartyOnThread(owner, name);

        var playerParty = playerPartyOptional.orElseGet(() -> {
            var party = new PlayerParty();
            party.setOwner(owner);
            party.setName(name);
            return party;
        });

        saveEntity(playerParty);
        DBUtil.PLAYER.refresh(owner);
        return playerParty;
    }

    private boolean deletePartyOnThread(CppPlayer owner, String name) {
        var playerPartyOptional = findPartyOnThread(owner, name);
        if (playerPartyOptional.isPresent()) {
            var playerParty = playerPartyOptional.get();
            DBUtil.PARTIES.remove(playerParty);
            DBUtil.PLAYER.refresh(owner);
        }
        return false;
    }

    public void deleteParty(PlayerParty party) {
        DBUtil.PARTIES.remove(party);
        DBUtil.PLAYER.refresh(party.getOwner());
    }
}
