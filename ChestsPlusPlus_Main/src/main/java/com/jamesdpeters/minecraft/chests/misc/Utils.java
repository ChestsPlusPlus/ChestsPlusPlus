package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.filters.Filter;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.PluginConfig;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Utils {

    public static void openChestInventory(Player player, ChestLinkStorage storage, LocationInfo openedChestLocation) {
        player.openInventory(storage.getInventory());
        //Check if all chests should perform open animation.
        if (PluginConfig.SHOULD_ANIMATE_ALL_CHESTS.get()) {
            storage.getLocations().forEach(locationInfo -> {
                Location location = locationInfo.getLocation();
                if (location != null) {
                    containerAnimation(storage.getInventory(), locationInfo, true);
                }
            });
        } else {
            containerAnimation(storage.getInventory(), openedChestLocation, true);
        }
    }

    private static void containerAnimation(Inventory inventory, LocationInfo location, boolean open) {
        if (location != null && Utils.isLocationChunkLoaded(location.getLocation())) {
            Block block = location.getLocation().getBlock();
            if (block.getState() instanceof Container chest) {
                if (open) {
                    location.setTileEntityOpener(ApiSpecific.getChestOpener().updateState(inventory, chest, location.getTileEntityOpener()));
                } else {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> {
                        location.setTileEntityOpener(ApiSpecific.getChestOpener().updateState(inventory, chest, location.getTileEntityOpener()));
                    }, 1);
                }
            }
        }
    }

    public static void closeStorageInventory(ChestLinkStorage storage) {
        storage.getLocations().forEach(locationInfo -> {
            Location location = locationInfo.getLocation();
            if (location != null) {
                containerAnimation(storage.getInventory(), locationInfo, false);
            }
        });
    }


    public static void openChestInventory(Player player, Inventory inventory) {
        VirtualInventoryHolder holder = (VirtualInventoryHolder) inventory.getHolder();
        if (holder != null) holder.onPlayerRemoteOpened(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
        player.openInventory(inventory);
    }

    public static void closeInventorySound(Player player, Inventory inventory) {
        if (inventory.getLocation() != null)
            player.getWorld().playSound(inventory.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1f);
        else player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1f);
        //player.closeInventory();
    }

    public static boolean hopperMove(Inventory from, int amount, Inventory to) {
        // Search for an item stack to remove
        for (int i = 0; i < from.getContents().length; i++) {
            ItemStack stack = from.getItem(i);
            if (stack != null) {
                // if (hopperMove(from, stack, amount, to)) return true;
                hopperMove(from, stack, amount, to);
                return true;
            }
        }
        return false;
    }

    public static boolean hopperMove(Inventory from, ItemStack stack, int amount, Inventory to) {
        return hopperMove(from, stack, amount, to, true);
    }

    public static boolean hopperMove(Inventory from, ItemStack stack, int amount, Inventory to, boolean triggerEvent) {
        if (stack != null) {
            ItemStack toRemove = stack.clone();
            toRemove.setAmount(Math.min(stack.getAmount(), amount));
            stack.setAmount(stack.getAmount() - toRemove.getAmount());
            if(triggerEvent) {
                InventoryMoveItemEvent event = new InventoryMoveItemEvent(from, toRemove.clone(), to, false);
                Bukkit.getPluginManager().callEvent(event);
                if(event.isCancelled()) {
                    stack.setAmount(stack.getAmount() + toRemove.getAmount());
                    return false;
                }
            }

            HashMap<Integer, ItemStack> leftOvers = to.addItem(toRemove);
            for (ItemStack leftOver : leftOvers.values()) {
                from.addItem(leftOver);
                if (toRemove.equals(leftOver)) return false;
            }
            return true;
        }
        return false;
    }

    public static List<String> getOnlinePlayers() {
        return getPlayersAsNameList(Bukkit.getOnlinePlayers());
    }

    public static List<String> getAllPlayers() {
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            result.add(p.getName());
        }
        return result;
    }

    public static List<String> getPlayersAsNameList(Collection<? extends OfflinePlayer> players) {
        List<String> arr = new ArrayList<>();
        for (OfflinePlayer player : players) {
            arr.add(ChatColor.stripColor(player.getName()));
        }
        return arr;
    }

    public static String prettyPrintPlayers(ChatColor highlight, List<OfflinePlayer> players) {
        String playerString = players.stream().map(OfflinePlayer::getName).collect(Collectors.joining(","));
        return highlight + "[" + ChatColor.WHITE + playerString + highlight + "]";
    }

    /**
     * Gets the BlockFace of the block the player is currently targeting.
     *
     * @param player the player's whos targeted blocks BlockFace is to be checked.
     * @return the BlockFace of the targeted block, or null if the targeted block is non-occluding.
     */
    public static BlockFace getBlockFace(Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 100);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    public static BlockFace getNearestBlockFace(Player player, Location blockPlaced) {
        Vector blockCentre = blockPlaced.add(0.5, 0, 0.5).toVector().setY(0);
        Vector directionLoc = player.getEyeLocation().toVector().setY(0);
        double angle = Math.toDegrees(calculateXZAngle(blockCentre, directionLoc));
        if (angle <= 45 && angle > -45) return BlockFace.EAST;
        if (angle <= 135 && angle > 45) return BlockFace.SOUTH;
        if (angle <= -45 && angle > -135) return BlockFace.NORTH;
        if (angle <= -135 || angle > 135) return BlockFace.WEST;
        return null;
    }

    private static double calculateXZAngle(Vector origin, Vector to) {
        Vector vec = to.subtract(origin);
        return Math.atan2(vec.getZ(), vec.getX());
    }

    public static boolean isSideFace(BlockFace face) {
        if (face == BlockFace.NORTH) return true;
        if (face == BlockFace.EAST) return true;
        if (face == BlockFace.SOUTH) return true;
        return face == BlockFace.WEST;
    }

    public static Inventory copyInventory(Inventory inventory) {
        Inventory tempInv;
        if (inventory.getType() != InventoryType.CHEST) {
            tempInv = Bukkit.createInventory(null, inventory.getType());
        } else {
            tempInv = Bukkit.createInventory(null, inventory.getSize());
        }
        tempInv.setContents(inventory.getContents());
        return tempInv;
    }

    public static List<Inventory> copyInventoryList(List<Inventory> inventories) {
        List<Inventory> tempInvs = new ArrayList<>();
        for (Inventory inv : inventories) {
            Inventory tempInv = Utils.copyInventory(inv);
            tempInvs.add(tempInv);
        }
        return tempInvs;
    }

    public static ItemStack[] createAirList(int size) {
        ItemStack[] itemStacks = new ItemStack[size];
        ItemStack AIR = new ItemStack(Material.AIR);
        for (int i = 0; i < size; i++) {
            itemStacks[i] = AIR;
        }
        return itemStacks;
    }

    public static List<String> filterList(List<String> list, String phrase) {
        return list.stream().filter(s -> s.contains(phrase)).collect(Collectors.toList());
    }

    public static <T> void addIfNotNull(List<T> list, T value) {
        if (value != null) list.add(value);
    }

    public static boolean isAir(Block block) {
        return (block.getType() == Material.AIR) || (block.getType() == Material.CAVE_AIR);
    }

    public static boolean isLocationChunkLoaded(Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return location.getWorld() != null && location.getWorld().isChunkLoaded(chunkX, chunkZ);
    }

    public static boolean isLocationInChunk(Location location, Chunk chunk) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return (chunkX == chunk.getX()) && (chunkZ == chunk.getZ());
    }

    public static Collection<Entity> getPlayersInViewDistance(Location location) {
        if (location.getWorld() == null) return null;
        return location.getWorld().getNearbyEntities(location, Bukkit.getViewDistance() * 16, 256, Bukkit.getViewDistance() * 16, entity -> entity instanceof Player);
    }

    public static boolean isLocationInViewDistance(Player player, Location location) {
        if (location == null) return false;
        if (!player.getWorld().equals(location.getWorld())) return false;
        Location delta = player.getLocation().subtract(location);
        return (delta.getX() <= Bukkit.getViewDistance() * 16) && (delta.getZ() <= Bukkit.getViewDistance() * 16);
    }

    public static boolean isBlacklistedWorld(World world) {
        return PluginConfig.WORLD_BLACKLIST.get().contains(world.getName());
    }

    public static void copyFromResources(File jarFile, String directory) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(directory + "/") || entry.isDirectory()) {
                    continue;
                }

                ChestsPlusPlus.PLUGIN.saveResource(name, true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<OfflinePlayer> getOnlinePlayersNotInList(List<OfflinePlayer> players) {
        List<OfflinePlayer> list = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!players.contains(onlinePlayer)){
                list.add(onlinePlayer);
            }
        }
        return list;
    }

    public static List<ItemStack> getItemsFromRecipeChoice(RecipeChoice recipeChoice) {
        if (recipeChoice instanceof RecipeChoice.MaterialChoice materialChoice) {
            return materialChoice.getChoices().stream().map(ItemStack::new).toList();
        }
        else if (recipeChoice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices();
        }
        else {
            return List.of(recipeChoice.getItemStack());
        }

    }
}
