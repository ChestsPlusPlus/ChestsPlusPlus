package com.jamesdpeters.minecraft.chests.api;

import com.jamesdpeters.minecraft.chests.Api;
import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import lombok.Getter;
import org.bukkit.plugin.Plugin;

public class ApiSpecific {

    @Getter
    private static MaterialChecker materialChecker;
    @Getter
    private static ChestOpener chestOpener;
    @Getter
    private static NMSProvider nmsProvider;

    public static boolean init(Plugin plugin) {
        nmsProvider = Api.init(plugin);
        if (nmsProvider != null) {
            materialChecker = nmsProvider.getMaterialChecker();
            chestOpener = nmsProvider.getChestOpener();
        }

        return (nmsProvider != null && materialChecker != null && chestOpener != null);
    }
}
