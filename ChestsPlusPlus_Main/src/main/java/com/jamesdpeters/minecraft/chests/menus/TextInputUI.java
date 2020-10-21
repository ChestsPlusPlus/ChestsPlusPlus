package com.jamesdpeters.minecraft.chests.menus;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.entity.Player;
import java.util.function.BiFunction;

public class TextInputUI {


    public static void getInput(Player player, String title, BiFunction<Player, String, AnvilGUI.Response> responseBiFunction) {
        new AnvilGUI.Builder()
                .onComplete(responseBiFunction)
                .title(title)
                .plugin(ChestsPlusPlus.PLUGIN)
                .open(player);
    }

}
