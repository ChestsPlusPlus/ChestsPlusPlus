package com.jamesdpeters.minecraft.chests.v1_16_R1;

import com.jamesdpeters.minecraft.chests.Api;
import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.TileEntityOpener;
import com.jamesdpeters.minecraft.chests.v1_16_R1.tileentities.CustomTileEntityBarrel;
import com.jamesdpeters.minecraft.chests.v1_16_R1.tileentities.CustomTileEntityChest;
import net.minecraft.server.v1_16_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R1.block.CraftContainer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.List;


public class ChestOpener_1_16 implements ChestOpener {

    @Override
    public TileEntityOpener updateState(Inventory inventory, Container container, TileEntityOpener tileEntityOpener) {
        if(hasLiddedAPI()){
            if(container instanceof Lidded){
                if(inventory.getViewers().size() > 0){
                    ((Lidded) container).open();
                } else {
                    ((Lidded) container).close();
                }
                return null;
            }
            return null;
        }

        if(tileEntityOpener != null) {
            TileEntityOpener opener = tileEntityOpener;
            Bukkit.getScheduler().scheduleSyncDelayedTask(Api.getPlugin(), ()-> opener.setViewers(inventory.getViewers()),1);
            return tileEntityOpener;
        } else {
            CraftContainer craftContainer = (CraftContainer) container;
            CraftWorld craftWorld = (CraftWorld) craftContainer.getWorld();
            World world = craftWorld.getHandle();
            BlockPosition position = craftContainer.getPosition();
            TileEntity tileEntity = world.getTileEntity(position);

            //Checks if Tile Entity has already got custom Opener.
            if (tileEntity instanceof TileEntityOpener) {
                tileEntityOpener = (TileEntityOpener) tileEntity;
                TileEntityOpener opener = tileEntityOpener;
                Bukkit.getScheduler().scheduleSyncDelayedTask(Api.getPlugin(), ()-> opener.setViewers(inventory.getViewers()),1);
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
        Bukkit.getScheduler().scheduleSyncDelayedTask(Api.getPlugin(), ()-> tileEntOpener.setViewers(viewers),1);
    }

    private boolean hasLiddedAPI(){
        try {
            Class.forName("org.bukkit.block.Lidded");
            return true;
        } catch (ClassNotFoundException e){
            return false;
        }
    }
}
