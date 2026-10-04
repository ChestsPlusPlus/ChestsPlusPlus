package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Sign → link conversion: a wall sign reading {@code [ChestLink]} / {@code [AutoCraft]} on line 1 and a
 * group ({@code name} or {@code owner:name}) on line 2 links the block it hangs on, then the sign is removed (it
 * becomes the node's display). Since this is a real placement, protection plugins already approved it.
 */
public final class SignLinkListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final LinkService links;

    public SignLinkListener(Plugin plugin, LinkService links) {
        this.plugin = plugin;
        this.links = links;
    }

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

        if (links.link(event.getPlayer(), type, input, target, facing, true) == null) return;
        event.setCancelled(true);
        // Remove the sign once the edit has been processed; the display replaces it.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (sign.getBlockData() instanceof WallSign) sign.setType(Material.AIR, false);
        });
    }

    private static String plain(@Nullable Component line) {
        return line == null ? "" : PLAIN.serialize(line);
    }
}
