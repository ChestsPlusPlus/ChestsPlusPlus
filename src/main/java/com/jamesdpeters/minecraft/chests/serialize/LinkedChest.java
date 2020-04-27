package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.*;

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
        validate();
    }

    private void validate(){
        chests.forEach((s, invMap) -> {
            invMap.values().removeIf(Objects::isNull);
        });
    }

    public LinkedChest(){
        chests = new HashMap<>();
    }

}
