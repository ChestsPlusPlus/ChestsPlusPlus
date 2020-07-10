package com.jamesdpeters.minecraft.chests.v1_15_R1;

import net.minecraft.server.v1_15_R1.Block;
import net.minecraft.server.v1_15_R1.BlockChest;
import net.minecraft.server.v1_15_R1.BlockPropertyChestType;
import net.minecraft.server.v1_15_R1.EntityHuman;
import net.minecraft.server.v1_15_R1.EnumDirection;
import net.minecraft.server.v1_15_R1.SoundCategory;
import net.minecraft.server.v1_15_R1.SoundEffect;
import net.minecraft.server.v1_15_R1.SoundEffects;
import net.minecraft.server.v1_15_R1.TileEntityChest;
import org.bukkit.entity.HumanEntity;

import java.util.List;

public class CustomTileEntityChest extends TileEntityChest {

    private int phantomViewers = 0;
    private List<HumanEntity> viewers;

    @Override
    public List<HumanEntity> getViewers() {
        return viewers;
    }

    @Override
    public void tick() {
        //Do nothing.
    }

    @Override
    public void onOpen() {
        Block block = this.getBlock().getBlock();
        if (block instanceof BlockChest) {
            this.world.playBlockAction(this.position, block, 1, viewers.size());
            this.world.applyPhysics(this.position, block);
        }
    }

    public void setViewers(List<HumanEntity> viewers){
        int previousViewers = phantomViewers;
        phantomViewers = viewers.size();
        this.viewers = viewers;

        if(phantomViewers > 1 && previousViewers == 0) this.a(SoundEffects.BLOCK_CHEST_OPEN);
        if(phantomViewers == 0) this.a(SoundEffects.BLOCK_CHEST_CLOSE);

        onOpen();
    }

    private void a(SoundEffect soundeffect) {
        BlockPropertyChestType blockpropertychesttype = (BlockPropertyChestType)this.getBlock().get(BlockChest.c);
        if (blockpropertychesttype != BlockPropertyChestType.LEFT) {
            double d0 = (double)this.position.getX() + 0.5D;
            double d1 = (double)this.position.getY() + 0.5D;
            double d2 = (double)this.position.getZ() + 0.5D;
            if (blockpropertychesttype == BlockPropertyChestType.RIGHT) {
                EnumDirection enumdirection = BlockChest.i(this.getBlock());
                d0 += (double)enumdirection.getAdjacentX() * 0.5D;
                d2 += (double)enumdirection.getAdjacentZ() * 0.5D;
            }

            this.world.playSound((EntityHuman)null, d0, d1, d2, soundeffect, SoundCategory.BLOCKS, 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
        }

    }
}
