package com.jamesdpeters.minecraft.chests.v1_16_R1;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import net.minecraft.server.v1_16_R1.BlockPosition;
import net.minecraft.server.v1_16_R1.TileEntity;
import net.minecraft.server.v1_16_R1.TileEntityChest;
import net.minecraft.server.v1_16_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R1.block.CraftChest;
import org.bukkit.inventory.Inventory;


public class ChestOpener_1_16 implements ChestOpener {

    @Override
    public void setLidOpen(Inventory inventory, Chest chest, boolean open) {
        CraftChest craftChest = (CraftChest) chest;
        CraftWorld craftWorld = (CraftWorld) craftChest.getWorld();
        World world = craftWorld.getHandle();
        BlockPosition position = craftChest.getPosition();

        TileEntity tileEntity = world.getTileEntity(position);
        if(tileEntity instanceof TileEntityChest){

            boolean isCustomTileEntity = tileEntity instanceof CustomTileEntityChest;
            CustomTileEntityChest customTileEntityChest;
            if(!isCustomTileEntity){
                customTileEntityChest = new CustomTileEntityChest();
                world.removeTileEntity(position);
                world.setTileEntity(position, customTileEntityChest);
            } else {
                customTileEntityChest = (CustomTileEntityChest) tileEntity;
            }
            customTileEntityChest.setViewers(inventory.getViewers());
        }
    }
}