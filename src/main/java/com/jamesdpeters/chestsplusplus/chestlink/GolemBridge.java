package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import java.util.Comparator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Copper golems ↔ ChestLinks. A golem works on the block's own container, which stays empty for a linked block, and Paper has no way
 * to substitute it. Instead, when a golem opens a linked block, items go straight between the group and the golem's hand: the golem
 * then skips the empty container and carries on as if it had used it.
 */
@RequiredArgsConstructor
public final class GolemBridge implements Listener {

    /** Vanilla's copper golem carry limit. */
    static final int LOAD = 16;
    /** How far from the opened block the interacting golem can stand. */
    private static final double REACH = 2.5;

    private final Services services;
    private final ChestLinkService chestLinks;

    private boolean enabled() {
        return services.settings().features().copperGolems();
    }

    /**
     * Paper fires this several times per tick per searching golem, so non-linked blocks cost one hash lookup. Applies vanilla's rules to
     * the group rather than the empty container, and turns golems away entirely when the feature is off so nothing lands in it.
     */
    @EventHandler
    void onValidate(ItemTransportingEntityValidateTargetEvent event) {
        if (!(services.groupAt(event.getBlock()) instanceof ChestLinkGroup group)) return;
        if (!enabled() || !(event.getEntity() instanceof CopperGolem golem)) {
            event.setAllowed(false);
            return;
        }
        ItemStack held = golem.getEquipment().getItemInMainHand();
        event.setAllowed(held.isEmpty() ? !group.inventory().isEmpty() : accepts(group.inventory(), held));
    }

    /**
     * Chests delay their open and close callbacks, so these arrive without the golem, in the tick it starts or finishes interacting.
     * This fires for every game event, footsteps included, so the type checks come first.
     */
    @EventHandler
    void onGameEvent(GenericGameEvent event) {
        GameEvent type = event.getEvent();
        boolean open = type == GameEvent.CONTAINER_OPEN;
        if (!open && type != GameEvent.CONTAINER_CLOSE) return;
        Block block = event.getLocation().getBlock();
        if (!(services.groupAt(block) instanceof ChestLinkGroup group) || !enabled()) return;
        if (!open) {
            chestLinks.absorbPhysicalContents(group, block);
            return;
        }
        CopperGolem golem = interactingGolem(block);
        if (golem == null) return;
        switch (golem.getGolemState()) {
            case GETTING_NO_ITEM -> handOut(group, golem.getEquipment());
            case DROPPING_ITEM, DROPPING_NO_ITEM -> takeIn(group, golem.getEquipment());
            default -> {}
        }
    }

    private static @Nullable CopperGolem interactingGolem(Block block) {
        Location center = block.getLocation().toCenterLocation();
        return block.getWorld()
                .getNearbyEntitiesByType(CopperGolem.class, center, REACH, golem -> golem.getGolemState() != CopperGolem.State.IDLE)
                .stream()
                .min(Comparator.comparingDouble(golem -> golem.getLocation().distanceSquared(center)))
                .orElse(null);
    }

    /** Hands an empty-handed golem up to a load of the group's first stack. */
    private void handOut(ChestLinkGroup group, EntityEquipment equipment) {
        if (!equipment.getItemInMainHand().isEmpty()) return;
        Inventory inventory = group.inventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            int amount = Math.min(LOAD, stack.getAmount());
            equipment.setItemInMainHand(stack.asQuantity(amount));
            inventory.setItem(slot, stack.getAmount() == amount ? null : stack.asQuantity(stack.getAmount() - amount));
            chestLinks.changed(group);
            return;
        }
    }

    /** Takes what fits of the golem's load into the group; anything left is absorbed when the golem closes the block. */
    private void takeIn(ChestLinkGroup group, EntityEquipment equipment) {
        ItemStack held = equipment.getItemInMainHand();
        if (held.isEmpty()) return;
        Map<Integer, ItemStack> left = group.inventory().addItem(held.clone());
        equipment.setItemInMainHand(left.values().stream().findFirst().orElse(null));
        chestLinks.changed(group);
    }

    /** Vanilla's rule for a golem's drop-off: the container is empty, or already holds the item and has room for some of it. */
    static boolean accepts(Inventory inventory, ItemStack held) {
        if (inventory.isEmpty()) return true;
        boolean similar = false;
        boolean room = false;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.isEmpty()) {
                room = true;
            } else if (stack.isSimilar(held)) {
                similar = true;
                room |= stack.getAmount() < stack.getMaxStackSize();
            }
        }
        return similar && room;
    }
}
