package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.TileEntityOpener;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SerializableAs("LocationInfo")
public class LocationInfo implements ConfigurationSerializable {

    private Location location, signLocation;
    private ArmorStand itemStand, blockStand, toolItemStand;
    private TileEntityOpener tileEntityOpener;

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("Location", location);
        return map;
    }

    public LocationInfo(Map<String, Object> map){
        location = (Location) map.get("Location");
    }

    public LocationInfo(Location location){
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public Location getSignLocation() {
        return signLocation;
    }

    public Sign getSign(){
        if(signLocation.getBlock().getState() instanceof Sign){
            return (Sign) signLocation.getBlock().getState();
        }
        return null;
    }

    public ArmorStand getBlockStand() {
        return blockStand;
    }

    public ArmorStand getItemStand() {
        return itemStand;
    }

    public ArmorStand getToolItemStand() {
        return toolItemStand;
    }

    public void setBlockStand(ArmorStand blockStand) {
        this.blockStand = blockStand;
    }

    public void setItemStand(ArmorStand itemStand) {
        this.itemStand = itemStand;
    }

    public void setToolItemStand(ArmorStand toolItemStand) {
        this.toolItemStand = toolItemStand;
    }

    public void setSignLocation(Location signLocation) {
        this.signLocation = signLocation;
    }

    public void setTileEntityOpener(TileEntityOpener tileEntityOpener) {
        this.tileEntityOpener = tileEntityOpener;
    }

    public TileEntityOpener getTileEntityOpener() {
        return tileEntityOpener;
    }

    public static List<LocationInfo> convert(List<Location> locationList){
        List<LocationInfo> locationInfos = new ArrayList<>();
        for (Location location : locationList) {
            locationInfos.add(new LocationInfo(location));
        }
        return locationInfos;
    }

    public static Optional<LocationInfo> getLocationInfo(List<LocationInfo> locationInfos, Location location){
        return locationInfos.stream().filter(locationInfo -> locationInfo.getLocation().equals(location)).findFirst();
    }

    public boolean isInWorld(Player player){
        return getLocation() != null && player.getWorld().equals(getLocation().getWorld());
    }
}
