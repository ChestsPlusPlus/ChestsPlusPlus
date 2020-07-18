package com.jamesdpeters.minecraft.chests.v1_14_R1;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.TileEntityOpener;
import com.jamesdpeters.minecraft.chests.v1_14_R1.tileentities.CustomTileEntityBarrel;
import com.jamesdpeters.minecraft.chests.v1_14_R1.tileentities.CustomTileEntityChest;
import net.minecraft.server.v1_14_R1.BlockPosition;
import net.minecraft.server.v1_14_R1.TileEntity;
import net.minecraft.server.v1_14_R1.TileEntityBarrel;
import net.minecraft.server.v1_14_R1.TileEntityChest;
import net.minecraft.server.v1_14_R1.World;
import org.bukkit.block.Container;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.block.CraftChest;
import org.bukkit.craftbukkit.v1_14_R1.block.CraftContainer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class ChestOpener_1_14 implements ChestOpener {

    @Override
    public void setLidOpen(Inventory inventory, Container chest, boolean open) {
        CraftContainer craftContainer = (CraftContainer) chest;
        CraftWorld craftWorld = (CraftWorld) craftContainer.getWorld();
        World world = craftWorld.getHandle();
        BlockPosition position = craftContainer.getPosition();

        TileEntity tileEntity = world.getTileEntity(position);

        //Checks if Tile Entity has already got custom Opener.
        if(tileEntity instanceof TileEntityOpener){
            ((TileEntityOpener) tileEntity).setViewers(inventory.getViewers());
        } else {
            //If not set the new tile entity and set the viewers.
            if (tileEntity instanceof TileEntityChest) {
                setTileEnt(world, position, new CustomTileEntityChest(), inventory.getViewers());
            } else if (tileEntity instanceof TileEntityBarrel) {
                setTileEnt(world, position, new CustomTileEntityBarrel(), inventory.getViewers());
            }
        }
    }

    private <T extends TileEntity & TileEntityOpener> void setTileEnt(World world, BlockPosition position, T tileEntOpener, List<HumanEntity> viewers){
        world.removeTileEntity(position);
        world.setTileEntity(position, tileEntOpener);
        tileEntOpener.setViewers(viewers);
    }
}
