package com.jamesdpeters.minecraft.chests;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.stream.Stream;

public abstract class EntityEventListener implements Listener {

    protected void fixEntities(Chunk chunk) {
        removeEntities(chunk);
        setItemFrames(chunk);
    }

    public void fixEntities(World world) {
        removeEntities(world);
        setItemFrames(world);
    }

    private void removeEntities(World world) {
        world.getEntities().forEach(this::removeEntity);
    }

    private void removeEntities(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            removeEntity(entity);
        }
    }

    protected void removeEntity(Entity entity) {
        Integer val = entity.getPersistentDataContainer().get(Values.Instance().PluginKey, PersistentDataType.INTEGER);
        if (val != null && val == 1)
            entity.remove();
    }

    private void setItemFrames(World world) {
        setItemFrames(world.getEntities().stream());
    }

    private void setItemFrames(Chunk chunk) {
        setItemFrames(Arrays.stream(chunk.getEntities()));
    }

    private void setItemFrames(Stream<Entity> entityStream) {
        entityStream.filter(entity ->
                        (entity instanceof ItemFrame
                                && entity.getLocation().getBlock().getRelative(((ItemFrame) entity).getAttachedFace()).getState() instanceof Hopper))
                .forEach(entity -> Api.getNmsProvider().setItemFrameVisible((ItemFrame) entity, !PluginConfig.INVISIBLE_FILTER_ITEM_FRAMES.get()));
    }
}
