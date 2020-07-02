package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.api_interfaces.MaterialChecker;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialChecker_1_16 extends MaterialChecker {

    private List<Material> materials;

    MaterialChecker_1_16(){
        materials = new ArrayList<>();
        materials.addAll(DEFAULT_ITEMS);
        //Add previous API additions.
        materials.addAll(new MaterialChecker_1_15().graphically2DList());
        materials.addAll(Arrays.asList(
//                Material.DIRT
        ));
    }

    @Override
    protected List<Material> graphically2DList() {
        return materials;
    }

}
