package com.jamesdpeters.minecraft.chests.crafting;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Arrays;
import java.util.Map;

public class UserShapedRecipe {

    String[] shape;
    Map<Character, ItemStack> ingredientMap;

    public UserShapedRecipe(){

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
        // convert both shapes and ingredient maps to common ItemStack array.
        ItemStack[] matrix1 = shapeToMatrix(getShape(), getIngredientMap());
        ItemStack[] matrix2 = shapeToMatrix(recipe.getShape(), recipe.getIngredientMap());

        if(!Arrays.equals(matrix1, matrix2)) // compare arrays and if they don't match run another check with one shape mirrored.
        {
            mirrorMatrix(matrix1);

            return Arrays.equals(matrix1, matrix2);
        }

        return true; // ingredients match
    }

    private static ItemStack[] shapeToMatrix(String[] shape, Map<Character, ItemStack> map)
    {
        ItemStack[] matrix = new ItemStack[9];
        int slot = 0;

        for(int r = 0; r < shape.length; r++)
        {
            for(char col : shape[r].toCharArray())
            {
                matrix[slot] = map.get(col);
                slot++;
            }

            slot = ((r + 1) * 3);
        }

        return matrix;
    }

    private static void mirrorMatrix(ItemStack[] matrix)
    {
        ItemStack tmp;

        for(int r = 0; r < 3; r++)
        {
            tmp = matrix[(r * 3)];
            matrix[(r * 3)] = matrix[(r * 3) + 2];
            matrix[(r * 3) + 2] = tmp;
        }
    }
}
