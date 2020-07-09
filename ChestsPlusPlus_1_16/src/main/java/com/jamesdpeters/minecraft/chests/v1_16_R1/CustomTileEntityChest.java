package com.jamesdpeters.minecraft.chests.v1_16_R1;

import net.minecraft.server.v1_16_R1.Block;
import net.minecraft.server.v1_16_R1.BlockChest;
import net.minecraft.server.v1_16_R1.BlockPropertyChestType;
import net.minecraft.server.v1_16_R1.EntityHuman;
import net.minecraft.server.v1_16_R1.EnumDirection;
import net.minecraft.server.v1_16_R1.SoundCategory;
import net.minecraft.server.v1_16_R1.SoundEffect;
import net.minecraft.server.v1_16_R1.SoundEffects;
import net.minecraft.server.v1_16_R1.TileEntityChest;
import org.bukkit.Bukkit;

public class CustomTileEntityChest extends TileEntityChest {

    private int phantomViewers = 0;
    private int previousViewers = 0;

    @Override
    public void tick() {
        //Don't need to tick
    }

    @Override
    protected void onOpen() {
       //Do nothing
    }

    public void animate(){
        Block block = this.getBlock().getBlock();
        if (block instanceof BlockChest) {
            this.world.playBlockAction(this.position, block, 1, phantomViewers);
            this.world.applyPhysics(this.position, block);

            //Play block sound.
            Bukkit.broadcastMessage("Viewers: "+phantomViewers+" Prev: "+previousViewers);
            if(phantomViewers == 1 && previousViewers == 0) this.a(SoundEffects.BLOCK_CHEST_OPEN);
            if(phantomViewers == 0) this.a(SoundEffects.BLOCK_CHEST_CLOSE);
        }
    }

    public void setOpen(boolean open){
        previousViewers = phantomViewers;
        if(open) phantomViewers++;
        else phantomViewers--;
        if(phantomViewers < 0) phantomViewers = 0;
    }

    private void a(SoundEffect soundeffect) {
        BlockPropertyChestType blockpropertychesttype = (BlockPropertyChestType)this.getBlock().get(BlockChest.c);
        if (blockpropertychesttype != BlockPropertyChestType.LEFT) {
            double d0 = (double)this.position.getX() + 0.5D;
            double d1 = (double)this.position.getY() + 0.5D;
            double d2 = (double)this.position.getZ() + 0.5D;
            if (blockpropertychesttype == BlockPropertyChestType.RIGHT) {
                EnumDirection enumdirection = BlockChest.h(this.getBlock());
                d0 += (double)enumdirection.getAdjacentX() * 0.5D;
                d2 += (double)enumdirection.getAdjacentZ() * 0.5D;
            }

            this.world.playSound((EntityHuman)null, d0, d1, d2, soundeffect, SoundCategory.BLOCKS, 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
        }

    }
}
