package com.jamesdpeters.minecraft.chests.reflection.helpers;

import java.lang.reflect.Method;

public class ReflectMethod {

    private final Method method;

    public ReflectMethod(Method method) {
        method.setAccessible(true);
        this.method = method;
    }

    public Object invoke(Object instance, Object... args) {
        try {
            return method.invoke(instance, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
