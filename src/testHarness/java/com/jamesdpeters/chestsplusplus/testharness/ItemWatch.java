package com.jamesdpeters.chestsplusplus.testharness;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.jspecify.annotations.Nullable;

/**
 * Records the game tick an item first shows up in a watched container, so E2E specs can assert how many ticks something took
 * whatever the server's tick rate, instead of timing it from outside over RCON. Checked every tick, after ChestsPlusPlus's own tickers.
 */
final class ItemWatch implements Runnable {

    private final Map<Block, Material> watched = new HashMap<>();
    private final Map<Block, Long> seen = new HashMap<>();

    void watch(Block block, Material material) {
        seen.remove(block);
        watched.put(block, material);
    }

    @Nullable
    Long seen(Block block) {
        return seen.get(block);
    }

    void clear() {
        watched.clear();
        seen.clear();
    }

    @Override
    public void run() {
        watched.entrySet().removeIf(entry -> {
            Block block = entry.getKey();
            if (!(block.getState(false) instanceof Container container) || !container.getInventory().contains(entry.getValue())) return false;
            seen.put(block, block.getWorld().getGameTime());
            return true;
        });
    }
}
