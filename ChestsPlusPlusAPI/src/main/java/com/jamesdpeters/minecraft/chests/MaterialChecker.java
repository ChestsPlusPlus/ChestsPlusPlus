package com.jamesdpeters.minecraft.chests;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public abstract class MaterialChecker {


    /**
     * API-Specific implementation for materials checks.
     * Should return a list of materials where the texture for this item is displayed as 2D rather than a 3D model.
     * Should add the materials from the previous version (e.g 1.16 adds 1.15 which adds 1.14)
     * @return
     * List of Materials.
     */
    public abstract List<Material> graphically2DList();

    /**
     * List of @{@link Material} that gets ignored (Beds are a pain).
     * @return
     */
    public abstract List<Material> ignoredMaterials();

    /**
     * This returns true if an item is held like a pickaxe/sword etc.
     * Also for items like sticks an fishing rods.
     * @param itemStack
     * @return
     */
    public abstract boolean isTool(ItemStack itemStack);

    /**
     * Used to test if an item is graphically 2D (e.g a sign is a block but is held like an item.)
     * @param itemStack
     * @return
     */
    public boolean isGraphically2D(ItemStack itemStack){
        if (graphically2DList().contains(itemStack.getType())) return true;
        return !itemStack.getType().isBlock();
    }

    /**
     * Whether this item should be ignored when displaying. (Beds don't look good so are ignored.)
     * @param itemStack
     * @return
     */
    public boolean isIgnored(ItemStack itemStack){
        return ignoredMaterials().contains(itemStack.getType());
    }




}
