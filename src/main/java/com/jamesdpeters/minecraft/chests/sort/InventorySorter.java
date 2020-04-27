package com.jamesdpeters.minecraft.chests.sort;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;


public class InventorySorter {

    public static void sort(Inventory inventory, SortMethod sortMethod){
        switch (sortMethod){
            case OFF: return;
            case NAME: {
                List<ItemStack> condensed = condenseInventory(inventory.getContents());
                condensed.sort((item1, item2) -> {
                    if (item1 == null) return 1;
                    if (item2 == null) return -1;
                    else {
                        return item1.getType().name().compareTo(item2.getType().name());
                    }
                });
                ItemStack[] itemStacks = condensed.toArray(new ItemStack[0]);
                inventory.setContents(itemStacks);
                return;
            }
            case AMOUNT_DESC: {
                sortByAmount(inventory,true);
                return;
            }
            case AMOUNT_ASC: {
                sortByAmount(inventory,false);
            }
        }
    }

    private static void sortByAmount(Inventory inventory, boolean descending){

        HashMap<Material,Integer> itemAmounts = getItemAmounts(inventory.getContents());
        List<ItemStack> condensed = condenseInventory(inventory.getContents());

        condensed.sort((item1, item2) -> {
            if (item1 == null) return 1;
            if (item2 == null) return -1;

            int itemOrder = itemAmounts.get(item1.getType()).compareTo(itemAmounts.get(item2.getType()));
            if(descending) itemOrder *= -1;
            return itemOrder;
        });
        ItemStack[] itemStacks = condensed.toArray(new ItemStack[0]);
        inventory.setContents(itemStacks);
    }

    private static HashMap<Material,Integer> getItemAmounts(ItemStack[] itemStacks){
        HashMap<Material,Integer> itemAmounts = new HashMap<>();
        for(ItemStack itemStack : itemStacks){
            if(itemStack == null) continue;
            int amount;
            if(!itemAmounts.containsKey(itemStack.getType())){
                amount = itemStack.getAmount();
            } else {
                amount = itemAmounts.get(itemStack.getType()) + itemStack.getAmount();
            }
            itemAmounts.put(itemStack.getType(),amount);
        }
        return itemAmounts;
    }

    private static List<ItemStack> condenseInventory(ItemStack[] itemStacks){
        HashMap<Material,Integer> itemAmounts = getItemAmounts(itemStacks);
        return condenseInventory(itemAmounts);
    }

    private static List<ItemStack> condenseInventory(HashMap<Material,Integer> itemAmounts){
        List<ItemStack> condensedItems = new ArrayList<>();
        itemAmounts.forEach((material, amount) -> {
            int maxStack = material.getMaxStackSize();
            int amountOfMaxStacks = amount/maxStack;
            int remainder = amount % maxStack;

            for(int i=0; i<amountOfMaxStacks; i++){
                condensedItems.add(new ItemStack(material,maxStack));
            }
            if(remainder != 0) condensedItems.add(new ItemStack(material,remainder));
        });
        return condensedItems;
    }

    public static Material getMostCommonItem(Inventory inventory){
        return getItemAmounts(inventory.getContents()).entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
