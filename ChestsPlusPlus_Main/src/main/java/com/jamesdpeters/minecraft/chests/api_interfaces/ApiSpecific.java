package com.jamesdpeters.minecraft.chests.api_interfaces;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.MaterialChecker_1_15;
import com.jamesdpeters.minecraft.chests.MaterialChecker_1_16;
import org.bukkit.Bukkit;

import java.util.Arrays;

public class ApiSpecific {

    private static MaterialChecker materialChecker;
    private static Version version;

    public static void init(){
        version = getVersion();
        ChestsPlusPlus.PLUGIN.getLogger().info("Found API version: "+version);
        materialChecker = getMaterialChecker(version);
    }

    enum Version {
        API_1_16,
        API_1_15,
        API_1_14;
    }

    private static Version getVersion(){
        String version = Bukkit.getBukkitVersion().split("-")[0];
        String[] versionRevisions = version.split("\\.");
        String minorVersion = versionRevisions[1];

        //Switch minor revision number e.g 1.xx
        switch (minorVersion){
            //Assume default API is latest.
            default: return Version.API_1_16;
            case "15": return Version.API_1_15;
            case "14": return Version.API_1_14;
        }
    }

    private static MaterialChecker getMaterialChecker(Version version){
        switch (version){
            case API_1_16: return new MaterialChecker_1_16();
            case API_1_15: return new MaterialChecker_1_15();
            default: return MaterialChecker.Version_1_14;
        }
    }

    public static MaterialChecker getMaterialChecker() {
        return materialChecker;
    }
}
