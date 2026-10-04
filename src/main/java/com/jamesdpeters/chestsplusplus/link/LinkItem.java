package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * The item dropped when a linked block is broken with Silk Touch (plan §5.2): placing it re-links to the same group,
 * no sign needed. Identified by PDC (group id + type); named via data components.
 */
public final class LinkItem {

    /** A decoded link item. */
    public record Link(long groupId, GroupType type) {}

    private final NamespacedKey groupKey;
    private final NamespacedKey typeKey;

    public LinkItem(Plugin plugin) {
        this.groupKey = new NamespacedKey(plugin, "link_group");
        this.typeKey = new NamespacedKey(plugin, "link_type");
    }

    public ItemStack create(StorageGroup group, Material block, Messages messages) {
        ItemStack item = ItemStack.of(block);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(groupKey, PersistentDataType.LONG, group.id());
            pdc.set(typeKey, PersistentDataType.STRING, group.type().name());
        });
        String typeName = group.type() == GroupType.CHESTLINK ? "ChestLink" : "AutoCraft";
        item.setData(DataComponentTypes.ITEM_NAME,
                messages.get(Message.ITEM_LINKED_NAME, Messages.text("type", typeName), Messages.text("group", group.name())));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(messages.get(Message.ITEM_LINKED_LORE, Messages.text("group", group.name())))));
        return item;
    }

    public @Nullable Link read(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        Long id = pdc.get(groupKey, PersistentDataType.LONG);
        String type = pdc.get(typeKey, PersistentDataType.STRING);
        if (id == null || type == null) return null;
        try {
            return new Link(id, GroupType.valueOf(type.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
