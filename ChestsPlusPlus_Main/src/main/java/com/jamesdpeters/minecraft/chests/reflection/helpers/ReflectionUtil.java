package com.jamesdpeters.minecraft.chests.reflection.helpers;

import org.bukkit.Bukkit;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;

public class ReflectionUtil {

    private static final String version;

    static {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        var tempVersion = packageName.substring(packageName.lastIndexOf(".") + 1);
        if (tempVersion.equals("craftbukkit"))
            version = "";
        else version = tempVersion;
    }

    public static String getNmsPrefix() {
        return "net.minecraft.server." + getVersionPrefix() ;
    }

    private static String getVersionPrefix() {
        return version.isEmpty() ? "" : version + ".";
    }

    public static String getCraftBukkitPrefix() {
        return "org.bukkit.craftbukkit." +getVersionPrefix();
    }

    public static Class<?> getNmsClass(String name) {
        return getClass("net.minecraft.server." + name , false);
    }

    public static Class<?> getNbtClass(String name) {
        return getClass("net.minecraft.nbt." + name , false);
    }

    public static Class<?> getNmsClassAsArray(String name) {
        return getClass("net.minecraft.server." + name , true);
    }

    public static Class<?> getCraftBukkitClass(String name) {
        return getClass("org.bukkit.craftbukkit." + getVersionPrefix() + name , false);
    }

    public static Class<?> getCraftBukkitClassAsArray(String name) {
        return getClass("org.bukkit.craftbukkit." + getVersionPrefix() + name , true);
    }

    public static Class<?> getMojangAuthClass(String name) {
        return getClass("com.mojang.authlib." + name , false);
    }

    private static Class<?> getClass(String name, boolean asArray) {
        try {
            if(asArray) return Array.newInstance(Class.forName(name), 0).getClass();
            else return Class.forName(name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ReflectMethod getMethod(String name , Class<?> clazz , Class<?>... parameterClasses) {
        try {
            return new ReflectMethod(clazz.getDeclaredMethod(name, parameterClasses));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... constructorParameters) {
        try {
            return clazz.getConstructor(constructorParameters);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }



}
