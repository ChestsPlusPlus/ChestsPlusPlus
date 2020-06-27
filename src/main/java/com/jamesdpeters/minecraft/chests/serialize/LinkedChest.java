package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.*;

@SerializableAs("LinkedChest")
public class LinkedChest implements ConfigurationSerializable {

    public HashMap<String, HashMap<String, InventoryStorage>> chests;
    public HashMap<String, HashMap<String, AutoCraftingStorage>> autocraftingtables;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("chests",chests);
        hashMap.put("autocraftingtables",autocraftingtables);
        return hashMap;
    }

    @SuppressWarnings("unchecked")
    public LinkedChest(Map<String, Object> map){
        chests = (HashMap<String, HashMap<String, InventoryStorage>>) map.get("chests");
        if(chests == null) chests = new HashMap<>();
        autocraftingtables = (HashMap<String, HashMap<String, AutoCraftingStorage>>) map.get("autocraftingtables");
        if(autocraftingtables == null) autocraftingtables = new HashMap<>();
        validate();
    }

    private void validate(){
        if(chests != null) chests.forEach((s, invMap) -> invMap.values().removeIf(Objects::isNull));
        if(autocraftingtables != null) autocraftingtables.forEach((s, craftMap) -> craftMap.values().removeIf(Objects::isNull));
    }

    public LinkedChest(){
        chests = new HashMap<>();
        autocraftingtables = new HashMap<>();
    }

}
