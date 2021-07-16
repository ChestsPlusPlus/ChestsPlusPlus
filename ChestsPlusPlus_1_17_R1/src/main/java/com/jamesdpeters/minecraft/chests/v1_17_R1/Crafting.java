package com.jamesdpeters.minecraft.chests.v1_17_R1;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Crafting implements CraftingProvider {

    @Override
    public CraftingResult craft(Player player, World world, List<ItemStack> items) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        CraftingMenu menu = new CraftingMenu(-1, craftPlayer.getHandle().getInventory());
        CraftingContainer crafting = new CraftingContainer(menu.getBukkitView().getHandle(), 3, 3);

        for (int i = 0; i < items.size(); i++) {
            crafting.setItem(i, CraftItemStack.asNMSCopy(items.get(i)));
        }

        CraftServer server = (CraftServer) Bukkit.getServer();
        CraftWorld craftWorld = (CraftWorld) world;
        Optional<CraftingRecipe> optional = server.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, craftWorld.getHandle());

        net.minecraft.world.item.ItemStack itemStack = net.minecraft.world.item.ItemStack.EMPTY;

        if (optional.isPresent()) {
            CraftingRecipe recipeCrafting = optional.get();
            itemStack = recipeCrafting.assemble(crafting);
        }

        CraftingResult result = new CraftingResult();
        result.setResult(CraftItemStack.asBukkitCopy(itemStack));

        List<ItemStack> matrixResult = crafting.getContents().stream()
                .map(item -> {
                    Item remainingItem = item.getItem().getCraftingRemainingItem();
                    return remainingItem != null ? CraftItemStack.asBukkitCopy(remainingItem.getDefaultInstance()) : null;
                }).collect(Collectors.toList());

        result.setMatrixResult(matrixResult);

        return result;
    }

    @Override
    public Recipe getRecipe(Player player, World world, List<ItemStack> items) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        CraftingMenu menu = new CraftingMenu(-1, craftPlayer.getHandle().getInventory());
        CraftingContainer crafting = new CraftingContainer(menu.getBukkitView().getHandle(), 3, 3);

        for (int i = 0; i < items.size(); i++) {
            if(i >= 9) break; // ItemList cant contain more than 9 items.
            crafting.setItem(i, CraftItemStack.asNMSCopy(items.get(i)));
        }

        CraftServer server = (CraftServer) Bukkit.getServer();
        CraftWorld craftWorld = (CraftWorld) world;
        Optional<CraftingRecipe> optional = server.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, craftWorld.getHandle());

        return optional.map(net.minecraft.world.item.crafting.Recipe::toBukkitRecipe).orElse(null);

    }
}
