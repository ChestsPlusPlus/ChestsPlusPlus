package com.jamesdpeters.chestsplusplus.v2fixture;

import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * The benchmark world (docs/benchmarking.md): rows of cells, each a hopper chain that ends in a barrel, so the items in the barrels count
 * the work done. Every cell has enough input to run for about ten minutes.
 * <ul>
 * <li>ChestLink: a chest feeds a hopper into one linked chest, and a hopper under a second chest of the same group drains it.</li>
 * <li>AutoCraft: a linked chest of coal and sticks above a crafting table that makes torches into a hopper below.</li>
 * <li>Filter: a hopper with a v2 item-frame filter allowing andesite pulls from a chest of andesite and dirt.</li>
 * </ul>
 */
final class BenchmarkScenario {

    private static final int PER_ROW = 10;
    private static final int ROW_STRIDE = 3;
    private static final int SECTION_GAP = 4;

    private final OfflinePlayer owner;
    private final World world;
    private final int y;

    BenchmarkScenario(OfflinePlayer owner) {
        this.owner = owner;
        this.world = Bukkit.getWorlds().getFirst();
        this.y = world.getHighestBlockYAt(0, 0) + 1;
    }

    /** Builds the cells and returns the block area they cover as {@code minX minZ maxX maxZ}. */
    String build(int chestLinks, int autoCrafters, int filters) {
        quieten();
        int z = 0;
        for (int i = 0; i < chestLinks; i++) chestLinkCell(i, (i % PER_ROW) * 4, z + (i / PER_ROW) * ROW_STRIDE);
        z += rows(chestLinks) * ROW_STRIDE + SECTION_GAP;
        for (int i = 0; i < autoCrafters; i++) autoCraftCell(i, (i % PER_ROW) * 3, z + (i / PER_ROW) * ROW_STRIDE);
        z += rows(autoCrafters) * ROW_STRIDE + SECTION_GAP;
        for (int i = 0; i < filters; i++) filterCell((i % PER_ROW) * 2, z + (i / PER_ROW) * ROW_STRIDE);
        z += rows(filters) * ROW_STRIDE;
        int maxX = PER_ROW * 4;
        for (int cx = -1; cx <= maxX >> 4; cx++) for (int cz = -1; cz <= z >> 4; cz++) world.setChunkForceLoaded(cx, cz, true);
        return "-2 -2 " + maxX + " " + z;
    }

    private static int rows(int cells) {
        return (cells + PER_ROW - 1) / PER_ROW;
    }

    /** No mobs, weather, daylight or random ticks, so the runs differ only in the plugin. */
    private void quieten() {
        world.setSpawnLocation(0, y, -8);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.getEntitiesByClass(Mob.class).forEach(Mob::remove);
    }

    private void chestLinkCell(int index, int x, int z) {
        block(x + 2, 0, z, Material.BARREL);
        facing(block(x + 2, 1, z, Material.HOPPER), BlockFace.DOWN);
        Block drain = facing(block(x + 2, 2, z, Material.CHEST), BlockFace.NORTH);
        Block feed = facing(block(x, 2, z, Material.CHEST), BlockFace.NORTH);
        facing(block(x, 3, z, Material.HOPPER), BlockFace.DOWN);
        fill(block(x, 4, z, Material.CHEST), Material.COBBLESTONE, 27);
        String name = "bench" + index;
        ChestLinkStorage storage = Scenario.chestLink(owner, name, feed, BlockFace.NORTH);
        storage.addLocation(drain.getLocation(), Scenario.linkSign(drain, BlockFace.NORTH, Scenario.CHESTLINK_TAG, name, owner));
    }

    private void autoCraftCell(int index, int x, int z) {
        block(x + 1, 0, z, Material.BARREL);
        facing(block(x, 0, z, Material.HOPPER), BlockFace.EAST);
        Block table = block(x, 1, z, Material.CRAFTING_TABLE);
        Block input = facing(block(x, 2, z, Material.CHEST), BlockFace.NORTH);
        Container inputs = (Container) input.getState();
        for (int i = 0; i < 13; i++) inputs.getInventory().addItem(new ItemStack(Material.COAL, 64), new ItemStack(Material.STICK, 64));
        Scenario.chestLink(owner, "benchin" + index, input, BlockFace.NORTH);
        Scenario.autoCraft(owner, "benchac" + index, table, BlockFace.NORTH,
                Objects.requireNonNull(Bukkit.getRecipe(NamespacedKey.minecraft("torch"))),
                Scenario.grid(new ItemStack(Material.COAL), null, null, new ItemStack(Material.STICK)));
    }

    /** Two stacks of andesite to every one of dirt, so the filter has to skip slots. */
    private void filterCell(int x, int z) {
        block(x, 0, z, Material.BARREL);
        Block hopper = facing(block(x, 1, z, Material.HOPPER), BlockFace.DOWN);
        Container source = (Container) block(x, 2, z, Material.CHEST).getState();
        for (int i = 0; i < 27; i++) source.getInventory().setItem(i, new ItemStack(i % 3 == 2 ? Material.DIRT : Material.ANDESITE, 64));
        world.spawn(hopper.getRelative(BlockFace.NORTH).getLocation(), ItemFrame.class, frame -> {
            frame.setFacingDirection(BlockFace.NORTH, true);
            frame.setItem(new ItemStack(Material.ANDESITE));
            frame.setRotation(Rotation.NONE);
        });
    }

    /** Sets the block {@code up} blocks above the ground. */
    private Block block(int x, int up, int z, Material type) {
        Block block = world.getBlockAt(x, y + up, z);
        block.setType(type);
        return block;
    }

    private static Block facing(Block block, BlockFace face) {
        Scenario.facing(block, face);
        return block;
    }

    private static void fill(Block chest, Material material, int stacks) {
        Container container = (Container) chest.getState();
        for (int i = 0; i < stacks; i++) container.getInventory().addItem(new ItemStack(material, 64));
    }
}
