package com.jamesdpeters.minecraft.chests.reflection.minecraft;

import com.jamesdpeters.minecraft.chests.reflection.craftbukkit.CraftWorld;
import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;

import java.util.Objects;

public class WorldServer extends BaseReflection<Object> {

    public static final Class<?> clazz = ReflectionUtil.getNmsClass("level.WorldServer");

    static {
        assert clazz != null;
    }

    public WorldServer(CraftWorld craftWorld) {
        super(clazz, Objects.requireNonNull(CraftWorld.getHandle).invoke(craftWorld.getHandle()));
    }

}
