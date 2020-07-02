package com.jamesdpeters.minecraft.chests.sort;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum SortMethod {
    OFF,
    NAME,
    AMOUNT_DESC,
    AMOUNT_ASC;

    public static List<String> valuesList;

    static {
        valuesList = Stream.of(SortMethod.values()).map(SortMethod::toString).collect(Collectors.toList());
    }
}
