package com.jamesdpeters.minecraft.chests.api_interfaces;

import com.jamesdpeters.minecraft.chests.MaterialChecker_1_15;
import com.jamesdpeters.minecraft.chests.MaterialChecker_1_16;
import org.bukkit.Bukkit;

public class APISpecific {

    private static MaterialChecker materialChecker;
    private static Version version;

    static {
        version = getVersion();
    }

    enum Version {
        API_1_16,
        API_1_15,
        API_1_14;
    }

    private static Version getVersion(){
        String version = Bukkit.getBukkitVersion().split("-")[0];
        switch (version){
            //Assume default API is latest.
            default: return Version.API_1_16;
            case "1.15": return Version.API_1_15;
            case "1.14": return Version.API_1_14;

        }
    }

    private MaterialChecker getMaterialChecker(Version version){
        switch (version){
            case API_1_16: return new MaterialChecker_1_16();
            case API_1_15: return new MaterialChecker_1_15();
            default: return MaterialChecker.DEFAULT;
        }
    }
}
