package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.PluginConfig;
import com.jamesdpeters.minecraft.chests.Values;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.stream.Stream;

public class EntityEventListener implements Listener {

    @EventHandler
    void onEntityLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(EntityEventListener::removeEntity);
    }

    protected static void fixEntities(Chunk chunk) {
        removeEntities(chunk);
        setItemFrames(chunk);
    }

    public static void fixEntities(World world) {
        removeEntities(world);
        setItemFrames(world);
    }

    private static void removeEntities(World world) {
        world.getEntities().forEach(EntityEventListener::removeEntity);
    }

    private static void removeEntities(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            removeEntity(entity);
        }
    }

    protected static void removeEntity(Entity entity) {
        Integer val = entity.getPersistentDataContainer().get(Values.Instance().PluginKey, PersistentDataType.INTEGER);
        if (val != null && val == 1)
            entity.remove();
    }

    private static void setItemFrames(World world) {
        setItemFrames(world.getEntities().stream());
    }

    private static void setItemFrames(Chunk chunk) {
        setItemFrames(Arrays.stream(chunk.getEntities()));
    }

    private static void setItemFrames(Stream<Entity> entityStream) {
        entityStream
                // Filter Item frames that are connected to Hoppers.
                .filter(entity -> (entity instanceof ItemFrame
                        && entity.getLocation().getBlock().getRelative(((ItemFrame) entity).getAttachedFace()).getState() instanceof Hopper))
                .forEach(entity -> ((ItemFrame) entity).setVisible(!PluginConfig.INVISIBLE_FILTER_ITEM_FRAMES.get()));
    }

}
