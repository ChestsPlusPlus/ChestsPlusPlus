package com.jamesdpeters.minecraft.chests.filters;


import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ExtraTag implements Tag<Material> {

    public static final Tag<Material> DYES = new ExtraTag("dyes", Material.BLACK_DYE, Material.RED_DYE, Material.GREEN_DYE, Material.BROWN_DYE, Material.BLUE_DYE, Material.PURPLE_DYE, Material.CYAN_DYE, Material.LIGHT_GRAY_DYE, Material.GRAY_DYE, Material.PINK_DYE, Material.LIME_DYE, Material.YELLOW_DYE, Material.LIGHT_BLUE_DYE, Material.MAGENTA_DYE, Material.ORANGE_DYE, Material.WHITE_DYE);
    public static final Tag<Material> BEDS = new ExtraTag("beds", Material.BLACK_BED, Material.RED_BED, Material.GREEN_BED, Material.BROWN_BED, Material.BLUE_BED, Material.PURPLE_BED, Material.CYAN_BED, Material.LIGHT_GRAY_BED, Material.GRAY_BED, Material.PINK_BED, Material.LIME_BED, Material.YELLOW_BED, Material.LIGHT_BLUE_BED, Material.MAGENTA_BED, Material.ORANGE_BED, Material.WHITE_BED);

    NamespacedKey namespacedKey;
    Set<Material> values;

    public ExtraTag(String tag, Material... materials){
        namespacedKey = new NamespacedKey(ChestsPlusPlus.PLUGIN, tag);
        values = new HashSet<>(Arrays.asList(materials));
    }

    @Override
    public boolean isTagged(@NotNull Material item) {
        return values.contains(item);
    }

    @Override
    public Set<Material> getValues() {
        return values;
    }

    @Override
    public NamespacedKey getKey() {
        return namespacedKey;
    }
}
