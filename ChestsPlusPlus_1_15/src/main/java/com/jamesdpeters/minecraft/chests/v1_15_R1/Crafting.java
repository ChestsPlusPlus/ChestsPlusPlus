package com.jamesdpeters.minecraft.chests.v1_15_R1;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import net.minecraft.server.v1_15_R1.Container;
import net.minecraft.server.v1_15_R1.ContainerWorkbench;
import net.minecraft.server.v1_15_R1.EntityHuman;
import net.minecraft.server.v1_15_R1.IRecipe;
import net.minecraft.server.v1_15_R1.InventoryCraftResult;
import net.minecraft.server.v1_15_R1.InventoryCrafting;
import net.minecraft.server.v1_15_R1.Item;
import net.minecraft.server.v1_15_R1.RecipeCrafting;
import net.minecraft.server.v1_15_R1.RecipeRepair;
import net.minecraft.server.v1_15_R1.Recipes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_15_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftInventoryCrafting;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Optional;

public class Crafting implements CraftingProvider {

    @Override
    public CraftingResult craft(Player player, World world, ItemStack[] items) {
        CraftWorld craftWorld = (CraftWorld) world;
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ContainerWorkbench workbench = new ContainerWorkbench(-1, craftPlayer.getHandle().inventory);

        CraftInventoryView view = workbench.getBukkitView();
        CraftInventoryCrafting craftInventoryCrafting = (CraftInventoryCrafting) view.getTopInventory();
        InventoryCrafting inventoryCrafting = (InventoryCrafting) craftInventoryCrafting.getMatrixInventory();
        InventoryCraftResult resultInventory = (InventoryCraftResult) craftInventoryCrafting.getResultInventory();

        Optional<RecipeCrafting> recipe = getNMSRecipe(items, inventoryCrafting, craftWorld);

        net.minecraft.server.v1_15_R1.ItemStack itemstack = net.minecraft.server.v1_15_R1.ItemStack.a;

        if (recipe.isPresent()) {
            RecipeCrafting recipeCrafting = recipe.get();
            if (resultInventory.a(craftWorld.getHandle(), craftPlayer.getHandle(), recipeCrafting)) {
                itemstack = recipeCrafting.a(inventoryCrafting);
            }
        }

        net.minecraft.server.v1_15_R1.ItemStack result = CraftEventFactory.callPreCraftEvent(inventoryCrafting, resultInventory, itemstack, view, recipe.orElse(null) instanceof RecipeRepair);

        for(int i = 0; i < items.length; ++i) {
            Item remaining = inventoryCrafting.getContents().get(i).getItem().p();
            items[i] = remaining != null ? CraftItemStack.asBukkitCopy(new net.minecraft.server.v1_15_R1.ItemStack(remaining)) : null;
        }

        return new CraftingResult(CraftItemStack.asBukkitCopy(result), items);
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

    private Optional<RecipeCrafting> getNMSRecipe(ItemStack[] craftingMatrix, InventoryCrafting inventoryCrafting, CraftWorld world) {
        for(int i = 0; i < craftingMatrix.length; ++i) {
            inventoryCrafting.setItem(i, CraftItemStack.asNMSCopy(craftingMatrix[i]));
        }

        CraftServer server = (CraftServer) Bukkit.getServer();
        return server.getServer().getCraftingManager().craft(Recipes.CRAFTING, inventoryCrafting, world.getHandle());
    }
}
