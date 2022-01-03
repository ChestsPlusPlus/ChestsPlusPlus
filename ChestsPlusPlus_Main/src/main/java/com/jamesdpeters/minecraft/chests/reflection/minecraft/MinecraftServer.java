package com.jamesdpeters.minecraft.chests.reflection.minecraft;


import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectMethod;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;
import com.jamesdpeters.minecraft.chests.reflection.mojangauth.GameProfile;

import java.util.Optional;
import java.util.UUID;

public class MinecraftServer extends BaseReflection<Object> {

    public static final Class<?> clazz = ReflectionUtil.getNmsClass("MinecraftServer");
    private static final Class<?> userCache = ReflectionUtil.getNmsClass("players.UserCache");
    private static final Class<?> playerList = ReflectionUtil.getNmsClass("players.PlayerList");


    private static final ReflectMethod getProfileCache = ReflectionUtil.getMethod("getProfileCache", clazz);
    private static final ReflectMethod userCache_get = ReflectionUtil.getMethod("get", userCache, UUID.class);

    private static final ReflectMethod getPlayerList = ReflectionUtil.getMethod("getPlayerList", clazz);
    private static final ReflectMethod playerList_loadEntity = ReflectionUtil.getMethod("load", playerList, EntityPlayer.clazz);
    private static final ReflectMethod playerList_saveEntity = ReflectionUtil.getMethod("save", playerList, EntityPlayer.clazz);

    static {
        assert clazz != null;
        assert userCache_get != null;
        assert getProfileCache != null;
        assert userCache_get != null;
    }

    public MinecraftServer(Object dediServer) {
        super(clazz, dediServer);
    }

    public GameProfile getGameProfile(UUID uuid, String defaultName) {
        assert getProfileCache != null;
        var profileCache = getProfileCache.invoke(getHandle());
        var opt = userCache_get.invoke(profileCache, uuid);
        return GameProfile.fromOptional((Optional<?>) opt, uuid, defaultName);
    }

    public void loadEntity(EntityPlayer entityPlayer) {
        var playerList = getPlayerList.invoke(getHandle());
        var result = playerList_loadEntity.invoke(playerList, entityPlayer.getHandle());
        entityPlayer.loadGameTypes(result);
    }

    public void saveEntity(EntityPlayer entityPlayer) {
        var playerList = getPlayerList.invoke(getHandle());
        playerList_saveEntity.invoke(playerList, entityPlayer.getHandle());
    }

}
