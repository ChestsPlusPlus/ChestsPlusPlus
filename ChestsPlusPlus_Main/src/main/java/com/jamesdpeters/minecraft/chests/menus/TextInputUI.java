package com.jamesdpeters.minecraft.chests.menus;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.function.BiFunction;

public class TextInputUI {

    public static void getInput(Player player, String title, BiFunction<Player, String, AnvilGUI.ResponseAction> responseBiFunction) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot == AnvilGUI.Slot.OUTPUT)
                        return Collections.singletonList(responseBiFunction.apply(stateSnapshot.getPlayer(), stateSnapshot.getText()));
                    return Collections.emptyList();
                })
                .text("Enter Name")
                .title(title)
                .plugin(ChestsPlusPlus.PLUGIN)
                .open(player);
    }

}
