package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialChecker_1_16 extends MaterialChecker {

    private List<Material> materials;

    public MaterialChecker_1_16(){
        materials = new ArrayList<>();
        //Add previous API additions.
        materials.addAll(new MaterialChecker_1_15().graphically2DList());
        materials.addAll(Arrays.asList(
//                Material.DIRT
        ));
        API.getPlugin().getLogger().info("Loaded Material Checker 1.16");
    }

    @Override
    protected List<Material> graphically2DList() {
        return materials;
    }

}
