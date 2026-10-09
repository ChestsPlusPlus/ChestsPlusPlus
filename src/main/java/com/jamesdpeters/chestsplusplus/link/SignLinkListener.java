package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Sign → link conversion: a wall sign reading {@code [ChestLink]} / {@code [AutoCraft]} on line 1 and a
 * group ({@code name} or {@code owner:name}) on line 2 links the block it hangs on, then the sign is removed (it
 * becomes the node's display) and given back outside creative (configurable). Protection plugins approved placing the sign, not using
 * the block behind it (lock plugins usually allow signs on locked chests), so the target still gets the protection check.
 */
@RequiredArgsConstructor
public final class SignLinkListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final Services services;
    private final LinkService links;

    static @Nullable GroupType typeOf(String header) {
        return switch (header.trim().toLowerCase(Locale.ROOT)) {
            case "[chestlink]", "[cl]" -> GroupType.CHESTLINK;
            case "[autocraft]", "[ac]" -> GroupType.AUTOCRAFT;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onSignChange(SignChangeEvent event) {
        GroupType type = typeOf(plain(event.line(0)));
        if (type == null) return;
        Block sign = event.getBlock();
        if (!(sign.getBlockData() instanceof WallSign wallSign)) return;
        BlockFace facing = wallSign.getFacing();
        Block target = sign.getRelative(facing.getOppositeFace());
        String input = plain(event.line(1)).trim();

        Player player = event.getPlayer();
        if (links.link(player, type, input, target, facing, false) == null) return;
        event.setCancelled(true);
        boolean refund = !services.settings().linking().consumeSigns() && player.getGameMode() != GameMode.CREATIVE;
        // Remove the sign once the edit has been processed; the display replaces it.
        plugin.getServer().getScheduler().runTask(plugin, () -> removeSign(sign, refund ? player : null));
    }

    /** Only a sign still standing is refunded: one broken in the meantime has already dropped. */
    private static void removeSign(Block sign, @Nullable Player refundTo) {
        if (!(sign.getBlockData() instanceof WallSign data)) return;
        sign.setType(Material.AIR, false);
        if (refundTo != null && refundTo.isOnline()) give(refundTo, ItemStack.of(data.getPlacementMaterial()));
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    private static String plain(@Nullable Component line) {
        return line == null ? "" : PLAIN.serialize(line);
    }
}
