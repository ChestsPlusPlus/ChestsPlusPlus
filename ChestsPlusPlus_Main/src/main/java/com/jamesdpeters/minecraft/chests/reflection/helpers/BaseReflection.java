package com.jamesdpeters.minecraft.chests.reflection.helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class BaseReflection<T> {

    protected Object handle;
    private final T originalObj;
    private final Class<?> clazz;

    public BaseReflection(Class<?> clazz, T from) {
        assert clazz.isAssignableFrom(from.getClass());
        this.handle = clazz.cast(from);
        this.clazz = clazz;
        this.originalObj = from;
    }

    public BaseReflection(Class<?> clazz, Constructor<?> constructor, BaseReflection<?>... parameters) throws NoSuchMethodException {
        this.clazz = clazz;
        this.originalObj = null;
        try {
            var handles = Arrays.stream(parameters).map(BaseReflection::getHandle).toArray();
            this.handle = constructor.newInstance(handles);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public T getOriginalObj() {
        return originalObj;
    }

    public Object getHandle() {
        return handle;
    }

    public Class<?> getClazz() {
        return clazz;
    }
}
