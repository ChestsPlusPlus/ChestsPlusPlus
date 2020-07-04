package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SerializableAs("LocationInfo")
public class LocationInfo implements ConfigurationSerializable {

    private Location location;
    private ArmorStand itemStand, blockStand, toolItemStand;

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
}
