package com.jamesdpeters.minecraft.chests.reflection.mojangauth;

import com.jamesdpeters.minecraft.chests.reflection.helpers.BaseReflection;
import com.jamesdpeters.minecraft.chests.reflection.helpers.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.UUID;

public class GameProfile extends BaseReflection<Object> {

    public static final Class<?> clazz = ReflectionUtil.getMojangAuthClass("GameProfile");
    private static final Constructor<?> constructor = ReflectionUtil.getConstructor(clazz, UUID.class, String.class);

    public GameProfile(Object from) {
        super(clazz, from);
    }

    public static GameProfile fromOptional(Optional<?> from, UUID defaultUUID, String defaultName) {
        return new GameProfile(from.orElseGet(() -> {
            try {
                constructor.newInstance(defaultName, defaultName);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                return null;
            }
            return null;
        }));
    }
}
