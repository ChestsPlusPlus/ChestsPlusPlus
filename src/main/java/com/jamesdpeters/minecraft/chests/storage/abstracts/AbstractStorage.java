package com.jamesdpeters.minecraft.chests.storage.abstracts;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.misc.Values;
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
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractStorage implements ConfigurationSerializable {

    //Serializables
    private ArrayList<String> members;
    private List<OfflinePlayer> bukkitMembers;
    private OfflinePlayer player;
    private UUID playerUUID;
    private boolean isPublic;
    private List<LocationInfo> locationInfoList;
    private Inventory inventory;

    public AbstractStorage(OfflinePlayer player, String identifier, Location location){
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.isPublic = false;
        setIdentifier(identifier);
        LocationInfo locationInfo = new LocationInfo(location);
        locationInfoList = new ArrayList<>(Collections.singleton(locationInfo));
        inventory = initInventory();
        init();
    }


    /**
     * This constructor MUST be in the subclass in order for deserialization to work!
     * @param map
     */
    @SuppressWarnings("unchecked")
    public AbstractStorage(Map<String, Object> map){
        //Pass map through
        deserialize(map);

        //This reformats the previous method of location storage to the newer version.
        List<Location> locations = (ArrayList<Location>) map.get("locations");
        if(locations != null) {
            locations.removeAll(Collections.singletonList(null));
            locationInfoList = LocationInfo.convert(locations);
        } else {
            locationInfoList = (List<LocationInfo>) map.get("locationInfo");
            locationInfoList.removeAll(Collections.singletonList(null));
        }

        //Read owners UUID and find the player for that ID.
        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        player = Bukkit.getOfflinePlayer(playerUUID);

        //Read publicity data
        if(map.containsKey("isPublic")) isPublic = (boolean) map.get("isPublic");
        else isPublic = false;

        //Read members for this storage
        if(map.get("members") != null){
            members = (ArrayList<String>) map.get("members");
            bukkitMembers = new ArrayList<>();
            for(String uuid : members){
                bukkitMembers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
            }
        }

        inventory = initInventory();
        if(storeInventory()) {
            ItemStack[] itemStacks = ((ArrayList<ItemStack>) map.get("inventory")).toArray(new ItemStack[0]);
            inventory.setContents(itemStacks);
        }

        init();
    }

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new LinkedHashMap<>();
        //Add default parameters
        if(storeInventory()) map.put("inventory", inventory.getContents());
        map.put("locationInfo", locationInfoList);
        map.put("playerUUID", player.getUniqueId().toString());
        map.put("members", members);
        map.put("isPublic", isPublic);
        //Add custom parameters
        serialize(map);
        return map;
    }

    private void init(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(ChestsPlusPlus.PLUGIN, this::updateClients, 1, 5);
    }

    public abstract StorageType getStorageType();

    /**
     * @return true if this storage should store the inventory to disk.
     */
    public abstract boolean storeInventory();

    /**
     * Add custom parameters here to be serialized.
     * @param map - the map to be added to.
     */
    protected abstract void serialize(Map<String, Object> map);


    /**
     * Use this to deserialize custom parameters.
     * @param map - the map to deserialize from.
     */
    protected abstract void deserialize(Map<String, Object> map);
    protected abstract Inventory initInventory();
    protected abstract void setIdentifier(String newIdentifier);
    public abstract String getIdentifier();

    /**
     * This is called when a block is added to the storage system.
     * @param block - the block that was added.
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
     * @param location - location to be added.
     */
    public void addLocation(Location location){
        locationInfoList.add(new LocationInfo(location));
    }

    /**
     * Removes a location from this storage and removes any @{@link ArmorStand} associated with that location
     * @param location - location to be removed.
     */
    public void removeLocation(Location location){
        //Remove armor stands from the world.
        locationInfoList.stream().filter(locationInfo -> locationInfo.getLocation().equals(location)).forEach(locationInfo -> {
            ArmorStand blockStand = locationInfo.getBlockStand();
            if(blockStand != null) blockStand.remove();
            ArmorStand itemStand = locationInfo.getItemStand();
            if(itemStand != null) itemStand.remove();
        });
        //Remove this location from storage.
        locationInfoList.removeIf(locationInfo -> locationInfo.getLocation().equals(location));
    }

    /**
     * @param location - location being checked
     * @return true if this storage contains this location
     */
    public boolean containsLocation(Location location){
        return locationInfoList.stream().anyMatch(locationInfo -> locationInfo.getLocation().equals(location));
    }

    public int getLocationsSize(){
        return locationInfoList.size();
    }

    public void rename(String newName){
        setIdentifier(newName);
        if(storeInventory()){
            ItemStack[] items = getInventory().getContents();
            inventory = initInventory();
            inventory.setContents(items);
        } else {
            inventory = initInventory();
        }

        getLocations().forEach(location -> {
            Block block = location.getLocation().getBlock();
            BlockFace face = getStorageType().getStorageFacing(block);
            if(face != null) {
                Block signBlock = block.getRelative(face);
                if(signBlock.getState() instanceof Sign) {
                    Sign sign = (Sign) signBlock.getState();
                    sign.setLine(1, ChatColor.GREEN + ChatColor.stripColor("[" + newName+ "]"));
                    sign.update();
                }
            }
        });
    }

    /**
     * Drops the contents of the storage at the provided location.
     * @param location - location to drop.
     */
    public void dropInventory(Location location){
        for(ItemStack item : getInventory().getContents()) {
            if(location.getWorld() != null){
                if(item != null) {
                    location.getWorld().dropItemNaturally(location, item);
                    getInventory().remove(item);
                }
            }
        }
    }

    /* MEMBER METHODS */

    /**
     * Checks if the given @{@link Player} has permission to access this storage
     * @param player - the player being checked
     * @return true if player has permission.
     */
    public boolean hasPermission(Player player){
        if(player.hasPermission(Permissions.OPEN_ANY)) return true;
        return hasPermission((OfflinePlayer) player);
    }

    /**
     * Checks if the given @{@link OfflinePlayer} has permission to access this storage
     * @param player - the player being checked
     * @return true if player has permission.
     */
    public boolean hasPermission(OfflinePlayer player){
        if(isPublic) return true;
        if(player.getUniqueId().equals(playerUUID)) return true;
        if(members != null) {
            for (String uuid : members) {
                if (player.getUniqueId().toString().equals(uuid)) return true;
            }
        }
        return false;
    }

    /**
     * Add a @{@link Player} to this storage.
     * This will return false if the player is null or the player was already present.
     * @param player - the player being added.
     * @return true if the player was added
     */
    public boolean addMember(Player player){
        if(player != null){
            if(members == null) members = new ArrayList<>();
            if(bukkitMembers == null) bukkitMembers = new ArrayList<>();
            String uuid = player.getUniqueId().toString();
            if(members.contains(uuid)) return false;
            members.add(uuid);
            bukkitMembers.add(player);
            return true;
        }
        return false;
    }

    /**
     * Remove a @{@link Player} from this storage.
     * @param player - player being removed.
     * @return true if player was removed.
     */
    public boolean removeMember(Player player){
        if(player != null){
            if(bukkitMembers != null) bukkitMembers.remove(player);
            if(members != null){
                return members.remove(player.getUniqueId().toString());
            }
        }
        return false;
    }

    /* ARMOR STAND METHODS */

    /**
     * @return the @{@link ItemStack} an @{@link ArmorStand} should be holding.
     */
    protected abstract ItemStack getArmorStandItem();

    /**
     * Updates nearby clients for all locations of this storage:
     * - If getArmorStandItem() is non-null the block in-front of the storage is set to Air and an @{@link ArmorStand} is
     *   spawned that displays the item.
     */
    private void updateClients(){
        for (LocationInfo location : locationInfoList) {
            World world = location.getLocation().getWorld();
            Block block = location.getLocation().getBlock();
            BlockData air = Material.AIR.createBlockData();

            if(world != null) {
                Collection<Entity> players = world.getNearbyEntities(location.getLocation(), 20, 20, 20, entity -> entity instanceof Player);

                players.forEach(entity -> {
                    if(entity instanceof Player){
                        Player player = (Player) entity;
                        BlockFace facing = getStorageType().getStorageFacing(block);

                        if(facing != null) {

                            Block anchor = block.getRelative(facing);

                            ItemStack displayItem = getArmorStandItem();

                            if(displayItem != null) {
                                boolean isBlock = Utils.isGraphicallyBlock(displayItem);

                                Location standLoc = getArmorStandLoc(anchor,facing, isBlock);

                                //Make client think sign is invisible.
                                player.sendBlockChange(anchor.getLocation(), air);

                                //Get currently stored armorStand if there isn't one spawn it.
                                ArmorStand stand = isBlock ? location.getBlockStand() : location.getItemStand();
                                if(stand == null || !stand.isValid()){
                                    stand = createArmorStand(world,standLoc,isBlock);
                                    addArmorStand(isBlock, location, stand);
                                }

                                stand.setItemInHand(displayItem);

                                //Set on fire to correct lighting.
                                stand.setFireTicks(Integer.MAX_VALUE);

                                //Set other armor stand helmet to null.
                                setArmorStandHelmet(!isBlock, location, null);

                            } else {
                                anchor.getState().update();
                                setArmorStandHelmet(true,location,null);
                                setArmorStandHelmet(false,location,null);
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * Creates an empty @{@link ArmorStand} with properties to make it invisible, invulnerable etc.
     * @param world - the world to spawn in.
     * @param standLoc - location to spawn the @{@link ArmorStand} at.
     * @return instance of @{@link ArmorStand} that was spawned.
     */
    private ArmorStand createArmorStand(World world, Location standLoc, boolean isBlock){
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setSmall(true);
        stand.setCanPickupItems(false);
        EulerAngle angle = isBlock ? new EulerAngle( Math.toRadians( -15 ), Math.toRadians( -45 ), Math.toRadians(0) ) : new EulerAngle(Math.toRadians(90),0,Math.toRadians(180));
        stand.setRightArmPose(angle);

        //Store value of 1 in armour stand to indicate it belongs to this plugin.
        stand.getPersistentDataContainer().set(Values.PluginKey, PersistentDataType.INTEGER, 1);
        return stand;
    }

    /**
     * Gets the location of an @{@link ArmorStand} based on the Block, BlockFace and if it's a Block/Item.
     * @param anchor - anchor block to base @{@link ArmorStand} location from.
     * @param facing - BlockFace the stand should be placed on.
     * @param isBlock - true if the @{@link ItemStack} is a Block / false if an Item.
     * @return the calculated location for the @{@link ArmorStand}
     */
    private Location getArmorStandLoc(Block anchor, BlockFace facing, boolean isBlock){
//        double directionFactor = isBlock ? 0.6 : 0.3;
        double directionFactor = isBlock ? 0.65 : 0.275;
        double perpendicularFactor = isBlock ? 0.025 : 0.125;
//        double y = -0.4;
        double y = isBlock ? -0.3 : 0.1;
        //Get centre of block location.
        Location standLoc = anchor.getLocation().add(0.5,-0.5,0.5);
        Vector direction = facing.getDirection();

        double x = directionFactor*direction.getX() - perpendicularFactor*direction.getZ();
        double z = directionFactor*direction.getZ() + perpendicularFactor*direction.getX();

        float yaw = 180;
        standLoc.setYaw(getYaw(direction.getX(),direction.getZ())+yaw);
        return standLoc.subtract(x, y, z);
    }

    private void setArmorStandHelmet(boolean isBlock, LocationInfo location, ItemStack helmet){
        ArmorStand stand = isBlock ? location.getBlockStand() : location.getItemStand();
        if(stand != null) stand.setItemInHand(helmet);
    }

    private void addArmorStand(boolean isBlock, LocationInfo location, ArmorStand stand){
        if(isBlock) location.setBlockStand(stand);
        else location.setItemStand(stand);
    }

    /**
     * Get yaw based upon the direction of the x and y components of the Chest BlockFace
     * Uses precalculated values for most orientations.
     * @param x component
     * @param y component
     * @return yaw
     */
    private float getYaw(double x, double y){
        if(x == 0 && y == -1) return 0;
        if(x == 1 && y == 0) return 90;
        if(x == 0 && y == 1) return 180;
        if(x == -1 && y == 0) return 270;

        return (float) (Math.asin(y/Math.sqrt(y*y+x*x))+90);
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
