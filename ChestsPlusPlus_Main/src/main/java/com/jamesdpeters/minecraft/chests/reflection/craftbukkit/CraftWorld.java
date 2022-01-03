package com.jamesdpeters.minecraft.chests.reflection.craftbukkit;

import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectMethod;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;
import com.jamesdpeters.minecraft.chests.reflection.minecraft.WorldServer;
import org.bukkit.World;

public class CraftWorld extends BaseReflection<World> {

    private static final Class<?> clazz = ReflectionUtil.getCraftBukkitClass("CraftWorld");

    public static final ReflectMethod getHandle = ReflectionUtil.getMethod("getHandle", clazz);

    static {
        assert clazz != null;
        assert getHandle != null;
    }

    public CraftWorld(World world) {
        super(clazz, world);
    }

    public WorldServer getWorldServer() {
        return new WorldServer(this);
    }
}
