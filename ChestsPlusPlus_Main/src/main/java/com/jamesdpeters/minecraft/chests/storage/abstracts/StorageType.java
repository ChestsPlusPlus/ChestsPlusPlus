package com.jamesdpeters.minecraft.chests.storage.abstracts;


import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.PluginConfig;
import com.jamesdpeters.minecraft.chests.Values;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.party.PartyUtils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.storage.StorageUtils;
import lombok.var;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class StorageType<T extends AbstractStorage> implements Listener {

    private final ConfigStorage store;
    private final StorageUtils<T> storageUtils;
    private final HashMap<Location, T> storageCache;

    protected StorageType(ConfigStorage store) {
        this.store = store;
        storageUtils = new StorageUtils<>(this);
        storageCache = new HashMap<>();
    }

    public StorageUtils<T> getStorageUtils() {
        return storageUtils;
    }

    public abstract HashMap<String, HashMap<String, T>> getStorageMap(ConfigStorage store);

    public abstract T createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location, Location signLocation);

    public HashMap<String, HashMap<String, T>> getStorageMap() {
        return getStorageMap(store);
    }

    /**
     * This is the tag used in Signs such as [ChestLink] or [AutoCraft]
     *
     * @return String value of the sign tag.
     */
    public abstract String getSignTag();

    /**
     * This method should check if a block type is a valid type for this Storage.
     * E.g A Chest, or Crafting table etc.
     *
     * @param block - the block being checked
     * @return true if the block is valid.
     */
    public abstract boolean isValidBlockType(Block block);

    /**
     * This gets called when a block is removed from the storage system but is still present in the world.
     *
     * @param block - the block that was removed (Not the sign)
     */
    public abstract void onSignRemoval(Block block);

    public abstract boolean hasPermissionToAdd(Player player);

    public void createStorage(Player player, Block block, String identifier, boolean requireSign) {
        createStorage(player, player, block, identifier, requireSign);
    }

    public abstract void createStorage(Player player, OfflinePlayer owner, Block block, String identifier, boolean requireSign);

    public abstract void createStorageFacing(Player player, OfflinePlayer owner, Block block, String identifier, BlockFace facing, boolean requireSign);

    public void createStorageFacing(Player player, Block block, String identifier, BlockFace facing, boolean requireSign) {
        createStorageFacing(player, player, block, identifier, facing, requireSign);
    }

    public abstract BlockFace onStoragePlacedBlockFace(Player player, Block placed);

    /**
     * @return the direction the storage at the given location is facing.
     */
    public abstract BlockFace getStorageFacing(Block block);

    /**
     * @param block - the block being tested.
     * @return A list of @{@link BlockFace} that are valid to place a sign on this type of storage.
     */
    public abstract List<BlockFace> getValidBlockFaces(Block block);

    /**
     * Used to validate the block type/data when a new storage has been added.
     * For example, converting DoubleChests into a Chest.
     */
    public abstract void validate(Block block);

    public abstract StorageMessages getMessages();

    @EventHandler
    public void playerInteractEvent(PlayerInteractEvent event){
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            onBlockRightClick(event);
        }
    }

    public abstract void onBlockRightClick(PlayerInteractEvent event);

    /*
    STORAGE MAP SECTION
     */

    private HashMap<String, HashMap<String, T>> getMap() {
        return getStorageMap(store);
    }

    public List<T> getStorageMemberOf(Player player) {
        return getMap().entrySet().stream().flatMap(map -> map.getValue().values().stream().filter(storage -> {
            if (PartyUtils.getPlayerPartyStorage(storage.getOwner()).getOwnedPartiesCollection().stream().anyMatch(party -> party.isMember(player))) return true; // Uses party to match.

            if (storage.isPublic()) return false;
            if (storage.getOwner().getUniqueId().equals(player.getUniqueId())) return false;
            if (storage.getMembers() == null) return false;
            return storage.getMembers().stream().anyMatch(p -> p.getUniqueId().equals(player.getUniqueId()));
        })).collect(Collectors.toList());
    }

    public HashMap<String, T> getStorageMap(UUID playerUUID) {
        String id = playerUUID.toString();
        if (getMap().containsKey(id)) {
            return getMap().get(id);
        } else {
            HashMap<String, T> hashMap = new HashMap<>();
            getMap().put(id, hashMap);
            return hashMap;
        }
    }

    public T getStorage(UUID playerUUID, String identifier) {
        HashMap<String, T> map = getStorageMap(playerUUID);
        return map.getOrDefault(identifier, null);
    }

    public T getStorage(Location location) {
        T storage = storageCache.get(location);
        if (storage != null) return storage;
        if (location != null) {
            Block block = location.getBlock();
            if (isValidBlockType(block)) {
                StorageInfo<T> storageInfo = storageUtils.getStorageInfo(location);
                if (storageInfo != null) {
                    storage = storageInfo.getStorage(location);
                    storageCache.put(location, storage);
                    return storage;
                }
            }
        }
        return null;
    }

    public T getStorage(Player member, String playerChestID) {
        if (playerChestID.contains(":")) {
            String[] args = playerChestID.split(":");
            String playerName = args[0];
            String chestlinkID = args[1];
            Optional<T> storage = getStorageMemberOf(member).stream().filter(store -> store.getOwner().getName().equals(playerName) && store.getIdentifier().equals(chestlinkID)).findFirst();
            if (storage.isPresent()) return storage.get();
        }
        return null;
    }

    /*
    ADD/REMOVE UTILS
     */

    public boolean add(Player player, String identifier, Location chestLocation, Location sign, OfflinePlayer owner) {
        //List of groups this player has.
        HashMap<String, T> map = getStorageMap(owner.getUniqueId());

        //Get Storage for the given group or create it if it doesnt exist.
        if (!map.containsKey(identifier)) {
            if (isAtLimit(owner)) {
                //TODO Reformat messages!
                Messages.OWNER_HAS_TOO_MANY_CHESTS(player, owner);
                return false;
            }
            T storage = createNewStorageInstance(owner, identifier, chestLocation, sign);
            map.put(identifier, storage);
        }

        T storage = map.get(identifier);
        storage.onStorageAdded(chestLocation.getBlock(), player);
        storage.addLocation(chestLocation, sign);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1f);
        Config.saveASync();
        return true;
    }

    public boolean isAtLimit(OfflinePlayer player) {
        if (PluginConfig.SHOULD_LIMIT_CHESTS.get()) {
            return getStorageMap(player.getUniqueId()).size() >= PluginConfig.LIMIT_CHESTS_AMOUNT.get();
        }
        return false;
    }

    /**
     * Removes a block from the given storage system.
     *
     * @param storage     - @{@link AbstractStorage}
     * @param location    - the @{@link Location} to remove
     * @param hasPickedUp - true if the player Silk Touched the block.
     * @return
     */
    public T removeBlock(T storage, Location location, boolean hasPickedUp) {
        if (storage != null) {
            storage.removeLocation(location);
            storageCache.remove(location);
            if (storage.getLocationsSize() == 0 && !hasPickedUp) {
                storage.dropInventory(location);
                getStorageMap(storage.getOwner().getUniqueId()).remove(storage.getIdentifier());
            }
            Config.saveASync();
            return storage;
        }
        return null;
    }

    public T removeBlock(OfflinePlayer owner, String identifier, Location chestLocation) {
        return removeBlock(getStorageMap(owner.getUniqueId()).get(identifier), chestLocation, false);
    }

    public T removeBlock(Location chestLocation, boolean hasPickedUp) {
        T storage = getStorage(chestLocation);
        return removeBlock(storage, chestLocation, hasPickedUp);
    }

    public void removeStorage(Player player, String group) {
        T storage = getStorage(player.getUniqueId(), group);
        if (storage != null) {
            var locations = new ArrayList<>(storage.getLocations());
            locations.forEach(location -> {
                if (location != null) {
                    Block block = location.getLocation().getBlock();
                    BlockBreakEvent bbe = new BlockBreakEvent(block, player);
                    Bukkit.getPluginManager().callEvent(bbe);
                    if (!bbe.isCancelled())
                        block.breakNaturally();
                }
            });
            storage.dropInventory(player.getLocation());
            getStorageMap(player.getUniqueId()).remove(group);
            getMessages().removedGroup(player, group);
        } else {
            getMessages().groupDoesntExist(player, group);
        }
        Config.saveASync();
    }

    public int getTotalLocations() {
        AtomicInteger total = new AtomicInteger();
        getMap().forEach((s, map) -> {
            map.forEach((s1, storage) -> {
                if (storage != null) total.getAndIncrement();
            });
        });
        return total.get();
    }

    public boolean renameStorage(Player player, String oldIdentifier, String newIdentifier) {
        HashMap<String, T> map = getStorageMap(player.getUniqueId());
        if (!map.containsKey(oldIdentifier)) {
            Messages.CANNOT_RENAME_GROUP_DOESNT_EXIST(player, oldIdentifier);
            return false;
        }
        if (map.containsKey(newIdentifier)) {
            Messages.CANNOT_RENAME_GROUP_ALREADY_EXISTS(player, newIdentifier);
            return false;
        }
        T storage = map.get(oldIdentifier);
        storage.rename(newIdentifier);
        map.remove(oldIdentifier);
        map.put(newIdentifier, storage);
        //saveASync();
        return true;
    }

    /* HELPER UTILS */

    protected void placeSign(Block placedAgainst, Block toReplace, BlockFace facing, Player player, OfflinePlayer ownerPlayer, String identifier, String linkTag, boolean requireSign) {
        if (Utils.isAir(toReplace)) {
            BlockState replacedBlockState = toReplace.getState();

            Material signMaterial = Material.OAK_WALL_SIGN;
            if (player.getGameMode() != GameMode.CREATIVE && requireSign) {
                if (player.getEquipment() != null) {
                    if (!Tag.SIGNS.isTagged(player.getEquipment().getItemInMainHand().getType())) {
                        Messages.MUST_HOLD_SIGN(player);
                        return;
                    }
                    signMaterial = player.getEquipment().getItemInMainHand().getType();
                    player.getEquipment().getItemInMainHand().setAmount(player.getEquipment().getItemInMainHand().getAmount() - 1);
                } else {
                    Messages.MUST_HOLD_SIGN(player);
                    return;
                }
            }

            String uuid, group, owner = null;
            if (identifier.contains(":")) {
                String[] args = identifier.split(":");
                owner = args[0];
                group = args[1];
                ownerPlayer = Config.getOfflinePlayer(owner);
                if (ownerPlayer != null) {
                    uuid = ownerPlayer.getUniqueId().toString();
                } else {
                    getMessages().invalidID(player);
                    return;
                }
            } else {
                group = identifier;
                uuid = ownerPlayer.getUniqueId().toString();
            }

            String[] lines = new String[4];
            lines[0] = linkTag;
            lines[1] = Values.identifier(group);
            lines[3] = "";
            if (owner != null) lines[2] = owner;
            else lines[2] = "";

            Material airType = toReplace.getType();
            Material wallSign = Material.getMaterial(signMaterial.name().replace("SIGN", "WALL_SIGN"));
            toReplace.setType(wallSign != null ? wallSign : Material.OAK_WALL_SIGN);
            Sign sign = (Sign) toReplace.getState();
            WallSign signBlockData = (WallSign) sign.getBlockData();
            signBlockData.setFacing(facing);
            sign.setBlockData(signBlockData);
            sign.getPersistentDataContainer().set(Values.Instance().playerUUID, PersistentDataType.STRING, uuid);
            sign.update();

            BlockPlaceEvent event = new BlockPlaceEvent(sign.getBlock(), replacedBlockState, placedAgainst, new ItemStack(Material.AIR), player, true, EquipmentSlot.HAND);
            ChestsPlusPlus.PLUGIN.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                sign.setType(airType);
                return;
            }

            SignChangeEvent signChangeEvent = new SignChangeEvent(sign.getBlock(), player, lines);
            ChestsPlusPlus.PLUGIN.getServer().getPluginManager().callEvent(signChangeEvent);
        } else {
            getMessages().invalidSignPlacement(player);
        }
    }

    public List<String> getStorageList(Player player, String searchedArg) {
        return getStorageMap(player.getUniqueId()).values().stream().filter(t -> t.getIdentifier().contains(searchedArg)).map(AbstractStorage::getIdentifier).collect(Collectors.toList());
    }

    public List<String> getOpenableStorageList(Player player, String searchedArg) {
        List<String> playerList = getStorageList(player, searchedArg);
        List<String> memberList = getStorageMemberOf(player).stream().filter(t -> t.getIdentifier().contains(searchedArg)).map(storage -> storage.getOwner().getName() + ":" + storage.getIdentifier()).collect(Collectors.toList());
        playerList.addAll(memberList);
        return playerList;
    }

    public List<LocationInfo> getViewingDistanceStorages(Player player) {
        List<LocationInfo> list = new ArrayList<>();
        getStorageMap(store).values().forEach(map -> map.values().forEach(abstractStorage -> abstractStorage.getLocations().forEach(locationInfo -> {
            if (Utils.isLocationInViewDistance(player, locationInfo.getSignLocation())) {
                list.add(locationInfo);
            }
        })));
        return list;
    }

    public List<LocationInfo> getLocationsInChunk(Chunk chunk) {
        List<LocationInfo> list = new ArrayList<>();
        getStorageMap().values().forEach(map -> map.values().forEach(abstractStorage -> abstractStorage.getLocations().forEach(locationInfo -> {
            if (locationInfo.getSignLocation().getChunk().equals(chunk)) {
                list.add(locationInfo);
            }
        })));
        return list;
    }

    /*
    POST LOAD
     */

    public void onConfigLoad() {
        getMap().values().forEach(stringTHashMap -> stringTHashMap.values().forEach(AbstractStorage::postConfigLoad));
    }

}
