package com.jamesdpeters.minecraft.chests;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

public class VersionMatcher {
    private static final Map<String, String> VERSION_TO_REVISION = new HashMap<>() {
        {
            this.put("1.20", "v1_20_R1");
            this.put("1.20.1", "v1_20_R1");
            this.put("1.20.2", "v1_20_R2");
            this.put("1.20.3", "v1_20_R3");
            this.put("1.20.4", "v1_20_R3");
            this.put("1.20.5", "v1_20_R4");
            this.put("1.20.6", "v1_20_R4");
            this.put("1.21", "v1_21_R1");
        }
    };
    private static final String FALLBACK_REVISION = "v1_20_R1";

    public static String match() {
        String craftBukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
        String rVersion;
        if (!craftBukkitPackage.contains(".v")) {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            rVersion = VERSION_TO_REVISION.getOrDefault(version, FALLBACK_REVISION);
        } else {
            rVersion = craftBukkitPackage.split("\\.")[3];
        }

        return rVersion;
    }
}
