package com.jamesdpeters.minecraft.chests;

import com.jamesdpeters.minecraft.chests.api_interfaces.MaterialChecker;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MaterialChecker_1_15 extends MaterialChecker {

    private List<Material> materials;

    MaterialChecker_1_15(){
        materials = new ArrayList<>();
        materials.addAll(DEFAULT_ITEMS);
        materials.addAll(Arrays.asList(
//                Material.DIRT
        ));
    }

    @Override
    protected List<Material> graphically2DList() {
        return materials;
    }
}
