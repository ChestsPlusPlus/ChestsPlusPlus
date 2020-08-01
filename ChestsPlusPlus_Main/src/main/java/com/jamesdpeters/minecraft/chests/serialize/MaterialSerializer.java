package com.jamesdpeters.minecraft.chests.serialize;

import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.LinkedHashMap;
import java.util.Map;

@SerializableAs("Material")
public class MaterialSerializer implements ConfigurationSerializable {

    private final Material material;

    public MaterialSerializer(Material material) {
        this.material = material;
    }

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("Material", material.name());
        return map;
    }

    public MaterialSerializer(Map<String, Object> map) {
        String matName = (String) map.get("Material");
        material = Material.getMaterial(matName);
    }

    public Material getMaterial() {
        return material;
    }
}
