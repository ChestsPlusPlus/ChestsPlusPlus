package com.jamesdpeters.minecraft.chests.reflection.craftbukkit;

import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectMethod;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;
import com.jamesdpeters.minecraft.chests.reflection.minecraft.MinecraftServer;
import org.bukkit.Server;

public class CraftServer extends BaseReflection<Server> {

    public static final Class<?> clazz = ReflectionUtil.getCraftBukkitClass("CraftServer");
    private static final ReflectMethod getServer = ReflectionUtil.getMethod("getServer", clazz);

    static {
        assert clazz != null;
        assert getServer != null;
    }

    public CraftServer(Server server) {
        super(clazz, server);
    }

    public MinecraftServer getServer() {
        assert getServer != null;
        return new MinecraftServer(getServer.invoke(getOriginalObj()));
    }
}
