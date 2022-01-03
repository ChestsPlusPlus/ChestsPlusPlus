package com.jamesdpeters.minecraft.chests.reflection.minecraft;


import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectMethod;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;
import com.jamesdpeters.minecraft.chests.reflection.mojangauth.GameProfile;

import java.lang.reflect.Constructor;
import java.util.Objects;

public class EntityPlayer extends BaseReflection<Object> {

    public static final Class<?> clazz = ReflectionUtil.getNmsClass("level.EntityPlayer");
    private static final Constructor<?> constructor = ReflectionUtil.getConstructor(clazz, MinecraftServer.clazz, WorldServer.clazz, GameProfile.clazz);

    private static final Class<?> nbtTagCompoundClazz = ReflectionUtil.getNbtClass("NBTTagCompound");
    private static final ReflectMethod loadGameTypes = ReflectionUtil.getMethod("loadGameTypes", clazz, nbtTagCompoundClazz);

    static {
        assert clazz != null;
        assert constructor != null;
    }

    public EntityPlayer(MinecraftServer minecraftServer, WorldServer worldServer, GameProfile gameProfile) throws NoSuchMethodException {
        super(clazz, Objects.requireNonNull(constructor), minecraftServer, worldServer, gameProfile);
    }

    public EntityPlayer(Object object) {
        super(clazz, object);
    }

    public void loadGameTypes(Object nbtTagCompound) {
        Object tag = nbtTagCompoundClazz.cast(nbtTagCompound);
        loadGameTypes.invoke(getHandle(), tag);
    }
}
