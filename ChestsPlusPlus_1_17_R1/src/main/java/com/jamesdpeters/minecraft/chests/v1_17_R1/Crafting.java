package com.jamesdpeters.minecraft.chests.v1_17_R1;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.InventoryCrafting;
import net.minecraft.world.item.crafting.Recipes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;

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

        var recipe = ((CraftServer) Bukkit.getServer()).getServer().getCraftingManager().craft(Recipes.a, inventoryCrafting, craftWorld.getHandle());

        // Generate the resulting ItemStack from the Crafting Matrix
        net.minecraft.world.item.ItemStack itemStack = net.minecraft.world.item.ItemStack.b;

        if (recipe.isPresent()) {
            itemStack = recipe.get().a(inventoryCrafting);
        }

        return createItemCraftResult(CraftItemStack.asBukkitCopy(itemStack), inventoryCrafting, craftWorld.getHandle());
    }

    @Override
    public Recipe getRecipe(Player player, World world, ItemStack[] items) {
        return Bukkit.getCraftingRecipe(items, world);
    }

    private CraftingResult createItemCraftResult(ItemStack itemStack, InventoryCrafting inventoryCrafting, WorldServer worldServer) {
        CraftServer server = (CraftServer) Bukkit.getServer();
        NonNullList<net.minecraft.world.item.ItemStack> remainingItems = server.getServer().getCraftingManager().c(Recipes.a, inventoryCrafting, worldServer);

        CraftingResult craftItemResult = new CraftingResult(itemStack, new ItemStack[9], new ArrayList<>());

        // Create resulting matrix and overflow items
        for (int i = 0; i < remainingItems.size(); ++i) {
            net.minecraft.world.item.ItemStack itemstack1 = inventoryCrafting.getItem(i);
            net.minecraft.world.item.ItemStack itemstack2 = (net.minecraft.world.item.ItemStack) remainingItems.get(i);

            if (!itemstack1.isEmpty()) {
                inventoryCrafting.splitStack(i, 1);
                itemstack1 = inventoryCrafting.getItem(i);
            }

            if (!itemstack2.isEmpty()) {
                if (itemstack1.isEmpty()) {
                    inventoryCrafting.setItem(i, itemstack2);
                } else if (net.minecraft.world.item.ItemStack.equals(itemstack1, itemstack2) && net.minecraft.world.item.ItemStack.matches(itemstack1, itemstack2)) {
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
