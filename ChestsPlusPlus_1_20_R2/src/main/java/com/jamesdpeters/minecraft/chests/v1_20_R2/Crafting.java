package com.jamesdpeters.minecraft.chests.v1_20_R2;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R2.CraftServer;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack;
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
        CraftingContainer inventoryCrafting = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override
            public InventoryView getBukkitView() {
                return null;
            }

            @Override
            public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int i) {
                return null;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return false;
            }

        }, 3, 3);

        for (int i = 0; i < items.length; i++) {
            inventoryCrafting.setItem(i, CraftItemStack.asNMSCopy(items[i]));
        }

        var recipe = ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, inventoryCrafting, craftWorld.getHandle());

        // Generate the resulting ItemStack from the Crafting Matrix
        net.minecraft.world.item.ItemStack itemStack = net.minecraft.world.item.ItemStack.EMPTY;

        if (recipe.isPresent()) {
            itemStack = recipe.get().value().assemble(inventoryCrafting, RegistryAccess.EMPTY);
        }

        return createItemCraftResult(CraftItemStack.asBukkitCopy(itemStack), inventoryCrafting, craftWorld.getHandle());
    }

    @Override
    public Recipe getRecipe(Player player, World world, ItemStack[] items) {
        return Bukkit.getCraftingRecipe(items, world);
    }

    private CraftingResult createItemCraftResult(ItemStack itemStack, CraftingContainer inventoryCrafting, Level worldServer) {
        CraftServer server = (CraftServer) Bukkit.getServer();
        NonNullList<net.minecraft.world.item.ItemStack> remainingItems = server.getServer().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, inventoryCrafting, worldServer);

        CraftingResult craftItemResult = new CraftingResult(itemStack, new ItemStack[9], new ArrayList<>());

        // Create resulting matrix and overflow items
        for (int i = 0; i < remainingItems.size(); ++i) {
            net.minecraft.world.item.ItemStack itemstack1 = inventoryCrafting.getItem(i);
            net.minecraft.world.item.ItemStack itemstack2 = (net.minecraft.world.item.ItemStack) remainingItems.get(i);

            if (!itemstack1.isEmpty()) {
                inventoryCrafting.removeItem(i, 1);
                itemstack1 = inventoryCrafting.getItem(i);
            }

            if (!itemstack2.isEmpty()) {
                if (itemstack1.isEmpty()) {
                    inventoryCrafting.setItem(i, itemstack2);
                } else if (net.minecraft.world.item.ItemStack.matches(itemstack1, itemstack2)) {
                    itemstack2.grow(itemstack1.getCount());
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
