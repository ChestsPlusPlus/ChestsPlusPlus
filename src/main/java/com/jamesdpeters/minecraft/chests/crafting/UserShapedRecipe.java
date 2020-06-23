package com.jamesdpeters.minecraft.chests.crafting;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserShapedRecipe {

    private String[] shape;
    private Map<Character, ItemStack> ingredientMap;
    private Character[] chars = new Character[]{'a','b','c','d','e','f','g','h','i'};

    public UserShapedRecipe(List<ItemStack> itemStacks){
        if(itemStacks.size() < 9) throw new IllegalArgumentException("ItemStack List must contain 9 items");
        if(itemStacks.size() == 10) itemStacks.remove(0);
        int firstRow=-1; int lastRow=0;
        int firstCol=-1; int lastCol=0;

        for (int row = 0; row < 3; row++) {
            boolean removeRow = true;
            for (int col = 0; col < 3; col++) {
                if (itemStacks.get((row * 3) + col) != null) {
                    removeRow = false;
                    break;
                }
            }
            if (!removeRow) {
                if(firstRow == -1) firstRow = row;
                if(row > lastRow) lastRow = row;
            }
        }

        for (int col = 0; col < 3; col++) {
            boolean removeCol = true;
            for (int row = 0; row < 3; row++) {
                if (itemStacks.get((row * 3) + col) != null) {
                    removeCol = false;
                    break;
                }
            }
            if (!removeCol) {
                if(firstCol == -1) firstCol = col;
                if(col > lastCol) lastCol = col;
            }
        }

        if(firstRow == -1) firstRow = 0;
        if(firstCol == -1) firstCol = 0;

        int index = 0;
        int rowIndex = 0;
        String[] shape = new String[(lastRow-firstRow+1)];
        Map<Character,ItemStack> itemMap = new HashMap<>();
        for(int row=firstRow; row <= lastRow; row++){
            StringBuilder shapeRow = new StringBuilder();
            for(int col=firstCol; col <= lastCol; col++){
                    shapeRow.append(chars[index]);
                    itemMap.put(chars[index], itemStacks.get((row * 3) + col));
                    index++;
            }
            shape[rowIndex] = shapeRow.toString();
            rowIndex++;
        }
        setIngredientMap(itemMap);
        setShape(shape);
    }

    public String[] getShape() {
        return shape;
    }

    public UserShapedRecipe setShape(String[] shape) {
        this.shape = shape;
        return this;
    }

    public Map<Character, ItemStack> getIngredientMap() {
        return ingredientMap;
    }

    public UserShapedRecipe setIngredientMap(Map<Character, ItemStack> ingredientMap) {
        this.ingredientMap = ingredientMap;
        return this;
    }

    public boolean matchesRecipe(ShapedRecipe recipe){
        if(!Arrays.equals(getShape(), recipe.getShape())) return false;
        if(!matchesChoiceMap(recipe.getChoiceMap(),recipe.getShape(),false)) {
            return matchesChoiceMap(recipe.getChoiceMap(), recipe.getShape(), true);
        }
        return true; // ingredients match
    }

    private Map<Character,Character> flipShape(String[] shape){
        Map<Character,Character> map = new HashMap<>();
        for(int i=0; i < shape.length; i++){
            StringBuilder input = new StringBuilder();
            String row = shape[i];
            String reverse = input.append(row).reverse().toString();
            char[] originalChars = row.toCharArray();
            char[] reverseChars = reverse.toCharArray();
            for(int c=0; c < row.length(); c++){
                map.put(originalChars[c],reverseChars[c]);
            }
        }
        return map;
    }

    private boolean matchesChoiceMap(Map<Character, RecipeChoice> choiceMap, String[] shape, boolean mirror){
        boolean matches = true;
        Map<Character,Character> charMap = null;
        if(mirror) charMap = flipShape(shape);
        for(Map.Entry<Character,RecipeChoice> entry : choiceMap.entrySet()){
            RecipeChoice choice = entry.getValue();
            //Flip characters if mirrored.
            Character character;
            if(mirror) character = charMap.get(entry.getKey());
            else character = entry.getKey();

            ItemStack item = getIngredientMap().get(character);
            if(choice != null && item != null) {
                if (!choice.test(item)) {
                    matches = false;
                    break;
                }
            } else if(choice == null ^ item == null) {
                matches = false;
                break;
            }
        }

        return matches;
    }
}
