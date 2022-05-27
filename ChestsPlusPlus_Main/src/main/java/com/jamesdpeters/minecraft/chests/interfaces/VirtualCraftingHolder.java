package com.jamesdpeters.minecraft.chests.interfaces;

import com.google.common.collect.Maps;
import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VirtualCraftingHolder implements InventoryHolder {

    private final Inventory inventory;
    private final AutoCraftingStorage storage;
    private BukkitRunnable guiTask;
    private BukkitRunnable craftItemTask;

    private RecipeChoice[] recipeChoices = new RecipeChoice[9];
    private Map<RecipeChoice, List<ItemStack>> recipeChoiceItems = new HashMap<>();
    private ItemStack result;
    private final int[] recipeChoiceIndex = new int[9];
    private boolean hasCompleteRecipe = false;

    /**
     * This gets set to true when updateCrafting() gets called on a 1 tick delay.
     */
    private boolean isUpdatingRecipe = false;

    private static final ItemStack AIR = new ItemStack(Material.AIR);

    public VirtualCraftingHolder(AutoCraftingStorage storage) {
        this.storage = storage;
        inventory = Bukkit.createInventory(this, InventoryType.WORKBENCH, storage.getIdentifier());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }


    public AutoCraftingStorage getStorage() {
        return storage;
    }

    public void setCrafting(ShapelessRecipe shapelessRecipe) {
        result = shapelessRecipe.getResult();
        recipeChoices = shapelessRecipe.getChoiceList().toArray(new RecipeChoice[0]);
        setHasCompleteRecipe();
    }

    public void setCrafting(ShapedRecipe recipe) {
        result = recipe.getResult();
        recipeChoices = new RecipeChoice[9];
        int row = 0;
        for (String r : recipe.getShape()) {
            int col = 0;
            for (char c : r.toCharArray()) {
                int i = (row * 3) + col;
                recipeChoices[i] = recipe.getChoiceMap().get(c);
                col++;
            }
            row++;
        }
        setHasCompleteRecipe();
    }

    public void setCrafting(Recipe recipe, ItemStack[] matrix) {
        if (recipe instanceof ShapedRecipe) setCrafting((ShapedRecipe) recipe);
        else if (recipe instanceof ShapelessRecipe) setCrafting((ShapelessRecipe) recipe);
        else {
            // For ComplexRecipes or other implementations just use the result and original matrix for choices.
            result = ApiSpecific.getNmsProvider().getCraftingProvider().craft(Bukkit.getWorlds().get(0), matrix).result();
            recipeChoices = new RecipeChoice[9];
            for (int i = 0; i < matrix.length; i++) {
                var item = matrix[i];
                RecipeChoice recipeChoice = null;
                if (item != null) {
                    recipeChoice = new RecipeChoice.MaterialChoice(item.getType());
                }
                recipeChoices[i] = recipeChoice;
            }
            setHasCompleteRecipe();
        }
    }

    private void setHasCompleteRecipe() {
        hasCompleteRecipe = true;
        for (RecipeChoice recipeChoice : recipeChoices) {
            if (recipeChoice != null)
                recipeChoiceItems.put(recipeChoice, Utils.getItemsFromRecipeChoice(recipeChoice));
        }
        startCraftingItems();
    }

    public void resetChoices() {
        recipeChoices = new RecipeChoice[9];
        recipeChoiceItems.clear();
        result = null;
        hasCompleteRecipe = false;
        stopCraftingItems();
    }

    public void updateCrafting() {
        var crafting = Arrays.copyOfRange(inventory.getContents(),1, inventory.getContents().length);

        Recipe recipe = Crafting.getRecipe(storage.getOwner().getPlayer(), crafting);
        getStorage().setRecipe(recipe, crafting); // Only store the crafting matrix if the recipe is valid
        resetChoices();

        if (recipe != null) {
            setCrafting(recipe, crafting);
            playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1f);
        } else {
            stopCraftingItems();
        }

        isUpdatingRecipe = false;
        updateGUI();
        storage.onItemDisplayUpdate(result);
    }

    private void playSound(Sound sound, float volume, float pitch) {
        storage.getInventory().getViewers().forEach(humanEntity -> {
            humanEntity.getWorld().playSound(humanEntity.getLocation(), sound, volume, pitch);
        });
    }

    public VirtualCraftingHolder setUpdatingRecipe(boolean updatingRecipe) {
        isUpdatingRecipe = updatingRecipe;
        return this;
    }

    //Start and stop animation based on if the inventory is open.
    public void startAnimation() {
        guiTask = new UpdateTask();
    }

    public void stopAnimation() {
        if (guiTask != null) guiTask.cancel();
    }

    private class UpdateTask extends BukkitRunnable {
        BukkitTask task;

        public UpdateTask() {
            task = runTaskTimer(ChestsPlusPlus.PLUGIN, 1, 30);
        }

        @Override
        public void run() {
            updateGUI();
        }
    }

    public void updateGUI() {
        inventory.setItem(0, result);
        if (hasCompleteRecipe && !isUpdatingRecipe) {
            for (int i = 0; i < 9; i++) {
                int index = recipeChoiceIndex[i];
                List<ItemStack> choices = null;

                if (index < recipeChoices.length) {
                    var recipeChoice = recipeChoices[i];
                    choices = recipeChoiceItems.get(recipeChoice);
                }

                if (choices != null && !choices.isEmpty()) {
                    ItemStack choice;
                    if (index < choices.size()) {
                        choice = choices.get(index);
                    } else {
                        recipeChoiceIndex[i] = 0;
                        choice = choices.get(0);
                    }
                    inventory.setItem(i + 1, choice);
                    recipeChoiceIndex[i]++;
                } else {
                    inventory.setItem(i + 1, null);
                    recipeChoiceIndex[i] = 0;
                }
            }
        }
    }

    public void startCraftingItems() {
        if (craftItemTask == null || craftItemTask.isCancelled()) craftItemTask = new CraftItems();
    }

    public void stopCraftingItems() {
        if (craftItemTask != null) craftItemTask.cancel();
    }

    private class CraftItems extends BukkitRunnable {
        BukkitTask task;

        CraftItems() {
            task = runTaskTimer(ChestsPlusPlus.PLUGIN, 1, 20);
        }

        @Override
        public void run() {
            craftItem();
        }
    }

    /**
     * This method will craft an item if a chest above contains the correct amount of materials
     * And there is a hopper below.
     */
    public void craftItem() {
        for (LocationInfo location : storage.getLocations()) {
            if (!Utils.isLocationChunkLoaded(location.getLocation())) continue;
            Block block = location.getLocation().getBlock();
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            Block blockAbove = block.getRelative(BlockFace.UP);

            Inventory output;

            if (blockBelow.getState() instanceof Hopper hopper) {
                if (blockBelow.isBlockPowered() || blockBelow.isBlockIndirectlyPowered()) {
                    continue; //If hopper is powered no crafting should happen.
                }
                output = hopper.getInventory();
            } else {
                output = getInventory(blockBelow);
                //If there is no output crafting isn't possible, so skip this location.
                if (output == null) continue;
                //If crafting table is powered output into container is possible.
                if (!block.isBlockPowered()) continue;
            }

            List<Inventory> inventories = new ArrayList<>();
            Utils.addIfNotNull(inventories, getInventory(blockAbove));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.NORTH)));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.EAST)));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.SOUTH)));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.WEST)));

            boolean didCraft = craftItem(inventories, output, getInventory());

            //Play sound if crafting occured.
            if (didCraft) {
                if (location.getLocation().getWorld() != null) {
                    location.getLocation().getWorld().playSound(location.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.25f, 1f);
                    if (output.getHolder() instanceof VirtualInventoryHolder) {
                        ((VirtualInventoryHolder) output.getHolder()).getStorage().updateDisplayItem();
                    }
                }
            }
        }
    }

    private Inventory getInventory(Block block) {
        Inventory inventory = null;
        if (block.getState() instanceof Container) {
            ChestLinkStorage storage = Config.getChestLink().getStorage(block.getLocation());
            //Check if a ChestLink exists above the CraftingTable and if the owner of the CraftingTable has permission to access that Chest.
            if (storage != null) {
                if (storage.hasPermission(this.storage.getOwner())) {
                    inventory = storage.getInventory();
                } else {
                    return null;
                }
            } else {
                inventory = ((Container) block.getState()).getInventory();
            }
        }
        return inventory;
    }

    /**
     * Finds a valid recipe matrix for the currently selected recipe.
     * Removes the items from the inventories provided.
     * @param inventories
     * @return a valid recipe matrix selected from the inventories provided.
     */
    private ItemStack[] getRecipeMatrix(List<Inventory> inventories) {
        // Store each item selected for the recipe.
        // This is used to retrieve the actual result taking into account meta data, such as repairing items.
        ItemStack[] tempRecipe = Utils.createAirList(9);

        // Need a new copy of the inventories to test for each recipe choice.
        List<Inventory> tempInvs = Utils.copyInventoryList(inventories);

        for (int i = 0; i < recipeChoices.length; i++) {
            var recipeChoice = recipeChoices[i];
            var possibleItems = recipeChoiceItems.get(recipeChoice);

            if (possibleItems == null)
                continue;

            boolean foundMatch = false;
            for (ItemStack possibleItem : possibleItems) {

                for (Inventory tempInv : tempInvs) {
                    int index = tempInv.first(possibleItem.getType());

                    if (index != -1) {
                        ItemStack item = tempInv.getItem(index);

                        if (item != null) {
                            // If a valid item has been found in one of the inventories.
                            ItemStack selectedItem = item.clone();
                            item.setAmount(item.getAmount() - 1);
                            tempInv.setItem(index, item);

                            selectedItem.setAmount(1);
                            tempRecipe[i] = selectedItem;
                            foundMatch = true;
                            break;
                        }
                    }
                }

                if (foundMatch)
                    break;
            }

            //If no match found
            if (!foundMatch) {
                return null;
            }
        }

        // Move temp invs to input invs.
        for (int i = 0; i < tempInvs.size(); i++) {
            moveTempInv(tempInvs.get(i), inventories.get(i));
        }

        return tempRecipe;
    }

    private boolean craftItem(List<Inventory> inputs, Inventory output, Inventory craftingInventory) {
        boolean sameInv = false;
        Inventory sameInventory = null;

        // Create a copy of the real inventories and decide if any of the inputs match the output.
        List<Inventory> tempInvs = new ArrayList<>();

        // Remove duplicate inventories from list.
        inputs = inputs.stream().distinct().collect(Collectors.toList());

        for (Inventory inv : inputs) {
            Inventory tempInv = Utils.copyInventory(inv);
            tempInvs.add(tempInv);
            if (inv.equals(output)) {
                sameInv = true;
                sameInventory = tempInv;
            }
        }

        ItemStack[] recipe = getRecipeMatrix(tempInvs);
        // null recipe means no recipe was found from the inventories.
        if (recipe == null) return false;

        // Use NMS to get the real result considering meta data etc.
        CraftingResult craftingResult = Crafting.craft(storage.getOwner().getPlayer(), recipe);

        //TODO
//        Recipe recipeActual = Crafting.getRecipe(storage.getOwner().getPlayer(), recipe);
//        CraftingInventoryImpl craftingInventoryImpl = new CraftingInventoryImpl(craftingInventory, craftingResult.getResult(), recipe, recipeActual);
//        InventoryViewImpl inventoryView = new InventoryViewImpl(craftingInventory, output, ApiSpecific.getNmsProvider().getNPCProvider().createHumanEntity());
//        PrepareItemCraftEvent itemCraftEvent = new PrepareItemCraftEvent(craftingInventoryImpl, inventoryView, false);
//        Bukkit.getPluginManager().callEvent(itemCraftEvent);

        if (craftingResult.result() == null) return false;

        //If we reach here there are enough materials so check for space in the Hopper and update inventory.
        //Check if output and input are the same inventory to avoid duplication.
        Inventory tempOutput = sameInv ? sameInventory : Utils.copyInventory(output);
        HashMap<Integer, ItemStack> map = tempOutput.addItem(craftingResult.result());

        boolean isEmpty = Arrays.stream(craftingResult.matrixResult())
                .allMatch(itemStack -> (itemStack == null || itemStack.getType() == Material.AIR));

        // Add any leftover items from the recipe e.g buckets.
        HashMap<Integer, ItemStack> craftingMatrixLeftOvers =
                isEmpty ? Maps.newHashMap()
                        : tempOutput.addItem(craftingResult.matrixResult());

        //If result fits into output copy over the temporary inventories.
        if (map.isEmpty() && craftingMatrixLeftOvers.isEmpty()) {
            for (int i = 0; i < tempInvs.size(); i++) {
                moveTempInv(tempInvs.get(i), inputs.get(i));
            }
            if (!sameInv) moveTempInv(tempOutput, output);
            return true;
        }
        return false;
    }

    private void moveTempInv(Inventory tempInv, Inventory realInv) {
        ItemStack[] contents = new ItemStack[realInv.getSize()];
        System.arraycopy(tempInv.getContents(), 0, contents, 0, realInv.getSize());
        realInv.setContents(contents);
    }

    public void forceUpdateInventory() {
        inventory.getViewers().forEach(humanEntity -> {
            if (humanEntity instanceof Player) {
                ((Player) humanEntity).updateInventory();
            }
        });
    }

}
