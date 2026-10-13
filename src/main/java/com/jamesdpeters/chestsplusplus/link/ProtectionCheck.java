package com.jamesdpeters.chestsplusplus.link;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Protection check for links that don't come from a real placement: fires a synthetic right-click and requires that no plugin denied
 * using the block. Our own interact listeners must ignore that click.
 */
public final class ProtectionCheck {

    private boolean firingSyntheticInteract;

    /** Is the synthetic interact event being fired right now? Listeners must ignore it. */
    public boolean isFiringSyntheticInteract() {
        return firingSyntheticInteract;
    }

    public boolean passes(Player player, Block block, BlockFace face) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), block, face,
                EquipmentSlot.HAND);
        firingSyntheticInteract = true;
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            firingSyntheticInteract = false;
        }
        return event.useInteractedBlock() != Event.Result.DENY;
    }
}
