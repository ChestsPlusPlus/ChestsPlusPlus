package com.jamesdpeters.minecraft.chests.database;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.database.dao.PlayerDatabase;
import com.jamesdpeters.minecraft.chests.database.dao.PlayerPartyDatabase;
import com.jamesdpeters.minecraft.chests.database.entities.CppPlayer;
import com.jamesdpeters.minecraft.chests.database.entities.PlayerParty;
import com.jamesdpeters.minecraft.database.hibernate.HibernateUtil;

public class DBUtil {

    /**
     * ALL HIBERNATE ENTITIES
     */
    private final static Class<?>[] entities = {
        CppPlayer.class,
        PlayerParty.class
    };

    public final static PlayerDatabase PLAYER = new PlayerDatabase();
    public final static PlayerPartyDatabase PARTIES = new PlayerPartyDatabase();

    public static void init() {
        HibernateUtil.init(ChestsPlusPlus.PLUGIN.getDataFolder(), entities);
    }

}
