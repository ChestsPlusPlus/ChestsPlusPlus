package com.jamesdpeters.minecraft.chests.interfaces;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
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

public class VirtualCraftingHolder implements InventoryHolder {

    private Inventory inventory;
    private AutoCraftingStorage storage;
    private BukkitRunnable guiTask;
    private BukkitRunnable craftItemTask;

    private ItemStack[][] recipeChoices = new ItemStack[9][];
    private ItemStack result;
    private int[] recipeChoiceIndex = new int[9];
    private boolean hasCompleteRecipe = false;

    /**
     *  This gets set to true when updateCrafting() gets called on a 1 tick delay.
     */
    private boolean isUpdatingRecipe = false;

    private static ItemStack AIR = new ItemStack(Material.AIR);

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

    public void setCrafting(ShapelessRecipe shapelessRecipe){
        result = shapelessRecipe.getResult();
        List<RecipeChoice> choiceList = shapelessRecipe.getChoiceList();
        for(int i=0; i<choiceList.size(); i++){
            RecipeChoice recipeChoice = choiceList.get(i);
            if(recipeChoice instanceof RecipeChoice.MaterialChoice){
                RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) recipeChoice;
                ItemStack[] choices = materialChoice.getChoices().stream().map(ItemStack::new).toArray(ItemStack[]::new);
                recipeChoices[i] = choices;
            }
        }
        setHasCompleteRecipe();
    }

    public void setCrafting(ShapedRecipe recipe){
        result = recipe.getResult();
        int row = 0;
        for(String r : recipe.getShape()){
            int col = 0;
            for(char c : r.toCharArray()){
                RecipeChoice recipeChoice = recipe.getChoiceMap().get(c);
                if(recipeChoice instanceof RecipeChoice.MaterialChoice){
                    RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) recipeChoice;
                    ItemStack[] choices = materialChoice.getChoices().stream().map(ItemStack::new).toArray(ItemStack[]::new);
                    int i = (row * 3) + col;
                    recipeChoices[i] = choices;
                }
                col++;
            }
            row++;
        }
        setHasCompleteRecipe();
    }

    public void setCrafting(Recipe recipe){
        if(recipe instanceof ShapedRecipe) setCrafting((ShapedRecipe) recipe);
        if(recipe instanceof ShapelessRecipe) setCrafting((ShapelessRecipe) recipe);
    }

    private void setHasCompleteRecipe(){
        hasCompleteRecipe = true;
        startCraftingItems();
    }

    public void resetChoices(){
        recipeChoices = new ItemStack[9][];
        result = null;
        hasCompleteRecipe = false;
        stopCraftingItems();
    }

    public void updateCrafting(){
        List<ItemStack> craftingMatrix = new ArrayList<>(Arrays.asList(inventory.getContents()));
        if (craftingMatrix.get(0) != null) craftingMatrix.remove(0);
        Recipe recipe = Crafting.getResult(craftingMatrix);
        getStorage().setRecipe(recipe);
        resetChoices();
        if(recipe != null){
            setCrafting(recipe);
            playSound(Sound.BLOCK_NOTE_BLOCK_CHIME,0.5f,1f);
        }
        isUpdatingRecipe = false;
        updateGUI();
    }

    private void playSound(Sound sound, float volume, float pitch){
        storage.getInventory().getViewers().forEach(humanEntity -> {
            humanEntity.getWorld().playSound(humanEntity.getLocation(), sound, volume, pitch);
        });
    }

    public VirtualCraftingHolder setUpdatingRecipe(boolean updatingRecipe) {
        isUpdatingRecipe = updatingRecipe;
        return this;
    }

    //Start and stop animation based on if the inventory is open.
    public void startAnimation(){
        guiTask = new UpdateTask();
    }
    public void stopAnimation(){
        if(guiTask != null) guiTask.cancel();
    }

    private class UpdateTask extends BukkitRunnable {
        BukkitTask task;
        public UpdateTask(){
            task = runTaskTimer(ChestsPlusPlus.PLUGIN,1,15);
        }
        @Override
        public void run() {
            updateGUI();
        }
    }

    public void updateGUI(){
        inventory.setItem(0, result);
        if(hasCompleteRecipe && !isUpdatingRecipe) {
            for (int i = 0; i < 9; i++) {
                ItemStack[] choices = recipeChoices[i];
                if (choices != null) {
                    int index = recipeChoiceIndex[i];
                    ItemStack choice;
                    if (index < choices.length) {
                        choice = choices[index];
                    } else {
                        recipeChoiceIndex[i] = 0;
                        choice = choices[0];
                    }
                    inventory.setItem(i + 1, choice);
                    recipeChoiceIndex[i]++;
                } else {
                    inventory.setItem(i + 1, null);
                }
            }
        }
    }

    public void startCraftingItems(){
        craftItemTask = new CraftItems();
    }
    public void stopCraftingItems(){
        if(craftItemTask != null) craftItemTask.cancel();
    }

    private class CraftItems extends BukkitRunnable {
        BukkitTask task;
        CraftItems(){
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
    public void craftItem(){
        for(Location location : storage.getLocations()){
            Block block = location.getBlock();
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            Block blockAbove = block.getRelative(BlockFace.UP);

            Inventory output;

            if(blockBelow.getState() instanceof Hopper){
                Hopper hopper = (Hopper) blockBelow.getState();
                output = hopper.getInventory();
            } else {
                output = getInventory(blockBelow);
                if(output == null) continue;
                //If crafting table is powered output into container is possible.
                if(!block.isBlockPowered()) continue;
            }

            boolean didCraft = false;
            if(craftItem(blockAbove,output)) didCraft = true;
            if(craftItemIfHopperSource(block.getRelative(BlockFace.NORTH),output)) didCraft = true;
            if(craftItemIfHopperSource(block.getRelative(BlockFace.EAST),output)) didCraft = true;
            if(craftItemIfHopperSource(block.getRelative(BlockFace.SOUTH),output)) didCraft = true;
            if(craftItemIfHopperSource(block.getRelative(BlockFace.WEST),output)) didCraft = true;

            //Play sound if crafting occured.
            if(didCraft) if(location.getWorld() != null) {
                location.getWorld().playSound(location, Sound.BLOCK_DISPENSER_DISPENSE, 0.25f, 1f);
            }
        }
    }

    private boolean craftItemIfHopperSource(Block sourceBlock, Inventory output){
        if(sourceBlock.getState() instanceof Hopper){
            return craftItem(sourceBlock, output);
        }
        return false;
    }

    private boolean craftItem(Block sourceBlock, Inventory output){
        Inventory source = getInventory(sourceBlock);
        if(source == null) return false;
        return craftItem(source, output);
    }

    private Inventory getInventory(Block block){
        Inventory inventory = null;
        if(block.getState() instanceof Container){
            InventoryStorage storage = Config.getInventoryStorage(block.getLocation());
            //Check if a ChestLink exists above the CraftingTable and if the owner of the CraftingTable has permission to access that Chest.
            if(storage != null){
                if(storage.hasPermission(this.storage.getOwner())) {
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

    private boolean craftItem(Inventory inputInventory, Inventory output){
        boolean sameInv = inputInventory.equals(output);

        Inventory tempInv = Utils.copyInventory(inputInventory);
        for(ItemStack[] choices : recipeChoices){
            if(choices == null) continue;
            boolean foundMatch = false;
            for(ItemStack choice : choices){
                int index = tempInv.first(choice.getType());
                if(index != -1){
                    ItemStack item = tempInv.getItem(index);
                    if(item != null) {
                        item.setAmount(item.getAmount() - 1);
                        tempInv.setItem(index, item);
                        foundMatch = true;
                        break;
                    }
                }
            }
            //If no match
            if(!foundMatch) return false;
        }

        //If we reach here there are enough materials so check for space in the Hopper and update inventory.
        //Check if output and input are the same inventory to avoid duplication.
        Inventory tempOutput = sameInv ? tempInv : Utils.copyInventory(output);
        HashMap map = tempOutput.addItem(result.clone());

        //If result fits into output copy over the temporary inventories.
        if(map.isEmpty()){
            moveTempInv(tempInv,inputInventory);
            if(!sameInv) moveTempInv(tempOutput, output);
            return true;
        }
        return false;
    }

    private void moveTempInv(Inventory tempInv, Inventory realInv){
        ItemStack[] contents = new ItemStack[realInv.getSize()];
        System.arraycopy(tempInv.getContents(), 0, contents,0,realInv.getSize());
        realInv.setContents(contents);
    }

    public void forceUpdateInventory(){
        inventory.getViewers().forEach(humanEntity -> {
            if(humanEntity instanceof Player){
                ((Player) humanEntity).updateInventory();
            }
        });
    }

}
