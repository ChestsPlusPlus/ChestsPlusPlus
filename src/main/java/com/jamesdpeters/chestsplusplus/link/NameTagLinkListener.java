package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Name tag → link conversion: right-clicking an unlinked chest, barrel, crafting table etc. with a renamed name tag
 * links it to the group named on the tag ({@code name} or {@code owner:name}), picking the group type from the block.
 * Unnamed tags do nothing. Like a mob name tag, one is used up outside creative (configurable). Runs at HIGH so protection plugins
 * have already had their say on this real interaction.
 */
public final class NameTagLinkListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Services services;
    private final LinkService links;

    public NameTagLinkListener(Services services, LinkService links) {
        this.services = services;
        this.links = links;
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || links.isFiringSyntheticInteract()) return;
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || item == null || item.getType() != Material.NAME_TAG) return;
        String name = tagName(item);
        if (name == null) return;
        // Linked blocks keep their normal click behaviour (opening the group).
        if (services.nodes().get(BlockPos.of(block)) != null) return;
        GroupType type = typeFor(block);
        if (type == null) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        if (links.link(player, type, name, block, NodeListener.facingFor(event.getBlockFace(), player), true) == null)
            return;
        if (services.settings().linking().consumeNameTags() && player.getGameMode() != GameMode.CREATIVE)
            item.subtract();
    }

    /** The group type whose blocks include {@code block}, if its feature is enabled. */
    private @Nullable GroupType typeFor(Block block) {
        for (GroupType type : GroupType.values()) {
            GroupTypeHandler handler = links.handler(type);
            if (handler != null && links.isFeatureEnabled(type) && handler.isValidBlock(block)) return type;
        }
        return null;
    }

    /** The name written on the tag, or null if it was never renamed or is blank. */
    static @Nullable String tagName(ItemStack item) {
        Component custom = item.getData(DataComponentTypes.CUSTOM_NAME);
        if (custom == null) return null;
        String name = PLAIN.serialize(custom).trim();
        return name.isEmpty() ? null : name;
    }
}
