package com.jamesdpeters.minecraft.chests.v1_16_R3;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import net.minecraft.server.v1_16_R3.Container;
import net.minecraft.server.v1_16_R3.EntityHuman;
import net.minecraft.server.v1_16_R3.IRecipe;
import net.minecraft.server.v1_16_R3.InventoryCrafting;
import net.minecraft.server.v1_16_R3.Item;
import net.minecraft.server.v1_16_R3.RecipeCrafting;
import net.minecraft.server.v1_16_R3.Recipes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Crafting implements CraftingProvider {

    @Override
    public CraftingResult craft(World world, List<ItemStack> items) {
        Container container = new Container(null, -1) {
            @Override
            public InventoryView getBukkitView() {
                return null;
            }

            @Override
            public boolean canUse(EntityHuman entityHuman) {
                return false;
            }
        };

        InventoryCrafting crafting = new InventoryCrafting(container, 3, 3);

        for (int i = 0; i < items.size(); i++) {
            crafting.setItem(i, CraftItemStack.asNMSCopy(items.get(i)));
        }

        CraftServer server = (CraftServer) Bukkit.getServer();
        CraftWorld craftWorld = (CraftWorld) world;
        Optional<RecipeCrafting> optional = server.getServer().getCraftingManager().craft(Recipes.CRAFTING, crafting, craftWorld.getHandle());

        net.minecraft.server.v1_16_R3.ItemStack itemstack = net.minecraft.server.v1_16_R3.ItemStack.b;

        if (optional.isPresent()) {
            RecipeCrafting recipeCrafting = optional.get();
            itemstack = recipeCrafting.a(crafting);
        }

        CraftingResult result = new CraftingResult();
        result.setResult(CraftItemStack.asBukkitCopy(itemstack));

        List<ItemStack> matrixResult = crafting.getContents().stream()
                .map(itemStack -> {
                    Item remainingItem = itemStack.getItem().getCraftingRemainingItem();
                    return remainingItem != null ? CraftItemStack.asBukkitCopy(remainingItem.createItemStack()) : null;
                }).collect(Collectors.toList());

        result.setMatrixResult(matrixResult);

        return result;
    }

    @Override
    public Recipe getRecipe(World world, List<ItemStack> items) {
        Container container = new Container(null, -1) {
            @Override
            public InventoryView getBukkitView() {
                return null;
            }

            @Override
            public boolean canUse(EntityHuman entityHuman) {
                return false;
            }
        };

        InventoryCrafting crafting = new InventoryCrafting(container, 3, 3);

        for (int i = 0; i < items.size(); i++) {
            if(i >= 9) break; // ItemList cant contain more than 9 items.
            crafting.setItem(i, CraftItemStack.asNMSCopy(items.get(i)));
        }

        CraftServer server = (CraftServer) Bukkit.getServer();
        CraftWorld craftWorld = (CraftWorld) world;
        Optional<RecipeCrafting> optional = server.getServer().getCraftingManager().craft(Recipes.CRAFTING, crafting, craftWorld.getHandle());

        return optional.map(IRecipe::toBukkitRecipe).orElse(null);

    }
}
