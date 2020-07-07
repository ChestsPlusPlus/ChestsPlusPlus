package com.jamesdpeters.minecraft.chests;

public interface NMSProvider {
    ChestOpener getChestOpener();
    MaterialChecker getMaterialChecker();
}
