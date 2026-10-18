package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.PaperApi;
import com.jamesdpeters.chestsplusplus.core.scheduler.MainThread;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.Locale;
import java.util.function.Supplier;
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
import org.jspecify.annotations.Nullable;

/**
 * Sign → link conversion: a wall sign reading {@code [ChestLink]} / {@code [AutoCraft]} on line 1 and a group ({@code name} or
 * {@code owner:name}, optionally in brackets as v2 required) on line 2 links the block it hangs on, then the sign is removed (it
 * becomes the node's display) and given back outside creative (configurable). Runs at MONITOR so every protection plugin has ruled on
 * the sign edit before anything changes. They approved editing the sign, not using the block behind it (lock plugins usually allow
 * signs on locked chests), so the target still gets the protection check.
 */
@RequiredArgsConstructor
public final class SignLinkListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final MainThread mainThread;
    private final Supplier<Settings> settings;
    private final LinkService links;

    static @Nullable GroupType typeOf(String header) {
        return switch (header.trim().toLowerCase(Locale.ROOT)) {
            case "[chestlink]", "[cl]" -> GroupType.CHESTLINK;
            case "[autocraft]", "[ac]" -> GroupType.AUTOCRAFT;
            default -> null;
        };
    }

    static String groupOf(String line) {
        String trimmed = line.trim();
        return trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")
                ? trimmed.substring(1, trimmed.length() - 1).trim()
                : trimmed;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onSignChange(SignChangeEvent event) {
        GroupType type = typeOf(plain(event.line(0)));
        if (type == null) return;
        Block sign = event.getBlock();
        if (!(sign.getBlockData() instanceof WallSign wallSign)) return;
        BlockFace facing = wallSign.getFacing();
        Block target = sign.getRelative(facing.getOppositeFace());
        String input = groupOf(plain(event.line(1)));

        Player player = event.getPlayer();
        if (links.link(player, type, input, target, facing, false) == null) return;
        boolean refund = !settings.get().linking().consumeSigns() && player.getGameMode() != GameMode.CREATIVE;
        // Remove the sign once the edit has been processed; the display replaces it.
        mainThread.nextTick(() -> removeSign(sign, refund ? player : null));
    }

    /** Only a sign still standing is refunded: one broken in the meantime has already dropped. */
    private static void removeSign(Block sign, @Nullable Player refundTo) {
        if (!(sign.getBlockData() instanceof WallSign data)) return;
        sign.setType(Material.AIR, false);
        if (refundTo != null && refundTo.isOnline()) give(refundTo, ItemStack.of(data.getPlacementMaterial()));
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(PaperApi.location(player), left));
    }

    private static String plain(@Nullable Component line) {
        return line == null ? "" : PLAIN.serialize(line);
    }
}
