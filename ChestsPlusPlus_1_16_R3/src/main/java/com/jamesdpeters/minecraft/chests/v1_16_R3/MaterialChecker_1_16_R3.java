package com.jamesdpeters.minecraft.chests.v1_16_R3;

import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.v1_16_R1.MaterialChecker_1_16;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Only a protocol change from 1.16.2 to 1.16.3 so no new materials were added.
 */
public class MaterialChecker_1_16_R3 extends MaterialChecker {

    private MaterialChecker_1_16 version1_16;

    public MaterialChecker_1_16_R3(){
        version1_16 = new MaterialChecker_1_16();
    }

    @Override
    public List<Material> graphically2DList() {
        return version1_16.graphically2DList();
    }

    @Override
    public List<Material> ignoredMaterials() {
        return version1_16.ignoredMaterials();
    }

    @Override
    public boolean isTool(ItemStack itemStack) {
        return version1_16.isTool(itemStack);
    }
}
