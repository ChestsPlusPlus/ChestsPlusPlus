package com.jamesdpeters.minecraft.chests.filters;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;

public enum CustomTag {
	DYES(Arrays.asList(Material.WHITE_DYE, Material.BLACK_DYE /* ... */)), //
	TRASH(Arrays.asList(Material.GRANITE, Material.DIORITE, Material.ANDESITE));

	private final List<Material> materials;

	private CustomTag(List<Material> materials) {
		this.materials = materials;
	}

	public boolean isTagged(Material material) {
		return this.materials.contains(material);
	}

}
