package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unchecked")
public class ItemTypeUtil {

    private static final List<Tag<Material>> tags;

    static {
        tags = new ArrayList<>();

        tags.add(ChestsPlusPlusTag.SEEDS);
        tags.add(Tag.WOOL);
        tags.add(Tag.PLANKS);
        tags.add(Tag.STONE_BRICKS);
        tags.add(Tag.WOODEN_BUTTONS);
        tags.add(Tag.BUTTONS);
        tags.add(Tag.CARPETS);
        tags.add(Tag.WOODEN_DOORS);
        tags.add(Tag.WOODEN_STAIRS);
        tags.add(Tag.WOODEN_SLABS);
        tags.add(Tag.WOODEN_FENCES);
        tags.add(Tag.WOODEN_PRESSURE_PLATES);
        tags.add(Tag.WOODEN_TRAPDOORS);
        tags.add(Tag.DOORS);
        tags.add(Tag.SAPLINGS);
        tags.add(Tag.LOGS);
        tags.add(Tag.DARK_OAK_LOGS);
        tags.add(Tag.OAK_LOGS);
        tags.add(Tag.BIRCH_LOGS);
        tags.add(Tag.ACACIA_LOGS);
        tags.add(Tag.JUNGLE_LOGS);
        tags.add(Tag.SPRUCE_LOGS);
        tags.add(Tag.BANNERS);
        tags.add(Tag.SAND);
        tags.add(Tag.STAIRS);
        tags.add(Tag.SLABS);
        tags.add(Tag.WALLS);
        tags.add(Tag.ANVIL);
        tags.add(Tag.RAILS);
        tags.add(Tag.LEAVES);
        tags.add(Tag.TRAPDOORS);
        tags.add(Tag.FLOWER_POTS);
        tags.add(Tag.SMALL_FLOWERS);
        tags.add(Tag.BEDS);
        tags.add(Tag.FENCES);
        tags.add(Tag.ICE);
        tags.add(Tag.CORAL_BLOCKS);
        tags.add(Tag.WALL_CORALS);
        tags.add(Tag.CORAL_PLANTS);
        tags.add(Tag.SIGNS);
        tags.add(Tag.ITEMS_BANNERS);
        tags.add(Tag.ITEMS_BOATS);
        tags.add(Tag.ITEMS_FISHES);
        tags.add(Tag.ITEMS_CREEPER_DROP_MUSIC_DISCS);
        tags.add(Tag.ITEMS_COALS);
        tags.add(Tag.ITEMS_ARROWS);
    }

    public static boolean isSimilarTag(ItemStack is1, ItemStack is2){
        for (Tag<Material> tag : tags) {
            if (tag.isTagged(is1.getType()) && tag.isTagged(is2.getType())) return true;
        }
        return false;
    }
}
