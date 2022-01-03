package com.jamesdpeters.minecraft.chests.reflection.craftbukkit;

import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectMethod;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;
import com.jamesdpeters.minecraft.chests.reflection.minecraft.EntityPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.util.Objects;

public class CraftPlayer extends BaseReflection<Player> {

    public static final Class<?> clazz = ReflectionUtil.getCraftBukkitClass("entity.CraftPlayer");
    private static final Constructor<?> constructor = ReflectionUtil.getConstructor(clazz, CraftServer.clazz, EntityPlayer.clazz);

    private static final ReflectMethod getHandle = ReflectionUtil.getMethod("getHandle", clazz);

    static {
        assert clazz != null;
        assert constructor != null;
    }

    public CraftPlayer(CraftServer craftServer, EntityPlayer entityPlayer) throws NoSuchMethodException {
        super(clazz, Objects.requireNonNull(constructor), craftServer, entityPlayer);
    }

    public CraftPlayer(Player player) {
        super(clazz, player);
    }

    @Override
    public Player getOriginalObj() {
        return (Player) getHandle();
    }

    public EntityPlayer getEntityPlayer() {
        return new EntityPlayer(getHandle.invoke(getHandle()));
    }
}
