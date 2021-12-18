package com.jamesdpeters.minecraft.chests.storage.abstracts;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.Values;
import com.jamesdpeters.minecraft.chests.party.PlayerPartyStorage;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class AbstractStorage implements ConfigurationSerializable {

    //Serializables
    private ArrayList<String> members;
    private List<OfflinePlayer> bukkitMembers;
    private final OfflinePlayer player;
    private final UUID playerUUID;
    private boolean isPublic;
    private List<LocationInfo> locationInfoList;
    private Inventory inventory;

    private int signUpdateTask;

    public AbstractStorage(OfflinePlayer player, String identifier, Location location, Location signLocation) {
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.isPublic = false;
        setIdentifier(identifier);
        LocationInfo locationInfo = new LocationInfo(location);
        locationInfo.setSignLocation(signLocation);
        locationInfoList = new ArrayList<>(Collections.singleton(locationInfo));
        inventory = initInventory();
        init();
    }


    /**
     * This constructor MUST be in the subclass in order for deserialization to work!
     *
     * @param map
     */
    @SuppressWarnings("unchecked")
    public AbstractStorage(Map<String, Object> map) {
        //This reformats the previous method of location storage to the newer version.
        List<Location> locations = (ArrayList<Location>) map.get("locations");
        if (locations != null) {
            locations.removeAll(Collections.singletonList(null));
            locationInfoList = LocationInfo.convert(locations);
        } else {
            locationInfoList = (List<LocationInfo>) map.get("locationInfo");
            locationInfoList.removeAll(Collections.singletonList(null));
            locationInfoList.removeIf(locationInfo -> locationInfo.getLocation() == null);
            locationInfoList = locationInfoList.stream().distinct().collect(Collectors.toList());
        }

        //Read owners UUID and find the player for that ID.
        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        player = Bukkit.getOfflinePlayer(playerUUID);

        //Read publicity data
        if (map.containsKey("isPublic")) isPublic = (boolean) map.get("isPublic");
        else isPublic = false;

        //Read members for this storage
        if (map.get("members") != null) {
            members = (ArrayList<String>) map.get("members");
            bukkitMembers = new ArrayList<>();
            for (String uuid : members) {
                bukkitMembers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
            }
        }

        //Pass map through
        deserialize(map);

        inventory = initInventory();
        if (storeInventory()) {
            ItemStack[] itemStacks = ((ArrayList<ItemStack>) map.get("inventory")).toArray(new ItemStack[0]);
            inventory.setContents(itemStacks);
        }

    }

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        //Add default parameters
        if (storeInventory()) map.put("inventory", inventory.getContents());
        map.put("locationInfo", locationInfoList);
        map.put("playerUUID", player.getUniqueId().toString());
        map.put("members", members);
        map.put("isPublic", isPublic);
        //Add custom parameters
        serialize(map);
        return map;
    }

    private void init() {
        if (shouldDisplayArmourStands()) {
            startSignChangeTask();
        } else {
            for (LocationInfo locationInfo : locationInfoList) {
                if (locationInfo.getSignLocation() != null)
                    locationInfo.getSignLocation().getBlock().getState().update();
                if (locationInfo.getBlockStand() != null) locationInfo.getBlockStand().remove();
                if (locationInfo.getToolItemStand() != null) locationInfo.getToolItemStand().remove();
            }
        }
    }

    private int startSignChangeTask() {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(ChestsPlusPlus.PLUGIN, this::updateSign, 1, 5);
    }

    private void updateSign() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            List<LocationInfo> locationInfos = locationInfoList.stream().filter(locationInfo -> locationInfo.isInWorld(player)).collect(Collectors.toList()); // Create a utility method for this
            locationInfos.forEach(locationInfo -> {
                if (Utils.isLocationInViewDistance(player, locationInfo.getSignLocation()) && Utils.isLocationChunkLoaded(locationInfo.getSignLocation())) {
                    if (displayItem != null) player.sendBlockChange(locationInfo.getSignLocation(), air);
                }
            });
        });
    }

    /**
     * This is called after the config has loaded into memory.
     */
    public void postConfigLoad() {
        for (LocationInfo locationInfo : locationInfoList) {
            locationInfo.setSignLocation(getSignLocation(locationInfo.getLocation()));
        }

        init();
    }

    public abstract StorageType<?> getStorageType();

    /**
     * @return true if this storage should store the inventory to disk.
     */
    public abstract boolean storeInventory();

    /**
     * Add custom parameters here to be serialized.
     *
     * @param map - the map to be added to.
     */
    protected abstract void serialize(Map<String, Object> map);


    /**
     * Use this to deserialize custom parameters.
     *
     * @param map - the map to deserialize from.
     */
    protected abstract void deserialize(Map<String, Object> map);

    protected abstract Inventory initInventory();

    protected abstract void setIdentifier(String newIdentifier);

    public abstract String getIdentifier();

    public abstract boolean shouldDisplayArmourStands();

    /**
     * @return whether to drop the inventory of this storage when it's removed.
     */
    public abstract boolean doesDropInventory();

    /**
     * This is the distance from a full block to the size of the storage block. (e.g Chest is smaller than a regular block.)
     *
     * @return
     */
    public abstract double getBlockOffset(Block block);

    /**
     * This is called when a block is added to the storage system.
     *
     * @param block  - the block that was added.
     * @param player - the player who added the storage.
     */
    public abstract void onStorageAdded(Block block, Player player);

    /**
     * @return List of locations
     * *THIS MUST NOT BE OPERATED ON SINCE ARMOR STANDS DEPEND ON THE PROPER REMOVAL OF LOCATIONS.
     */
    public List<LocationInfo> getLocations() {
        return locationInfoList;
    }

    /**
     * Adds a location to this storage.
     *
     * @param location - location to be added.
     */
    public void addLocation(Location location, Location signLocation) {
        LocationInfo locationInfo = getLocationInfo(location);

        if (locationInfo == null){
            locationInfo = new LocationInfo(location);
            locationInfoList.add(locationInfo);
        }
        locationInfo.setSignLocation(signLocation);

        if (shouldDisplayArmourStands()) {
            if (displayItem != null) {
                updateSign();
                updateClient(locationInfo);
            }
        }
    }

    /**
     * Removes a location from this storage and removes any @{@link ArmorStand} associated with that location
     *
     * @param location - location to be removed.
     */
    public void removeLocation(Location location) {
        //Remove armor stands from the world.
        locationInfoList.stream().filter(locationInfo -> locationInfo.getLocation().equals(location)).forEach(locationInfo -> {
            ArmorStand blockStand = locationInfo.getBlockStand();
            if (blockStand != null) blockStand.remove();
            ArmorStand itemStand = locationInfo.getItemStand();
            if (itemStand != null) itemStand.remove();
            ArmorStand toolStand = locationInfo.getToolItemStand();
            if (toolStand != null) toolStand.remove();
        });
        //Remove this location from storage.
        locationInfoList.removeIf(locationInfo -> locationInfo.getLocation().equals(location));
    }

    /**
     * @param location - location being checked
     * @return true if this storage contains this location
     */
    public boolean containsLocation(Location location) {
        return locationInfoList.stream().filter(locationInfo -> locationInfo.getLocation() != null).anyMatch(locationInfo -> locationInfo.getLocation().equals(location));
    }

    public LocationInfo getLocationInfo(Location location) {
        return locationInfoList.stream().filter(locationInfo -> locationInfo.getLocation().equals(location)).findFirst().orElse(null);
    }

    public int getLocationsSize() {
        return locationInfoList.size();
    }

    public void rename(String newName) {
        setIdentifier(newName);
        if (storeInventory()) {
            ItemStack[] items = getInventory().getContents();
            inventory = initInventory();
            inventory.setContents(items);
        } else {
            inventory = initInventory();
        }

        getLocations().forEach(location -> {
            Block block = location.getLocation().getBlock();
            BlockFace face = getStorageType().getStorageFacing(block);
            if (face != null) {
                Block signBlock = block.getRelative(face);
                if (signBlock.getState() instanceof Sign sign) {
                    sign.setLine(1, ChatColor.GREEN + ChatColor.stripColor("[" + newName + "]"));
                    sign.update();
                }
            }
        });
    }

    /**
     * Drops the contents of the storage at the provided location.
     *
     * @param location - location to drop.
     */
    public void dropInventory(Location location) {
        if (doesDropInventory()) {
            for (ItemStack item : getInventory().getContents()) {
                if (location.getWorld() != null) {
                    if (item != null) {
                        location.getWorld().dropItemNaturally(location, item);
                        getInventory().remove(item);
                    }
                }
            }
        }
    }

    public Location getSignLocation(Location storageBlock) {
        if (storageBlock == null) return null;
        World world = storageBlock.getWorld();
        Block block = storageBlock.getBlock();

        if (world != null) {
            BlockFace facing = getStorageType().getStorageFacing(block);

            if (facing != null) {
                Block sign = block.getRelative(facing);
                return sign.getLocation();
            }
        }
        return null;
    }

    /* MEMBER METHODS */

    /**
     * Checks if the given @{@link Player} has permission to access this storage
     *
     * @param player - the player being checked
     * @return true if player has permission.
     */
    public boolean hasPermission(Player player) {
        if (player.hasPermission(Permissions.OPEN_ANY)) return true;
        return hasPermission((OfflinePlayer) player);
    }

    /**
     * Checks if the given @{@link OfflinePlayer} has permission to access this storage
     *
     * @param player - the player being checked
     * @return true if player has permission.
     */
    public boolean hasPermission(OfflinePlayer player) {
        if (isPublic) return true;
        if (player.getUniqueId().equals(playerUUID)) return true;
        if (PlayerPartyStorage.doPlayersShareParty(getOwner(), player)) return true;
        if (members != null) {
            for (String uuid : members) {
                if (player.getUniqueId().toString().equals(uuid)) return true;
            }
        }
        return false;
    }

    /**
     * Add a @{@link Player} to this storage.
     * This will return false if the player is null or the player was already present.
     *
     * @param player - the player being added.
     * @return true if the player was added
     */
    public boolean addMember(OfflinePlayer player) {
        if (player != null) {
            if (members == null) members = new ArrayList<>();
            if (bukkitMembers == null) bukkitMembers = new ArrayList<>();
            String uuid = player.getUniqueId().toString();
            if (members.contains(uuid)) return false;
            members.add(uuid);
            bukkitMembers.add(player);
            return true;
        }
        return false;
    }

    /**
     * Remove a @{@link Player} from this storage.
     *
     * @param player - player being removed.
     * @return true if player was removed.
     */
    public boolean removeMember(OfflinePlayer player) {
        if (player != null) {
            if (bukkitMembers != null) bukkitMembers.remove(player);
            if (members != null) {
                return members.remove(player.getUniqueId().toString());
            }
        }
        return false;
    }

    /* ARMOR STAND METHODS */

    private ItemStack displayItem;
    private DISPLAY_TYPE displayType;

    private void resetSign() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            List<LocationInfo> locationInfos = locationInfoList.stream().filter(locationInfo -> locationInfo.isInWorld(player)).collect(Collectors.toList()); // Create a utility method for this
            locationInfos.forEach(locationInfo -> {
                if (Utils.isLocationInViewDistance(player, locationInfo.getSignLocation())) {
                    if (locationInfo.getSignLocation().getBlock().getState() instanceof Sign sign) {
                        player.sendBlockChange(locationInfo.getSignLocation(), sign.getBlockData());
                        player.sendSignChange(locationInfo.getSignLocation(), sign.getLines());
                    }
                }
            });
        });
    }

    public void onItemDisplayUpdate(ItemStack newItem) {
        if (shouldDisplayArmourStands()) {
            if (newItem == null || newItem.getType().equals(Material.AIR)) {
                if (displayItem != null) resetSign();
                Bukkit.getScheduler().cancelTask(signUpdateTask);
                signUpdateTask = -1;
            } else {
                if (signUpdateTask == -1) signUpdateTask = startSignChangeTask();
            }
        }
        displayItem = newItem;
        displayType = DISPLAY_TYPE.getType(displayItem);
        if (shouldDisplayArmourStands()) updateClients();
    }

    private final EulerAngle BLOCK_POSE = new EulerAngle(Math.toRadians(-15), Math.toRadians(-45), Math.toRadians(0));
    private final EulerAngle STANDARD_ITEM_POSE = new EulerAngle(Math.toRadians(90), 0, Math.toRadians(180));
    private final EulerAngle TOOL_ITEM_POSE = new EulerAngle(Math.toRadians(-145), 0, Math.toRadians(0));

    /**
     * Updates nearby clients for all locations of this storage:
     * - If getArmorStandItem() is non-null the block in-front of the storage is set to Air and an @{@link ArmorStand} is
     * spawned that displays the item.
     */
    private void updateClients() {
        if (locationInfoList == null) return;
        for (LocationInfo location : locationInfoList) {
            updateClient(location);
        }
    }

    private final BlockData air = Material.AIR.createBlockData();

    enum DISPLAY_TYPE {
        IGNORE,
        TOOL,
        BLOCK,
        ITEM;

        public static DISPLAY_TYPE getType(ItemStack itemStack) {
            if (itemStack == null) return IGNORE;
            if (ApiSpecific.getMaterialChecker().isIgnored(itemStack)) return IGNORE;
            if (ApiSpecific.getMaterialChecker().isTool(itemStack)) return TOOL;
            if (ApiSpecific.getMaterialChecker().isGraphically2D(itemStack)) return ITEM;
            else return BLOCK;
        }
    }

    public void updateClient(LocationInfo location) {
        if (location.getLocation() == null || !Utils.isLocationChunkLoaded(location.getLocation())) return;
        World world = location.getLocation().getWorld();

        if (world != null) {
            if (location.getSignLocation() == null) return;
            Block storageBlock = location.getLocation().getBlock();
            Block anchor = location.getSignLocation().getBlock();

            if (displayItem != null && displayType != DISPLAY_TYPE.IGNORE) {
                boolean isBlock = displayType == DISPLAY_TYPE.BLOCK;
                boolean isTool = displayType == DISPLAY_TYPE.TOOL;

                //Get currently stored armorStand if there isn't one spawn it.
                ArmorStand stand = isTool ? location.getToolItemStand() : (isBlock ? location.getBlockStand() : location.getItemStand());
                if (stand == null || !stand.isValid()) {
                    BlockFace facing;
                    if (anchor.getBlockData() instanceof Directional) {
                        facing = ((Directional) anchor.getBlockData()).getFacing();
                    } else return;
                    Location standLoc = isTool ? getHeldItemArmorStandLoc(storageBlock, anchor, facing) : getArmorStandLoc(storageBlock, anchor, facing, isBlock);
                    stand = createArmorStand(world, standLoc, isBlock, isTool);
                    addArmorStand(isBlock, isTool, location, stand);
                }

                stand.setItemInHand(displayItem);

                //Set on fire to correct lighting.
                stand.setFireTicks(Integer.MAX_VALUE);

                //Set other armor stand helmet to null.
                if (isBlock) {
                    removeArmorStandItem(location.getToolItemStand());
                    removeArmorStandItem(location.getItemStand());
                } else {
                    removeArmorStandItem(location.getBlockStand());
                    if (isTool) removeArmorStandItem(location.getItemStand());
                    else removeArmorStandItem(location.getToolItemStand());
                }
            } else {
                anchor.getState().update();
                removeArmorStandItem(location.getToolItemStand());
                removeArmorStandItem(location.getItemStand());
                removeArmorStandItem(location.getBlockStand());
            }
        }

    }

    /**
     * Creates an empty @{@link ArmorStand} with properties to make it invisible, invulnerable etc.
     *
     * @param world    - the world to spawn in.
     * @param standLoc - location to spawn the @{@link ArmorStand} at.
     * @return instance of @{@link ArmorStand} that was spawned.
     */
    private ArmorStand createArmorStand(World world, Location standLoc, boolean isBlock, boolean isTool) {
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setSmall(true);
        stand.setCanPickupItems(false);
        EulerAngle angle = isTool ? TOOL_ITEM_POSE : (isBlock ? BLOCK_POSE : STANDARD_ITEM_POSE);
        stand.setRightArmPose(angle);

        //Store value of 1 in armour stand to indicate it belongs to this plugin.
        stand.getPersistentDataContainer().set(Values.Instance().PluginKey, PersistentDataType.INTEGER, 1);
        return stand;
    }

    /**
     * Gets the location of an @{@link ArmorStand} based on the Block, BlockFace and if it's a Block/Item.
     *
     * @param anchor  - anchor block to base @{@link ArmorStand} location from.
     * @param facing  - BlockFace the stand should be placed on.
     * @param isBlock - true if the @{@link ItemStack} is a Block / false if an Item.
     * @return the calculated location for the @{@link ArmorStand}
     */
    private Location getArmorStandLoc(Block storageBlock, Block anchor, BlockFace facing, boolean isBlock) {
        double directionFactor = isBlock ? 0.65 : 0.275;
        double perpendicularFactor = isBlock ? 0.025 : 0.125;
        double y = isBlock ? -0.3 : 0.1;
        float yaw = 180;
        return getArmorStandLoc(storageBlock, anchor, facing, directionFactor, perpendicularFactor, y, yaw);
    }


    private Location getHeldItemArmorStandLoc(Block storageBlock, Block anchor, BlockFace facing) {
        double directionFactor = 0.36;
        double perpendicularFactor = 0;
        double y = 0.275;
        float yaw = -90;
        return getArmorStandLoc(storageBlock, anchor, facing, directionFactor, perpendicularFactor, y, yaw);
    }

    private Location getArmorStandLoc(Block storageBlock, Block anchor, BlockFace facing, double directionFactor, double perpendicularFactor, double y, float yaw) {
        //Get centre of block location.
        Location standLoc = anchor.getLocation().add(0.5, -0.5, 0.5);
        Vector direction = facing.getDirection();

        directionFactor = directionFactor + getBlockOffset(storageBlock);
        double x = directionFactor * direction.getX() - perpendicularFactor * direction.getZ();
        double z = directionFactor * direction.getZ() + perpendicularFactor * direction.getX();

        standLoc.setYaw(getYaw(direction.getX(), direction.getZ()) + yaw);
        return standLoc.subtract(x, y, z);
    }

    private void removeArmorStandItem(ArmorStand stand) {
        if (stand != null) stand.setItemInHand(null);
    }

    private void addArmorStand(boolean isBlock, boolean isTool, LocationInfo location, ArmorStand stand) {
        if (isTool) location.setToolItemStand(stand);
        else if (isBlock) location.setBlockStand(stand);
        else location.setItemStand(stand);
    }

    /**
     * Get yaw based upon the direction of the x and y components of the Chest BlockFace
     * Uses precalculated values for most orientations.
     *
     * @param x component
     * @param y component
     * @return yaw
     */
    private float getYaw(double x, double y) {
        if (x == 0 && y == -1) return 0;
        if (x == 1 && y == 0) return 90;
        if (x == 0 && y == 1) return 180;
        if (x == -1 && y == 0) return 270;

        return (float) (Math.asin(y / Math.sqrt(y * y + x * x)) + 90);
    }


    /* GETTERS */

    public List<OfflinePlayer> getMembers() {
        return bukkitMembers;
    }

    public OfflinePlayer getOwner() {
        return player;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /* SETTERS */

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public AbstractStorage setInventory(Inventory inventory) {
        this.inventory = inventory;
        return this;
    }
}
