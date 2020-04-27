package com.jamesdpeters.minecraft.chests.sort;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        HashMap<ItemStack,Integer> itemAmounts = getItemAmounts(inventory.getContents());
        List<ItemStack> condensed = condenseInventory(inventory.getContents());

        condensed.sort((item1, item2) -> {
            if (item1 == null) return 1;
            if (item2 == null) return -1;

            Optional<ItemStack> matchItem1 = itemAmounts.keySet().stream().filter(is -> is.isSimilar(item1)).findFirst();
            Optional<ItemStack> matchItem2 = itemAmounts.keySet().stream().filter(is -> is.isSimilar(item2)).findFirst();
            if(!matchItem1.isPresent()) return 1;
            if(!matchItem2.isPresent()) return -1;

            int itemOrder = itemAmounts.get(matchItem1.get()).compareTo(itemAmounts.get(matchItem2.get()));
            if(descending) itemOrder *= -1;
            return itemOrder;
        });
        ItemStack[] itemStacks = condensed.toArray(new ItemStack[0]);
        inventory.setContents(itemStacks);
    }

    private static HashMap<ItemStack,Integer> getItemAmounts(ItemStack[] itemStacks){
        HashMap<ItemStack,Integer> itemAmounts = new HashMap<>();
        for(ItemStack itemStack : itemStacks){
            if(itemStack == null) continue;
            int amount;
            Optional<ItemStack> match = itemAmounts.keySet().stream().filter(is -> is.isSimilar(itemStack)).findFirst();
            if(!match.isPresent()){
                amount = itemStack.getAmount();
                itemAmounts.put(itemStack,amount);
            } else {
                amount = itemAmounts.get(match.get()) + itemStack.getAmount();
                itemAmounts.put(match.get(), amount);
            }
        }
        return itemAmounts;
    }

    private static List<ItemStack> condenseInventory(ItemStack[] itemStacks){
        HashMap<ItemStack,Integer> itemAmounts = getItemAmounts(itemStacks);
        return condenseInventory(itemAmounts);
    }

    private static List<ItemStack> condenseInventory(HashMap<ItemStack,Integer> itemAmounts){
        List<ItemStack> condensedItems = new ArrayList<>();
        itemAmounts.forEach((itemStack, amount) -> {
            int maxStack = itemStack.getMaxStackSize();
            int amountOfMaxStacks = amount/maxStack;
            int remainder = amount % maxStack;

            for(int i=0; i<amountOfMaxStacks; i++){
                condensedItems.add(cloneItem(itemStack,maxStack));
            }
            if(remainder != 0) condensedItems.add(cloneItem(itemStack,remainder));
        });
        return condensedItems;
    }

    public static ItemStack getMostCommonItem(Inventory inventory){
        return getItemAmounts(inventory.getContents()).entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static ItemStack cloneItem(ItemStack itemStack, int newAmount){
        ItemStack item = itemStack.clone();
        item.setAmount(newAmount);
        return item;
    }
}
