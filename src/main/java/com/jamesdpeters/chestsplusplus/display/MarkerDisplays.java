package com.jamesdpeters.chestsplusplus.display;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Spawns display entities that are never saved with their chunk and carry a marker, so ours can be told from anyone else's. */
public final class MarkerDisplays {

    private final NamespacedKey marker;

    public MarkerDisplays(Plugin plugin, String marker) {
        this.marker = new NamespacedKey(plugin, marker);
    }

    /** Spawns a {@code type} at {@code placement} on {@code block}; {@code setup} runs before it joins the world. */
    public <T extends Display> T spawn(Block block, DisplayLayout.Placement placement, Class<T> type, float viewRange, Consumer<? super T> setup) {
        Location at = block.getLocation().clone().add(placement.x(), placement.y(), placement.z());
        at.setYaw(placement.yaw());
        at.setPitch(0);
        return block.getWorld().spawn(at, type, entity -> {
            entity.setPersistent(false);
            entity.setViewRange(viewRange);
            entity.getPersistentDataContainer().set(marker, PersistentDataType.BOOLEAN, true);
            setup.accept(entity);
        });
    }

    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(marker, PersistentDataType.BOOLEAN);
    }

    /** A plain scale, with no rotation or translation. */
    public static Transformation scale(float x, float y, float z) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(x, y, z), new AxisAngle4f());
    }
}
