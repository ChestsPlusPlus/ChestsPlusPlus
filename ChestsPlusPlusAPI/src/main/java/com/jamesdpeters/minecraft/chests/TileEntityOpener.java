package com.jamesdpeters.minecraft.chests;

import org.bukkit.entity.HumanEntity;

import java.util.List;

public interface TileEntityOpener {
    void setViewers(List<HumanEntity> viewers);
}
