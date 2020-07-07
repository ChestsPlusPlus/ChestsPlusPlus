package com.jamesdpeters.minecraft.chests.v1_16_R1;

import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.v1_15_R1.MaterialChecker_1_15;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MaterialChecker_1_16 extends MaterialChecker {

    private List<Material> materials;
    private List<Material> ignoredMaterials;
    private MaterialChecker_1_15 version1_15;

    public MaterialChecker_1_16(){
        version1_15 = new MaterialChecker_1_15();

        materials = new ArrayList<>();
        materials.addAll(version1_15.graphically2DList());
        materials.addAll(Tag.CROPS.getValues());
        materials.addAll(Tag.TALL_FLOWERS.getValues());
        materials.addAll(Tag.FLOWERS.getValues());
        materials.add(Material.WARPED_FUNGUS);
        materials.add(Material.WARPED_ROOTS);
        materials.add(Material.TWISTING_VINES);
        materials.add(Material.NETHER_SPROUTS);
        materials.add(Material.WEEPING_VINES);
        materials.add(Material.CRIMSON_ROOTS);
        materials.add(Material.CRIMSON_FUNGUS);
        materials.add(Material.SOUL_CAMPFIRE);
        materials.add(Material.SOUL_LANTERN);
        materials.add(Material.CHAIN);

        ignoredMaterials = new ArrayList<>();
        ignoredMaterials.addAll(version1_15.ignoredMaterials());
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
        if(itemStack.getType() == Material.WARPED_FUNGUS_ON_A_STICK) return true;
        return version1_15.isTool(itemStack);
    }
}
