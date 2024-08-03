package com.jamesdpeters.minecraft.chests.v1_21_R1;

import com.jamesdpeters.minecraft.chests.BaseMaterialChecker;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MaterialChecker_1_21_R1 extends MaterialChecker {

    private final BaseMaterialChecker baseMaterialChecker;

    public MaterialChecker_1_21_R1(){
        baseMaterialChecker = new BaseMaterialChecker();
    }

    @Override
    public List<Material> graphically2DList() {
        return baseMaterialChecker.graphically2DList();
    }

    @Override
    public List<Material> ignoredMaterials() {
        return baseMaterialChecker.ignoredMaterials();
    }

    @Override
    public boolean isTool(ItemStack itemStack) {
        return baseMaterialChecker.isTool(itemStack);
    }
}
