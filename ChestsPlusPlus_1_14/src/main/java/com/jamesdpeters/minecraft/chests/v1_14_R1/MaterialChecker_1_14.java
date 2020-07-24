package com.jamesdpeters.minecraft.chests.v1_14_R1;

import com.jamesdpeters.minecraft.chests.MaterialChecker;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MaterialChecker_1_14 extends MaterialChecker {

    static List<Material> version_1_14_Items;
    static List<Material> version_1_14_Ignored_Items;

    public MaterialChecker_1_14(){
        version_1_14_Items = new ArrayList<>();
        version_1_14_Items.addAll(Tag.SIGNS.getValues());
        version_1_14_Items.addAll(Tag.WALL_SIGNS.getValues());
        version_1_14_Items.addAll(Tag.DOORS.getValues());
        version_1_14_Items.addAll(Tag.SAPLINGS.getValues());
        version_1_14_Items.addAll(Tag.SMALL_FLOWERS.getValues());
        version_1_14_Items.addAll(Tag.RAILS.getValues());
        version_1_14_Items.addAll(Tag.CORAL_PLANTS.getValues());
        version_1_14_Items.addAll(getGlassPanes());
        version_1_14_Items.add(Material.BROWN_MUSHROOM);
        version_1_14_Items.add(Material.RED_MUSHROOM);
        version_1_14_Items.add(Material.END_ROD);
        version_1_14_Items.add(Material.COBWEB);
        version_1_14_Items.add(Material.GRASS);
        version_1_14_Items.add(Material.FERN);
        version_1_14_Items.add(Material.DEAD_BUSH);
        version_1_14_Items.add(Material.SEAGRASS);
        version_1_14_Items.add(Material.SEA_PICKLE);
        version_1_14_Items.add(Material.LADDER);
        version_1_14_Items.add(Material.IRON_BARS);
        version_1_14_Items.add(Material.VINE);
        version_1_14_Items.add(Material.LILY_PAD);
        version_1_14_Items.add(Material.SUNFLOWER);
        version_1_14_Items.add(Material.LILAC);
        version_1_14_Items.add(Material.ROSE_BUSH);
        version_1_14_Items.add(Material.PEONY);
        version_1_14_Items.add(Material.TALL_GRASS);
        version_1_14_Items.add(Material.LARGE_FERN);
        version_1_14_Items.add(Material.BELL);
        version_1_14_Items.add(Material.CAMPFIRE);
        version_1_14_Items.add(Material.LANTERN);
        version_1_14_Items.add(Material.TURTLE_EGG);
        version_1_14_Items.add(Material.SUGAR_CANE);
        version_1_14_Items.add(Material.KELP);
        version_1_14_Items.add(Material.BAMBOO);
        version_1_14_Items.add(Material.LEVER);
        version_1_14_Items.add(Material.TRIPWIRE_HOOK);
        version_1_14_Items.add(Material.REPEATER);
        version_1_14_Items.add(Material.COMPARATOR);
        version_1_14_Items.add(Material.CAULDRON);
        version_1_14_Items.add(Material.BREWING_STAND);
        version_1_14_Items.add(Material.HOPPER);
        version_1_14_Items.add(Material.TORCH);
        version_1_14_Items.add(Material.BONE);
        version_1_14_Items.add(Material.BLAZE_ROD);
        version_1_14_Items.add(Material.WHEAT);

        version_1_14_Ignored_Items = new ArrayList<>();
        version_1_14_Ignored_Items.addAll(Tag.BEDS.getValues());
        version_1_14_Ignored_Items.addAll(Tag.BANNERS.getValues());
        version_1_14_Ignored_Items.add(Material.DRAGON_HEAD);
        version_1_14_Ignored_Items.add(Material.PLAYER_HEAD);
        version_1_14_Ignored_Items.add(Material.ZOMBIE_HEAD);
        version_1_14_Ignored_Items.add(Material.SKELETON_SKULL);
        version_1_14_Ignored_Items.add(Material.CREEPER_HEAD);
        version_1_14_Ignored_Items.add(Material.SHIELD);
        version_1_14_Ignored_Items.add(Material.CROSSBOW);
        version_1_14_Ignored_Items.add(Material.TRIDENT);
    }

    private static List<Material> getGlassPanes(){
        List<Material> materials = new ArrayList<>();
        materials.add(Material.GLASS_PANE);
        materials.add(Material.BLACK_STAINED_GLASS_PANE);
        materials.add(Material.BLUE_STAINED_GLASS_PANE);
        materials.add(Material.BROWN_STAINED_GLASS_PANE);
        materials.add(Material.CYAN_STAINED_GLASS_PANE);
        materials.add(Material.GRAY_STAINED_GLASS_PANE);
        materials.add(Material.GREEN_STAINED_GLASS_PANE);
        materials.add(Material.LIME_STAINED_GLASS_PANE);
        materials.add(Material.MAGENTA_STAINED_GLASS_PANE);
        materials.add(Material.ORANGE_STAINED_GLASS_PANE);
        materials.add(Material.PINK_STAINED_GLASS_PANE);
        materials.add(Material.PURPLE_STAINED_GLASS_PANE);
        materials.add(Material.RED_STAINED_GLASS_PANE);
        materials.add(Material.WHITE_STAINED_GLASS_PANE);
        materials.add(Material.YELLOW_STAINED_GLASS_PANE);
        materials.add(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        materials.add(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        return materials;
    }

    @Override
    public boolean isTool(ItemStack itemStack){
        String matType = itemStack.getType().toString();
        if(matType.contains("AXE")) return true;
        if(matType.contains("SWORD")) return true;
        if(matType.contains("PICKAXE")) return true;
        if(matType.contains("HOE")) return true;
        if(matType.contains("SHOVEL")) return true;
        if(itemStack.getType() == Material.FISHING_ROD) return true;
        if(itemStack.getType() == Material.CARROT_ON_A_STICK) return true;
        if(itemStack.getType() == Material.STICK) return true;
        return false;
    }

    @Override
    public List<Material> graphically2DList() {
        return version_1_14_Items;
    }

    @Override
    public List<Material> ignoredMaterials() {
        return version_1_14_Ignored_Items;
    }

}
