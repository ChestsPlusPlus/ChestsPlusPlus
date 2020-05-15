package com.jamesdpeters.minecraft.chests.crafting;

import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
        for(ShapedRecipe shapedRecipe : shapedRecipes) {
            if (matchesShaped(shapedRecipe, craftingTable)) return shapedRecipe.getResult();
        }
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
        //validateItemList(items);
        if(shape.getResult().getType().equals(Material.WHITE_WOOL)){
            Bukkit.broadcastMessage(items.toString());
            Bukkit.broadcastMessage(Arrays.toString(shape.getShape()));
            Bukkit.broadcastMessage(shape.getChoiceMap().toString());
        }
        UserShapedRecipe userShapedRecipe = getUserShapedRecipe(items);
        if(userShapedRecipe == null) return false;
        return userShapedRecipe.matchesRecipe(shape);
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

    private static void validateItemList(List<ItemStack> itemStacks){
        if(itemStacks.size() >=9) {
            int rowsToRemove = 0;
            int colsToRemove = 0;

            for (int row = 0; row < 3; row++) {
                boolean removeRow = true;
                for (int col = 0; col < 3; col++) {
                    if (itemStacks.get((row * 3) + col) != null) {
                        removeRow = false;
                        break;
                    }
                }
                if (removeRow) rowsToRemove++;
            }

            for (int col = 0; col < 3; col++) {
                boolean removeCol = true;
                for (int row = 0; row < 3; row++) {
                    if (itemStacks.get((row * 3) + col) != null) {
                        removeCol = false;
                        break;
                    }
                }
                if (removeCol) colsToRemove++;
            }


            Bukkit.broadcastMessage("Rows to remove: "+rowsToRemove+" Cols: "+colsToRemove);

            int index = 1;
            Iterator<ItemStack> iter = itemStacks.iterator();
            while (iter.hasNext()) {
                iter.next();
                int row = (index) / 3;
                int column = (index % 3) - 1;
                Bukkit.broadcastMessage("row: "+row+" col: "+column);
                if ((row < rowsToRemove) || (column < colsToRemove)) iter.remove();
                index++;
            }

            Bukkit.broadcastMessage("Items: "+itemStacks.toString());

            //itemStacks.replaceAll(itemStack -> itemStack == null ? new ItemStack(Material.AIR) : itemStack);
        }
    }

    private static UserShapedRecipe getUserShapedRecipe(List<ItemStack> itemStacks){
        if(itemStacks.size() < 9) return null;
        Character[] chars = new Character[]{'a','b','c','d','e','f','g','h','i'};
        int rowsToRemove = 0;
        int colsToRemove = 0;

        for (int row = 0; row < 3; row++) {
            boolean removeRow = true;
            for (int col = 0; col < 3; col++) {
                if (itemStacks.get((row * 3) + col) != null) {
                    removeRow = false;
                    break;
                }
            }
            if (removeRow) rowsToRemove++;
        }

        for (int col = 0; col < 3; col++) {
            boolean removeCol = true;
            for (int row = 0; row < 3; row++) {
                if (itemStacks.get((row * 3) + col) != null) {
                    removeCol = false;
                    break;
                }
            }
            if (removeCol) colsToRemove++;
        }

        int index = 0;
        String[] shape = new String[3-rowsToRemove];
        Map<Character,ItemStack> itemMap = new HashMap<>();
        for(int row=rowsToRemove; row < 3; row++){
            StringBuilder shapeRow = new StringBuilder();
            for(int col=colsToRemove; col < 3; col++){
                shapeRow.append(chars[index]);
                itemMap.put(chars[index],itemStacks.get((row*3)+col));
            }
            shape[row-rowsToRemove] = shapeRow.toString();
        }

        UserShapedRecipe recipe = new UserShapedRecipe();
        recipe.setIngredientMap(itemMap);
        recipe.setShape(shape);

        return recipe;
    }
}
