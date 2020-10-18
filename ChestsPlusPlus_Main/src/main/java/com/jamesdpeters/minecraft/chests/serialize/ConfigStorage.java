package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.party.PlayerPartyStorage;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@SerializableAs("ConfigStorage")
public class ConfigStorage implements ConfigurationSerializable {

    public HashMap<String, HashMap<String, ChestLinkStorage>> chests;
    public HashMap<String, HashMap<String, AutoCraftingStorage>> autocraftingtables;
    public HashMap<String, PlayerPartyStorage> parties;

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> hashMap = new LinkedHashMap<>();
        hashMap.put("chests", chests);
        hashMap.put("autocraftingtables", autocraftingtables);
        hashMap.put("parties", parties);
        return hashMap;
    }

    @SuppressWarnings("unchecked")
    public ConfigStorage(Map<String, Object> map) {
        //Legacy handling

        chests = (HashMap<String, HashMap<String, ChestLinkStorage>>) map.get("chests");
        if (chests == null) chests = new HashMap<>();

        autocraftingtables = (HashMap<String, HashMap<String, AutoCraftingStorage>>) map.get("autocraftingtables");
        if (autocraftingtables == null) autocraftingtables = new HashMap<>();

        parties = (HashMap<String, PlayerPartyStorage>) map.get("parties");
        if (parties == null) parties = new HashMap<>();

        validate();
    }

    private void validate() {
        if (chests != null)
            chests.forEach((s, invMap) -> invMap.values().removeIf(Objects::isNull));
        if (autocraftingtables != null)
            autocraftingtables.forEach((s, craftMap) -> craftMap.values().removeIf(Objects::isNull));
        if (parties != null)
            parties.values().removeIf(Objects::isNull);
    }

    public ConfigStorage() {
        chests = new HashMap<>();
        autocraftingtables = new HashMap<>();
        parties = new HashMap<>();
    }

}
