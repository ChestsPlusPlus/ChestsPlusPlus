package com.jamesdpeters.minecraft.chests.v1_14_R1;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.TileEntityOpener;
import com.jamesdpeters.minecraft.chests.v1_14_R1.tileentities.CustomTileEntityBarrel;
import com.jamesdpeters.minecraft.chests.v1_14_R1.tileentities.CustomTileEntityChest;
import net.minecraft.server.v1_14_R1.BlockPosition;
import net.minecraft.server.v1_14_R1.TileEntity;
import net.minecraft.server.v1_14_R1.TileEntityBarrel;
import net.minecraft.server.v1_14_R1.TileEntityChest;
import net.minecraft.server.v1_14_R1.TileEntityChestTrapped;
import net.minecraft.server.v1_14_R1.TileEntityTypes;
import net.minecraft.server.v1_14_R1.World;
import org.bukkit.block.Container;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.block.CraftContainer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class ChestOpener_1_14 implements ChestOpener {

    @Override
    public TileEntityOpener updateState(Inventory inventory, Container chest, TileEntityOpener tileEntityOpener) {
        if(tileEntityOpener != null) {
            tileEntityOpener.setViewers(inventory.getViewers());
            return tileEntityOpener;
        } else {
            CraftContainer craftContainer = (CraftContainer) chest;
            CraftWorld craftWorld = (CraftWorld) craftContainer.getWorld();
            World world = craftWorld.getHandle();
            BlockPosition position = craftContainer.getPosition();
            TileEntity tileEntity = world.getTileEntity(position);

            //Checks if Tile Entity has already got custom Opener.
            if (tileEntity instanceof TileEntityOpener) {
                tileEntityOpener = (TileEntityOpener) tileEntity;
                tileEntityOpener.setViewers(inventory.getViewers());
                return tileEntityOpener;
            } else {
                //If not set the new tile entity and set the viewers.
                if (tileEntity instanceof TileEntityChest) {
                    CustomTileEntityChest tileEntityChest = new CustomTileEntityChest(tileEntity instanceof TileEntityChestTrapped ? TileEntityTypes.TRAPPED_CHEST : TileEntityTypes.CHEST);
                    setTileEnt(world, position, tileEntityChest, inventory.getViewers());
                    return tileEntityChest;
                } else if (tileEntity instanceof TileEntityBarrel) {
                    CustomTileEntityBarrel barrel = new CustomTileEntityBarrel();
                    setTileEnt(world, position, barrel, inventory.getViewers());
                    return barrel;
                }
            }
            return null;
        }
    }

    private <T extends TileEntity & TileEntityOpener> void setTileEnt(World world, BlockPosition position, T tileEntOpener, List<HumanEntity> viewers){
        world.removeTileEntity(position);
        world.setTileEntity(position, tileEntOpener);
        tileEntOpener.setViewers(viewers);
    }
}
