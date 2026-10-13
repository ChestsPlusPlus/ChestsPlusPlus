package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.jspecify.annotations.Nullable;

/** Holds a ChestLink's lids open while anyone has its inventory open. */
@RequiredArgsConstructor
public final class LidAnimator {

    private final NodeIndex nodes;
    private final Supplier<Settings> settings;
    /** Lids currently held open per group, so the last viewer closing can close them. */
    private final Map<Long, Set<BlockPos>> openLids = new HashMap<>();

    /** Opens the lids not open yet: every node's when animating all nodes, else only {@code from}'s (null for a remote open). */
    public void open(ChestLinkGroup group, @Nullable BlockPos from) {
        Set<BlockPos> lids = openLids.computeIfAbsent(group.id(), k -> new HashSet<>());
        List<BlockPos> targets = new ArrayList<>();
        if (settings.get().chestlink().animateAllNodes()) {
            nodes.nodesOf(group.id()).forEach(node -> targets.add(node.pos()));
        } else if (from != null) {
            targets.add(from);
        }
        for (BlockPos pos : targets) {
            if (lids.contains(pos) || !pos.isLoaded()) continue;
            Block block = pos.block();
            if (block != null && block.getState(false) instanceof Lidded lidded) {
                lidded.open();
                lids.add(pos);
                playSound(block, Sound.BLOCK_BARREL_OPEN, Sound.BLOCK_CHEST_OPEN);
            }
        }
    }

    /** The last viewer left: closes every lid the group's viewers opened. */
    public void close(ChestLinkGroup group) {
        Set<BlockPos> lids = openLids.remove(group.id());
        if (lids == null) return;
        for (BlockPos pos : lids) {
            Block block = pos.isLoaded() ? pos.block() : null;
            if (block != null && block.getState(false) instanceof Lidded lidded) {
                lidded.close();
                playSound(block, Sound.BLOCK_BARREL_CLOSE, Sound.BLOCK_CHEST_CLOSE);
            }
        }
    }

    private static void playSound(Block block, Sound barrel, Sound chest) {
        Sound sound = block.getType() == Material.BARREL ? barrel : chest;
        block.getWorld().playSound(block.getLocation().clone().add(0.5, 0.5, 0.5), sound, 0.5f, 1f);
    }
}
