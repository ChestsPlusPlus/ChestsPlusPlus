package com.jamesdpeters.minecraft.chests.misc;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    List<String> lore;
    Material material;
    boolean enchanted;
    String name;

    OfflinePlayer skullOwner;
    ItemStack itemStack;

    private ItemBuilder() { }

    // Material must be provided for every item.
    public static ItemBuilder getInstance(Material material) {
        ItemBuilder builder = new ItemBuilder();
        builder.setMaterial(material);
        return builder;
    }

    public static ItemBuilder fromInstance(ItemStack itemStack) {
        ItemBuilder builder = new ItemBuilder();
        builder.itemStack = itemStack;
        return builder;
    }

    public static ItemBuilder getPlayerHead(OfflinePlayer skullOwner) {
        ItemBuilder builder = getInstance(Material.PLAYER_HEAD);
        builder.skullOwner = skullOwner;
        return builder;
    }

    public ItemBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public ItemBuilder setMaterial(Material material) {
        this.material = material;
        return this;
    }

    public ItemBuilder addLore(String lore){
        if (this.lore == null) this.lore = new ArrayList<>();
        this.lore.add(lore);
        return this;
    }

    public ItemBuilder setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    public ItemStack get() {
        if (itemStack == null) itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);

            if (enchanted) {
                itemStack.addUnsafeEnchantment(Enchantment.LURE, 1);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            if (skullOwner != null && meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(skullOwner);
            }
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }


}
