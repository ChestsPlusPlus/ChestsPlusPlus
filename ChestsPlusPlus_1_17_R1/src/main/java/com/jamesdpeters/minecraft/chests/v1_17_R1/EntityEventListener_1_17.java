package com.jamesdpeters.minecraft.chests.v1_17_R1;

import com.jamesdpeters.minecraft.chests.EntityEventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.EntitiesLoadEvent;

public class EntityEventListener_1_17 extends EntityEventListener {

    @EventHandler
    public void onEntityLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::removeEntity);
    }
}
