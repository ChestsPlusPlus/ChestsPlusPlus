package com.jamesdpeters.minecraft.chests.v1_15_R1;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import net.minecraft.server.v1_15_R1.Container;
import net.minecraft.server.v1_15_R1.EntityHuman;
import net.minecraft.server.v1_15_R1.IRecipe;
import net.minecraft.server.v1_15_R1.InventoryCrafting;
import net.minecraft.server.v1_15_R1.RecipeCrafting;
import net.minecraft.server.v1_15_R1.Recipes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;
import java.util.Optional;

public class Crafting implements CraftingProvider {

    @Override
    public ItemStack craft(World world, List<ItemStack> items) {
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

        net.minecraft.server.v1_15_R1.ItemStack itemstack = net.minecraft.server.v1_15_R1.ItemStack.a;

        if (optional.isPresent()) {
            RecipeCrafting recipeCrafting = optional.get();
            itemstack = recipeCrafting.a(crafting);
        }

        return CraftItemStack.asBukkitCopy(itemstack);
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
