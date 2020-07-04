package com.jamesdpeters.minecraft.chests;

import net.minecraft.server.v1_16_R1.Block;
import net.minecraft.server.v1_16_R1.BlockChest;
import net.minecraft.server.v1_16_R1.TileEntityChest;

public class CustomTileEntityChest extends TileEntityChest {

    private int phantomViewers;

    @Override
    protected void onOpen() {
        Block block = this.getBlock().getBlock();
        if (block instanceof BlockChest) {
            this.world.playBlockAction(this.position, block, 1, phantomViewers);
            this.world.applyPhysics(this.position, block);
        }
    }

    public void setOpen(boolean open){
        if(open) phantomViewers++;
        else phantomViewers--;
        if(phantomViewers < 0) phantomViewers = 0;
    }
}
