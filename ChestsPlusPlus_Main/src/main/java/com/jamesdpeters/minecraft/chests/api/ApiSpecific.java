package com.jamesdpeters.minecraft.chests.api;

import com.jamesdpeters.minecraft.chests.Api;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.v1_21_R1.MaterialChecker_1_21_R1;
import lombok.Getter;
import org.bukkit.plugin.Plugin;

public class ApiSpecific {

    @Getter
    private static MaterialChecker materialChecker;

    public static boolean init(Plugin plugin) {
        Api.init(plugin);
        materialChecker = new MaterialChecker_1_21_R1();

        return true;
    }
}
