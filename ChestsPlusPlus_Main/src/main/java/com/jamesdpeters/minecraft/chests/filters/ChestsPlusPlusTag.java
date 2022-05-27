package com.jamesdpeters.minecraft.chests.filters;

import com.google.common.collect.Sets;
import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@SuppressWarnings("rawtypes")
public class ChestsPlusPlusTag {

    public static Tag SEEDS;

    static {
        SEEDS = new Tag() {

            @Override
            public boolean isTagged(@NotNull Keyed item) {
                return item.equals(Material.WHEAT_SEEDS) || item.equals(Material.PUMPKIN_SEEDS) || item.equals(Material.MELON_SEEDS) || item.equals(Material.BEETROOT_SEEDS);
            }

            @NotNull
            @Override
            public Set<Material> getValues() {
                return Sets.newHashSet(Material.WHEAT_SEEDS, Material.PUMPKIN_SEEDS, Material.MELON_SEEDS, Material.BEETROOT_SEEDS);
            }

            @NotNull
            @Override
            public NamespacedKey getKey() {
                return new NamespacedKey(ChestsPlusPlus.PLUGIN, "seeds");
            }
        };
    }
}