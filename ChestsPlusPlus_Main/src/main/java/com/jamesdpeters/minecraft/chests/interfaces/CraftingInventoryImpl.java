package com.jamesdpeters.minecraft.chests.interfaces;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

public class CraftingInventoryImpl implements CraftingInventory {

    Inventory inventory;
    ItemStack result;
    ItemStack[] matrix;
    Recipe recipe;

    public CraftingInventoryImpl(Inventory inventory, ItemStack result, List<ItemStack> matrix, Recipe recipe) {
        this.inventory = inventory;
        this.result = result;
        this.matrix = matrix.toArray(new ItemStack[0]);
        this.recipe = recipe;
    }

    @Nullable
    @Override
    public ItemStack getResult() {
        return result;
    }

    @NotNull
    @Override
    public ItemStack[] getMatrix() {
        return matrix;
    }

    @Override
    public void setResult(@Nullable ItemStack newResult) {
        result = newResult;
    }

    @Override
    public void setMatrix(@NotNull ItemStack[] contents) {
        matrix = contents;
    }

    @Nullable
    @Override
    public Recipe getRecipe() {
        return recipe;
    }


    // INVENTORY METHODS

    @Override
    public int getSize() {
        return inventory.getSize();
    }

    @Override
    public int getMaxStackSize() {
        return inventory.getMaxStackSize();
    }

    @Override
    public void setMaxStackSize(int size) {
        inventory.setMaxStackSize(size);
    }

    @Nullable
    @Override
    public ItemStack getItem(int index) {
        return inventory.getItem(index);
    }

    @Override
    public void setItem(int index, @Nullable ItemStack item) {
        inventory.setItem(index, item);
    }

    @NotNull
    @Override
    public HashMap<Integer, ItemStack> addItem(@NotNull ItemStack... items)
            throws IllegalArgumentException {
        return inventory.addItem(items);
    }

    @NotNull
    @Override
    public HashMap<Integer, ItemStack> removeItem(@NotNull ItemStack... items)
            throws IllegalArgumentException {
        return inventory.removeItem(items);
    }

    @NotNull
    @Override
    public ItemStack[] getContents() {
        return inventory.getContents();
    }

    @Override
    public void setContents(@NotNull ItemStack[] items) throws IllegalArgumentException {
        inventory.setContents(items);
    }

    @NotNull
    @Override
    public ItemStack[] getStorageContents() {
        return inventory.getStorageContents();
    }

    @Override
    public void setStorageContents(@NotNull ItemStack[] items) throws IllegalArgumentException {
        inventory.setStorageContents(items);
    }

    @Override
    public boolean contains(@NotNull Material material) throws IllegalArgumentException {
        return inventory.contains(material);
    }

    @Override
    public boolean contains(@Nullable ItemStack item) {
        return inventory.contains(item);
    }

    @Override
    public boolean contains(@NotNull Material material, int amount) throws IllegalArgumentException {
        return inventory.contains(material, amount);
    }

    @Override
    public boolean contains(@Nullable ItemStack item, int amount) {
        return inventory.contains(item, amount);
    }

    @Override
    public boolean containsAtLeast(@Nullable ItemStack item, int amount) {
        return inventory.containsAtLeast(item, amount);
    }

    @NotNull
    @Override
    public HashMap<Integer, ? extends ItemStack> all(@NotNull Material material)
            throws IllegalArgumentException {
        return inventory.all(material);
    }

    @NotNull
    @Override
    public HashMap<Integer, ? extends ItemStack> all(@Nullable ItemStack item) {
        return inventory.all(item);
    }

    @Override
    public int first(@NotNull Material material) throws IllegalArgumentException {
        return inventory.first(material);
    }

    @Override
    public int first(@NotNull ItemStack item) {
        return inventory.first(item);
    }

    @Override
    public int firstEmpty() {
        return inventory.firstEmpty();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public void remove(@NotNull Material material) throws IllegalArgumentException {
        inventory.remove(material);
    }

    @Override
    public void remove(@NotNull ItemStack item) {
        inventory.remove(item);
    }

    @Override
    public void clear(int index) {
        inventory.clear(index);
    }

    @Override
    public void clear() {
        inventory.clear();
    }

    @NotNull
    @Override
    public List<HumanEntity> getViewers() {
        return inventory.getViewers();
    }

    @NotNull
    @Override
    public InventoryType getType() {
        return inventory.getType();
    }

    @Nullable
    @Override
    public InventoryHolder getHolder() {
        return inventory.getHolder();
    }

    @NotNull
    @Override
    public ListIterator<ItemStack> iterator() {
        return inventory.iterator();
    }

    @NotNull
    @Override
    public ListIterator<ItemStack> iterator(int index) {
        return inventory.iterator(index);
    }

    @Nullable
    @Override
    public Location getLocation() {
        return inventory.getLocation();
    }
}
