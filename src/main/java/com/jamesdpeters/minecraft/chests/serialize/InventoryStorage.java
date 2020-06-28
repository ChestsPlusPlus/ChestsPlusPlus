package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.inventories.ChestLinkMenu;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.runnables.VirtualChestToHopper;
import com.jamesdpeters.minecraft.chests.sort.InventorySorter;
import com.jamesdpeters.minecraft.chests.sort.SortMethod;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InventoryStorage implements ConfigurationSerializable {

    private Inventory inventory; //Old Inventory
    //    ArrayList<Location> locationsList;
    private ArrayList<String> members; //Members UUID
    private List<OfflinePlayer> bukkitMembers;
    private String inventoryName = "Chest";
    private VirtualChestToHopper chestToHopper;
    private OfflinePlayer player;
    private UUID playerUUID;
    private boolean isPublic;
    private SortMethod sortMethod;

    private List<LocationInfo> locationInfoList;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("inventory",inventory.getContents());
        hashMap.put("locationInfo",locationInfoList);
        hashMap.put("inventoryName",inventoryName);
        hashMap.put("playerUUID",playerUUID.toString());
        hashMap.put("members", members);
        hashMap.put("isPublic", isPublic);
        hashMap.put("sortMethod", sortMethod.toString());
        return hashMap;
    }

    @SuppressWarnings("unchecked")
    public InventoryStorage(Map<String, Object> map){
        String tempName = (String) map.get("inventoryName");
        if(tempName != null) inventoryName = tempName;

        inventory = initInventory();
        ItemStack[] itemStacks = ((ArrayList<ItemStack>) map.get("inventory")).toArray(new ItemStack[0]);

        inventory.setContents(itemStacks);
        List<Location> locations = (ArrayList<Location>) map.get("locations");
        if(locations != null) {
            locations.removeAll(Collections.singletonList(null));
            locationInfoList = LocationInfo.convert(locations);
        } else {
            locationInfoList = (List<LocationInfo>) map.get("locationInfo");
            locationInfoList.removeAll(Collections.singletonList(null));
        }

        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        player = Bukkit.getOfflinePlayer(playerUUID);

        if(map.containsKey("isPublic")) isPublic = (boolean) map.get("isPublic");
        else isPublic = false;

        if(map.containsKey("sortMethod")) sortMethod = Enum.valueOf(SortMethod.class, (String) map.get("sortMethod"));
        else sortMethod = SortMethod.OFF;

        if(map.get("members") != null){
            members = (ArrayList<String>) map.get("members");
            bukkitMembers = new ArrayList<>();
            for(String uuid : members){
                bukkitMembers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
            }
        }

        init();
    }

    public InventoryStorage(OfflinePlayer player, String group, Location location){
        this.inventoryName = group;
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.isPublic = false;
        this.sortMethod = SortMethod.OFF;
        LocationInfo locationInfo = new LocationInfo(location);
        locationInfoList = new ArrayList<>(Collections.singleton(locationInfo));

        Block block = location.getBlock();
        if(block.getState() instanceof Chest){
            Chest chest = (Chest) block.getState();
            inventory = initInventory();
            inventory.setContents(chest.getInventory().getContents());
            chest.getInventory().clear();
        }

        init();
    }

    private void init(){
        chestToHopper = new VirtualChestToHopper(this);
        chestToHopper.start();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(ChestsPlusPlus.PLUGIN, this::updatePlayers, 1, 5);
    }

    private Inventory initInventory(){
        return Bukkit.createInventory(new VirtualInventoryHolder(this), 54,inventoryName);
    }

    public Inventory getInventory() {
        return inventory;
    }

    /**
     * @return List of locations
     * *THIS MUST NOT BE OPERATED ON SINCE ARMOR STANDS DEPEND ON THE PROPER REMOVAL OF LOCATIONS.
     */
    public List<LocationInfo> getLocations() {
        return locationInfoList;
    }

    public void addLocation(Location location){
        locationInfoList.add(new LocationInfo(location));
    }

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

    public boolean containsLocation(Location location){
        return locationInfoList.stream().anyMatch(locationInfo -> locationInfo.getLocation().equals(location));
    }

    public int getLocationsSize(){
        return locationInfoList.size();
    }

    public void dropInventory(Location location){
        for(ItemStack item : inventory.getContents()) {
            if(location.getWorld() != null){
                if(item != null) {
                    location.getWorld().dropItemNaturally(location, item);
                    inventory.remove(item);
                }
            }
        }
    }

    public String getIdentifier() {
        return inventoryName;
    }

    public OfflinePlayer getOwner() {
        return player;
    }

    @Override
    public String toString() {
        return inventoryName+": "+locationInfoList.toString();
    }

    public ItemStack getIventoryIcon(Player player){
        ItemStack mostCommon = InventorySorter.getMostCommonItem(inventory);
        ItemStack toReturn;
        if(mostCommon == null) toReturn = new ItemStack(Material.CHEST);
        else toReturn = mostCommon.clone();

        ItemMeta meta = toReturn.getItemMeta();
        if(meta != null) {
            String dispName = ChatColor.GREEN + "" + getIdentifier() + ": " +ChatColor.WHITE+ ""+getTotalItems()+" items";
            if(player.getUniqueId().equals(playerUUID)) meta.setDisplayName(dispName);
            else meta.setDisplayName(getOwner().getName()+": "+dispName);

            if(getMembers() != null) {
                List<String> memberNames = new ArrayList<>();
                if(isPublic) memberNames.add(ChatColor.WHITE+"Public Chest");
                memberNames.add(ChatColor.BOLD+""+ChatColor.UNDERLINE+"Members:");
                getMembers().forEach(player1 -> memberNames.add(ChatColor.stripColor(player1.getName())));
                meta.setLore(memberNames);
            }
            toReturn.setItemMeta(meta);
        }
        toReturn.setAmount(1);
        return toReturn;
    }

    public ClickableItem getClickableItem(Player player) {
        return ClickableItem.from(getIventoryIcon(player), event -> {
            InventoryHolder inventoryHolder = inventory.getHolder();
            if(inventoryHolder instanceof VirtualInventoryHolder){
                ((VirtualInventoryHolder) inventoryHolder).setPreviousInventory(() -> {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> ChestLinkMenu.getMenu(player).open(player), 1);
                });
            }
            Utils.openChestInventory(player,getInventory());
        });
    }

    public boolean hasPermission(Player player){
        if(player.hasPermission(Permissions.OPEN_ANY)) return true;
        return hasPermission((OfflinePlayer) player);
    }

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

    public boolean removeMember(Player player){
        if(player != null){
            if(bukkitMembers != null) bukkitMembers.remove(player);
            if(members != null){
                members.remove(player.getUniqueId().toString());
                return true;
            }
        }
        return false;
    }

    public void rename(String newIdentifier){
        this.inventoryName = newIdentifier;
        ItemStack[] items = inventory.getContents();
        inventory = initInventory();
        inventory.setContents(items);
        locationInfoList.forEach(location -> {
            Block block = location.getLocation().getBlock();
            if(block.getBlockData() instanceof org.bukkit.block.data.type.Chest) {
                org.bukkit.block.data.type.Chest chest = (org.bukkit.block.data.type.Chest) block.getBlockData();
                BlockFace blockFace = chest.getFacing();
                Block signBlock = block.getRelative(blockFace);
                Sign sign = (Sign) signBlock.getState();
                sign.setLine(1,ChatColor.GREEN + ChatColor.stripColor("[" + newIdentifier + "]"));
                sign.update();
            }
        });
    }

    private void updatePlayers(){
        for (LocationInfo location : locationInfoList) {
            World world = location.getLocation().getWorld();
            Block block = location.getLocation().getBlock();
            BlockData air = Material.AIR.createBlockData();
            if(world != null) {
                Collection<Entity> players = world.getNearbyEntities(location.getLocation(), 20, 20, 20, entity -> entity instanceof Player);
                players.forEach(entity -> {
                    if(entity instanceof Player){
                        Player player = (Player) entity;
                        if(block.getBlockData() instanceof Directional) {
                            Directional chest = (Directional) block.getBlockData();
                            BlockFace facing = chest.getFacing();
                            Block sign = block.getRelative(facing);

                            ItemStack mostCommon = InventorySorter.getMostCommonItem(inventory);

                            if(mostCommon != null) {
                                boolean isBlock = mostCommon.getType().isBlock();

                                Location standLoc = getArmorStandLoc(sign,facing, mostCommon.getType().isBlock());

                                //Make client think sign is invisible.
                                player.sendBlockChange(sign.getLocation(), air);

                                //Get currently stored armorStand if there isn't one spawn it.
                                ArmorStand stand = isBlock ? location.getBlockStand() : location.getItemStand();
                                if(stand == null || !stand.isValid()){
                                    stand = createArmorStand(world,standLoc);
                                    addArmorStand(isBlock, location, stand);
                                }

                                stand.setHelmet(mostCommon);

                                //Set on fire to correct lighting.
                                stand.setFireTicks(Integer.MAX_VALUE);

                                //Set other armor stand helmet to null.
                                setArmorStandHelmet(!isBlock, location, null);

                            } else {
                                sign.getState().update();
                                setArmorStandHelmet(true,location,null);
                                setArmorStandHelmet(false,location,null);
                            }
                        }
                    }
                });
            }
        }
    }

    private ArmorStand createArmorStand(World world, Location standLoc){
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setSmall(true);
        return stand;
    }

    private Location getArmorStandLoc(Block sign, BlockFace facing, boolean isBlock){
//        int yawOffset = isBlock ? 180 : 0;
        double directionFactor = isBlock ? 0.6 : 0.3;
        double y = isBlock ? 0.1 : 0.45;
        //Get centre of block location.
        Location standLoc = sign.getLocation().add(0.5,-0.5,0.5);
        Vector direction = facing.getDirection();
        standLoc.setYaw(getYaw(direction.getX(),direction.getZ())+180);
        return standLoc.subtract(directionFactor*direction.getX(),y, directionFactor*direction.getZ());
    }

    private void setArmorStandHelmet(boolean isBlock, LocationInfo location, ItemStack helmet){
        ArmorStand stand = isBlock ? location.getBlockStand() : location.getItemStand();
        if(stand != null) stand.setHelmet(helmet);
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

    public List<OfflinePlayer> getMembers(){
        return bukkitMembers;
    }

    public int getTotalItems(){
        int total = 0;
        if(inventory != null) {
            for(ItemStack itemStack : inventory.getContents()){
                if(itemStack != null) total += itemStack.getAmount();
            }
        }
        return total;
    }

    public void setPublic(boolean value){
        this.isPublic = value;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setSortMethod(SortMethod sortMethod){
        this.sortMethod = sortMethod;
    }

    public SortMethod getSortMethod(){
        return sortMethod;
    }

    public void sort(){
        InventorySorter.sort(inventory, sortMethod);
    }
}
