package com.jamesdpeters.minecraft.chests.v1_16_R2;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import net.minecraft.server.v1_16_R2.Container;
import net.minecraft.server.v1_16_R2.EntityHuman;
import net.minecraft.server.v1_16_R2.IRecipe;
import net.minecraft.server.v1_16_R2.InventoryCrafting;
import net.minecraft.server.v1_16_R2.NonNullList;
import net.minecraft.server.v1_16_R2.RecipeCrafting;
import net.minecraft.server.v1_16_R2.Recipes;
import net.minecraft.server.v1_16_R2.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_16_R2.CraftServer;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Optional;

public class Crafting implements CraftingProvider {

    @Override
    public CraftingResult craft(World world, ItemStack[] items) {
        CraftWorld craftWorld = (CraftWorld) world;

        // Setup crafting inventories.
        InventoryCrafting inventoryCrafting = new InventoryCrafting(new Container(null, -1) {
            @Override
            public InventoryView getBukkitView() {
                return null;
            }

            @Override
            public boolean canUse(EntityHuman entityHuman) {
                return false;
            }
        }, 3, 3);

        for (int i = 0; i < items.length; i++) {
            inventoryCrafting.setItem(i, CraftItemStack.asNMSCopy(items[i]));
        }

        Optional<RecipeCrafting> recipe = ((CraftServer) Bukkit.getServer()).getServer().getCraftingManager().craft(Recipes.CRAFTING, inventoryCrafting, craftWorld.getHandle());

        // Generate the resulting ItemStack from the Crafting Matrix
        net.minecraft.server.v1_16_R2.ItemStack itemStack = net.minecraft.server.v1_16_R2.ItemStack.b;

        if (recipe.isPresent()) {
            itemStack = recipe.get().a(inventoryCrafting);
        }

        return createItemCraftResult(CraftItemStack.asBukkitCopy(itemStack), inventoryCrafting, craftWorld.getHandle());
    }

    @Override
    public Recipe getRecipe(Player player, World world, ItemStack[] items) {
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

        for (int i = 0; i < items.length; i++) {
            if(i >= 9) break; // ItemList cant contain more than 9 items.
            crafting.setItem(i, CraftItemStack.asNMSCopy(items[i]));
        }

        CraftServer server = (CraftServer) Bukkit.getServer();
        CraftWorld craftWorld = (CraftWorld) world;
        Optional<RecipeCrafting> optional = server.getServer().getCraftingManager().craft(Recipes.CRAFTING, crafting, craftWorld.getHandle());

        return optional.map(IRecipe::toBukkitRecipe).orElse(null);
    }

    private CraftingResult createItemCraftResult(ItemStack itemStack, InventoryCrafting inventoryCrafting, WorldServer worldServer) {
        CraftServer server = (CraftServer) Bukkit.getServer();
        NonNullList<net.minecraft.server.v1_16_R2.ItemStack> remainingItems = server.getServer().getCraftingManager().c(Recipes.CRAFTING, inventoryCrafting, worldServer);

        CraftingResult craftItemResult = new CraftingResult(itemStack, new ItemStack[9], new ArrayList<>());

        // Create resulting matrix and overflow items
        for (int i = 0; i < remainingItems.size(); ++i) {
            net.minecraft.server.v1_16_R2.ItemStack itemstack1 = inventoryCrafting.getItem(i);
            net.minecraft.server.v1_16_R2.ItemStack itemstack2 = remainingItems.get(i);

            if (!itemstack1.isEmpty()) {
                inventoryCrafting.splitStack(i, 1);
                itemstack1 = inventoryCrafting.getItem(i);
            }

            if (!itemstack2.isEmpty()) {
                if (itemstack1.isEmpty()) {
                    inventoryCrafting.setItem(i, itemstack2);
                } else if (net.minecraft.server.v1_16_R2.ItemStack.equals(itemstack1, itemstack2) && net.minecraft.server.v1_16_R2.ItemStack.matches(itemstack1, itemstack2)) {
                    itemstack2.add(itemstack1.getCount());
                    inventoryCrafting.setItem(i, itemstack2);
                } else {
                    craftItemResult.overflowItems().add(CraftItemStack.asBukkitCopy(itemstack2));
                }
            }
        }

        for (int i = 0; i < inventoryCrafting.getContents().size(); i++) {
            craftItemResult.setResultMatrix(i, CraftItemStack.asBukkitCopy(inventoryCrafting.getItem(i)));
        }

        return craftItemResult;
    }
}
