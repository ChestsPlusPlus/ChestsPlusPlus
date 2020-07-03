package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialChecker_1_15 extends MaterialChecker {

    private List<Material> materials;
    private List<Material> ignoredMaterials;

    public MaterialChecker_1_15(){
        materials = new ArrayList<>();
        materials.addAll(version_1_14_Items);

        ignoredMaterials = new ArrayList<>();
        ignoredMaterials.addAll(version_1_14_Ignored_Items);
    }

    @Override
    protected List<Material> graphically2DList() {
        return materials;
    }

    @Override
    protected List<Material> ignoredMaterials() {
        return ignoredMaterials;
    }

}
