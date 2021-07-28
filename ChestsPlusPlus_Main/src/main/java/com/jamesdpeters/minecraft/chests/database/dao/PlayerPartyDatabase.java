package com.jamesdpeters.minecraft.chests.database.dao;

import com.jamesdpeters.minecraft.chests.database.DBUtil;
import com.jamesdpeters.minecraft.chests.database.entities.Player;
import com.jamesdpeters.minecraft.chests.database.entities.PlayerParty;
import com.jamesdpeters.minecraft.database.hibernate.Database;

public class PlayerPartyDatabase extends Database<PlayerParty> {

    public PlayerPartyDatabase() {
        super(PlayerParty.class);
    }

    public PlayerParty createParty(Player owner, String name) {
        PlayerParty playerParty = findParty(owner, name);

        if (playerParty != null)
            return playerParty;

        playerParty = new PlayerParty();
        playerParty.setOwner(owner);
        playerParty.setName(name);

        saveEntity(playerParty);
        DBUtil.PLAYER.refresh(owner);
        return playerParty;
    }

    public PlayerParty findParty(Player owner, String name) {
        var query = getQuery("from PlayerParty where owner.playerUUID = :ownerId and name = :name");
        query.setParameter("ownerId", owner.getPlayerUUID());
        query.setParameter("name", name);
        return query.getSingleResult();
    }

}
