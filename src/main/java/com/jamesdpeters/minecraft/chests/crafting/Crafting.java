package com.jamesdpeters.minecraft.chests.crafting;

import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Crafting {

    private static List<ShapedRecipe> shapedRecipes;
    private static List<ShapelessRecipe> shapelessRecipes;

    public static void load(){
        shapedRecipes = new ArrayList<>();
        shapelessRecipes = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if(recipe instanceof ShapedRecipe) shapedRecipes.add((ShapedRecipe) recipe);
            if(recipe instanceof ShapelessRecipe) shapelessRecipes.add((ShapelessRecipe) recipe);
        });
        Bukkit.broadcastMessage("Shaped Recipes: "+shapedRecipes.size());
        Bukkit.broadcastMessage("Shapeless Recipes: "+shapelessRecipes.size());
    }

    public static ItemStack getResult(List<ItemStack> craftingTable){
        for(ShapelessRecipe shapelessRecipe : shapelessRecipes) {
            if (matchesShapeless(shapelessRecipe.getChoiceList(), craftingTable)) return shapelessRecipe.getResult();
        }
//        for(ShapedRecipe shapedRecipe : shapedRecipes) {
//            if (matchesShaped(shapedRecipe, craftingTable)) return shapedRecipe.getResult();
//        }
        return null;
    }

    private static boolean matchesShapeless(List<RecipeChoice> choice, List<ItemStack> items) {
        items = new ArrayList<>(items);
        for (RecipeChoice c : choice) {
            boolean match = false;
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (item == null || item.getType() == Material.AIR)
                    continue;
                if (c.test(item)) {
                    match = true;
                    items.remove(i);
                    break;
                }
            }
            if (!match)
                return false;
        }
        Set<ItemStack> remainingItems = new HashSet<>(items);
        return (remainingItems.size()==1&&(items.contains(new ItemStack(Material.AIR))||items.contains(null)));
    }

    private static boolean matchesShaped(ShapedRecipe shape, List<ItemStack> items) {
        String[] map = shape.getShape();
        Map<Character, RecipeChoice> choices = shape.getChoiceMap();
        for (Map.Entry<Character, RecipeChoice> entry : choices.entrySet()) {
            if (entry.getValue() == null)
                continue;
        }
        int index = 0;

        boolean test = true;

        for (String s : map) {
            if (!test)
                break;
            for (Character c : s.toCharArray()) {
                RecipeChoice currentChoice = choices.get(c);
                if (currentChoice == null) {
                    if (index < items.size() && items.get(index) != null
                            && items.get(index).getType() != Material.AIR) {
                        test = false;
                        break;
                    }
                    index++;
                    continue;
                }
                if (index >= items.size()) {
                    test = false;
                    break;
                }
                if (!currentChoice.test(items.get(index))) {
                    test = false;
                    break;
                }
                index++;
            }
        }

        return test;
    }

    public static void updateCrafting(Inventory inventory){
        List<ItemStack> craftingMatrix = new ArrayList<>(Arrays.asList(inventory.getContents()));
        if(craftingMatrix.get(0) != null) craftingMatrix.remove(0);
        ItemStack result = getResult(craftingMatrix);
        inventory.setItem(0, result);
    }

    public static void craft(Player player){
        Inventory craft = new VirtualCraftingHolder().getInventory();
        player.openInventory(craft);
    }
}
