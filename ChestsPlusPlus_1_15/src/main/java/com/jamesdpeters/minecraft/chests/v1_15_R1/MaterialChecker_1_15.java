package com.jamesdpeters.minecraft.chests.v1_15_R1;


import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.v1_14_R1.MaterialChecker_1_14;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MaterialChecker_1_15 extends MaterialChecker {

    private List<Material> materials;
    private List<Material> ignoredMaterials;
    private MaterialChecker materialChecker1_14;

    public MaterialChecker_1_15(){
        //Must add previous version values first!
        materialChecker1_14 = new MaterialChecker_1_14();
        materials = new ArrayList<>();
        materials.addAll(materialChecker1_14.graphically2DList());

        ignoredMaterials = new ArrayList<>();
        ignoredMaterials.addAll(materialChecker1_14.ignoredMaterials());
    }

    @Override
    public List<Material> graphically2DList() {
        return materials;
    }

    @Override
    public List<Material> ignoredMaterials() {
        return ignoredMaterials;
    }

    @Override
    public boolean isTool(ItemStack itemStack) {
        //Check previous version first.
        if(materialChecker1_14.isTool(itemStack)) return true;
        return false;
    }

}
