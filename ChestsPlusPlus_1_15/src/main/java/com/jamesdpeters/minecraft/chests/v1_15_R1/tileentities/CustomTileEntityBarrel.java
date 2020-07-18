package com.jamesdpeters.minecraft.chests.v1_15_R1.tileentities;

import com.jamesdpeters.minecraft.chests.TileEntityOpener;
import net.minecraft.server.v1_15_R1.BaseBlockPosition;
import net.minecraft.server.v1_15_R1.BlockBarrel;
import net.minecraft.server.v1_15_R1.EntityHuman;
import net.minecraft.server.v1_15_R1.EnumDirection;
import net.minecraft.server.v1_15_R1.IBlockData;
import net.minecraft.server.v1_15_R1.SoundCategory;
import net.minecraft.server.v1_15_R1.SoundEffect;
import net.minecraft.server.v1_15_R1.SoundEffects;
import net.minecraft.server.v1_15_R1.TileEntityBarrel;
import org.bukkit.entity.HumanEntity;

import java.util.List;

public class CustomTileEntityBarrel extends TileEntityBarrel implements TileEntityOpener {

    private int phantomViewers = 0;
    private List<HumanEntity> viewers;

    @Override
    public List<HumanEntity> getViewers() {
        return viewers;
    }

    @Override
    public void h() {
        //super.h();
    }

    @Override
    public void startOpen(EntityHuman entityhuman) {
        //Do nothing
    }

    @Override
    public void closeContainer(EntityHuman entityhuman) {
        //do nothing.
    }

    @Override
    public void setViewers(List<HumanEntity> viewers){
        int previousViewers = phantomViewers;
        phantomViewers = viewers.size();
        this.viewers = viewers;

        if(phantomViewers > 1 && previousViewers == 0) open();
        if(phantomViewers == 0) close();
    }

    public void open(){
        IBlockData iblockdata = this.getBlock();
        a(iblockdata, SoundEffects.BLOCK_BARREL_OPEN);
        a(iblockdata, true);
    }

    public void close(){
        IBlockData iblockdata = this.getBlock();
        a(iblockdata, SoundEffects.BLOCK_BARREL_CLOSE);
        a(iblockdata, false);
    }

    private void a(IBlockData iblockdata, boolean flag) {
        this.world.setTypeAndData(this.getPosition(), (IBlockData)iblockdata.set(BlockBarrel.b, flag), 3);
    }

    private void a(IBlockData iblockdata, SoundEffect soundeffect) {
        BaseBlockPosition baseblockposition = ((EnumDirection)iblockdata.get(BlockBarrel.a)).p();
        double d0 = (double)this.position.getX() + 0.5D + (double)baseblockposition.getX() / 2.0D;
        double d1 = (double)this.position.getY() + 0.5D + (double)baseblockposition.getY() / 2.0D;
        double d2 = (double)this.position.getZ() + 0.5D + (double)baseblockposition.getZ() / 2.0D;
        this.world.playSound((EntityHuman)null, d0, d1, d2, soundeffect, SoundCategory.BLOCKS, 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
    }
}
