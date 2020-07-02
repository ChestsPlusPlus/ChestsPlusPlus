package com.jamesdpeters.minecraft.chests.api_interfaces;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public abstract class MaterialChecker {

    public static List<Material> DEFAULT_ITEMS = Arrays.asList(
            //Signs
            Material.ACACIA_SIGN,
            Material.ACACIA_WALL_SIGN,
            Material.BIRCH_SIGN,
            Material.BIRCH_WALL_SIGN,
            Material.DARK_OAK_SIGN,
            Material.DARK_OAK_WALL_SIGN,
            Material.JUNGLE_SIGN,
            Material.JUNGLE_WALL_SIGN,
            Material.OAK_SIGN,
            Material.OAK_WALL_SIGN,
            Material.SPRUCE_SIGN,
            Material.SPRUCE_WALL_SIGN,
            //Doors
            Material.ACACIA_DOOR,
            Material.BIRCH_DOOR,
            Material.DARK_OAK_DOOR,
            Material.JUNGLE_DOOR,
            Material.OAK_DOOR,
            Material.SPRUCE_DOOR,
            Material.IRON_DOOR,
            //Saplings
            Material.SPRUCE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.BAMBOO_SAPLING,
            Material.BIRCH_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.OAK_SAPLING
    );

    public static MaterialChecker DEFAULT = new MaterialChecker() {
        @Override
        protected List<Material> graphically2DList() {
            return DEFAULT_ITEMS;
        }
    };

    /**
     * API-Specific implementation for materials checks.
     * Should return a list of materials where the texture for this item is displayed as 2D rather than a 3D model.
     * @return
     * List of Materials.
     */
    protected abstract List<Material> graphically2DList();

    /**
     * Used to test if an item is graphically a block (e.g a sign is a block but is held like an item.)
     * @param itemStack
     * @return
     */
    public boolean isGraphically2D(ItemStack itemStack){
        return graphically2DList().contains(itemStack.getType());
    }

}
