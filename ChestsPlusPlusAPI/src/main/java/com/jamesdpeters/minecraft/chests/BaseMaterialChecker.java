package com.jamesdpeters.minecraft.chests;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BaseMaterialChecker extends MaterialChecker {
    static List<Material> version_Items;
    static List<Material> version_Ignored_Items;

    public BaseMaterialChecker(){
        version_Items = new ArrayList<>();
        version_Items.addAll(Tag.SIGNS.getValues());
        version_Items.addAll(Tag.WALL_SIGNS.getValues());
        version_Items.addAll(Tag.DOORS.getValues());
        version_Items.addAll(Tag.SAPLINGS.getValues());
        version_Items.addAll(Tag.SMALL_FLOWERS.getValues());
        version_Items.addAll(Tag.RAILS.getValues());
        version_Items.addAll(Tag.CORAL_PLANTS.getValues());
        version_Items.addAll(getGlassPanes());
        version_Items.add(Material.BROWN_MUSHROOM);
        version_Items.add(Material.RED_MUSHROOM);
        version_Items.add(Material.END_ROD);
        version_Items.add(Material.COBWEB);
        version_Items.add(Material.TALL_GRASS);
        version_Items.add(Material.SHORT_GRASS);
        version_Items.add(Material.FERN);
        version_Items.add(Material.DEAD_BUSH);
        version_Items.add(Material.SEAGRASS);
        version_Items.add(Material.SEA_PICKLE);
        version_Items.add(Material.LADDER);
        version_Items.add(Material.IRON_BARS);
        version_Items.add(Material.VINE);
        version_Items.add(Material.LILY_PAD);
        version_Items.add(Material.SUNFLOWER);
        version_Items.add(Material.LILAC);
        version_Items.add(Material.ROSE_BUSH);
        version_Items.add(Material.PEONY);
        version_Items.add(Material.TALL_GRASS);
        version_Items.add(Material.LARGE_FERN);
        version_Items.add(Material.BELL);
        version_Items.add(Material.CAMPFIRE);
        version_Items.add(Material.LANTERN);
        version_Items.add(Material.TURTLE_EGG);
        version_Items.add(Material.SUGAR_CANE);
        version_Items.add(Material.KELP);
        version_Items.add(Material.BAMBOO);
        version_Items.add(Material.LEVER);
        version_Items.add(Material.TRIPWIRE_HOOK);
        version_Items.add(Material.REPEATER);
        version_Items.add(Material.COMPARATOR);
        version_Items.add(Material.CAULDRON);
        version_Items.add(Material.BREWING_STAND);
        version_Items.add(Material.HOPPER);
        version_Items.add(Material.TORCH);
        version_Items.add(Material.WHEAT);
        version_Items.add(Material.CAKE);

        version_Items.addAll(Tag.CROPS.getValues());
        version_Items.addAll(Tag.SMALL_FLOWERS.getValues());
        version_Items.addAll(Tag.FLOWERS.getValues());
        version_Items.add(Material.WARPED_FUNGUS);
        version_Items.add(Material.WARPED_ROOTS);
        version_Items.add(Material.TWISTING_VINES);
        version_Items.add(Material.NETHER_SPROUTS);
        version_Items.add(Material.WEEPING_VINES);
        version_Items.add(Material.CRIMSON_ROOTS);
        version_Items.add(Material.CRIMSON_FUNGUS);
        version_Items.add(Material.SOUL_CAMPFIRE);
        version_Items.add(Material.SOUL_LANTERN);
        version_Items.add(Material.SOUL_TORCH);
        version_Items.add(Material.CHAIN);

        version_Ignored_Items = new ArrayList<>();
        version_Ignored_Items.addAll(Tag.BEDS.getValues());
        version_Ignored_Items.addAll(Tag.BANNERS.getValues());
        version_Ignored_Items.add(Material.DRAGON_HEAD);
        version_Ignored_Items.add(Material.PLAYER_HEAD);
        version_Ignored_Items.add(Material.ZOMBIE_HEAD);
        version_Ignored_Items.add(Material.SKELETON_SKULL);
        version_Ignored_Items.add(Material.CREEPER_HEAD);
        version_Ignored_Items.add(Material.SHIELD);
        version_Ignored_Items.add(Material.CROSSBOW);
        version_Ignored_Items.add(Material.TRIDENT);
        version_Ignored_Items.add(Material.BOW);
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
        if(itemStack.getType() == Material.BLAZE_ROD) return true;
        if(itemStack.getType() == Material.BONE) return true;
        if(itemStack.getType() == Material.WARPED_FUNGUS_ON_A_STICK) return true;
        return false;
    }

    @Override
    public List<Material> graphically2DList() {
        return version_Items;
    }

    @Override
    public List<Material> ignoredMaterials() {
        return version_Ignored_Items;
    }
}
