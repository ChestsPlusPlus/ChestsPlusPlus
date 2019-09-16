package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SerializableAs("LinkedChest")
public class LinkedChest implements ConfigurationSerializable {

    public HashMap<String, HashMap<String, InventoryStorage>> chests;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("chests",chests);
        return hashMap;
    }

    @SuppressWarnings("unchecked")
    public LinkedChest(Map<String, Object> map){
        chests = (HashMap<String, HashMap<String, InventoryStorage>>) map.get("chests");
    }

    public LinkedChest(){
        chests = new HashMap<>();
    }

}
