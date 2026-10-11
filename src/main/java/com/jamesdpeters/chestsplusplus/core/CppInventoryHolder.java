package com.jamesdpeters.chestsplusplus.core;

import org.bukkit.inventory.InventoryHolder;

/** Holder of an inventory ChestsPlusPlus creates. Disabling the plugin closes every one, since only our listeners keep them safe. */
public interface CppInventoryHolder extends InventoryHolder {}
