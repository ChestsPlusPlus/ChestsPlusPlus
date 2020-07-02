package com.jamesdpeters.minecraft.chests.runnables;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;

public class ChestLinkVerifier extends BukkitRunnable {

    private Block block;
    private int delay = 1;

    public ChestLinkVerifier(Block block){
        this.block = block;
    }

    public void check(){
        runTaskLater(ChestsPlusPlus.PLUGIN,delay);
    }

    public ChestLinkVerifier withDelay(int delay){
        this.delay = delay;
        return this;
    }

    @Override
    public void run() {
        Chest chest = (Chest) block.getState();

        if(chest.getInventory().getHolder() instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) chest.getInventory().getHolder();
            InventoryHolder right = doubleChest.getRightSide();
            InventoryHolder left = doubleChest.getLeftSide();
            if(isChestLinked(doubleChest) && left != null && right != null) {
                convertToSingleChest(right.getInventory());
                convertToSingleChest(left.getInventory());
                convertToSingleChest(right.getInventory());
            }
        }
    }

    /**
     * Converts the block associated with this inventory into a Single Chest.
     * @param inventory
     */
    private void convertToSingleChest(Inventory inventory){
        if(inventory != null) {
            org.bukkit.block.data.type.Chest blockData = (org.bukkit.block.data.type.Chest) inventory.getLocation().getBlock().getBlockData();
            blockData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            inventory.getLocation().getBlock().setBlockData(blockData);
        }
    }

    /**
     * Checks if either side of a @{@link DoubleChest} has been added to a ChestLink
     * @param chest - the double chest being tested.
     * @return true if either side has been added to a ChestLink.
     */
    private boolean isChestLinked(DoubleChest chest){
        Location chestSide1 = block.getLocation();
        Location diff = chest.getLocation().clone().subtract(chestSide1).multiply(2);
        Location chestSide2 = chestSide1.clone().add(diff);

        ChestLinkStorage leftStorage = Config.getChestLink().getStorage(chestSide1);
        ChestLinkStorage rightStorage = Config.getChestLink().getStorage(chestSide2);

        return (leftStorage != null) || (rightStorage != null);
    }
}
