package com.jamesdpeters.minecraft.chests.v1_14_R1;

import com.jamesdpeters.minecraft.chests.EntityEventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;

public class EntityEventListener_1_14 extends EntityEventListener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        fixEntities(event.getChunk());
    }

}
