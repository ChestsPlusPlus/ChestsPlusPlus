package com.jamesdpeters.chestsplusplus.persistence;

import java.sql.Types;
import org.bukkit.inventory.ItemStack;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jspecify.annotations.Nullable;

/**
 * Stores {@code ItemStack[]} as a BLOB with Paper's item serialisation, which upgrades old data versions on read. Items are serialised when
 * the statement binds, on the persistence I/O thread, so a store's snapshot must hold clones rather than live inventory mirrors.
 */
final class ItemsBlob {

    private ItemsBlob() {}

    static void register(Jdbi jdbi) {
        jdbi.registerArgument(new AbstractArgumentFactory<ItemStack[]>(Types.BLOB) {
            @Override
            protected Argument build(ItemStack[] value, ConfigRegistry config) {
                return (position, statement, context) -> statement.setBytes(position, ItemStack.serializeItemsAsBytes(value));
            }
        });
        jdbi.registerColumnMapper(QualifiedType.of(ItemStack[].class), (row, column, context) -> {
            byte[] bytes = row.getBytes(column);
            return bytes == null ? null : items(bytes);
        });
    }

    /** Deserialises and normalises empty stacks to {@code null} (nulls are stored as AIR x0). */
    static @Nullable ItemStack[] items(byte[] bytes) {
        @Nullable ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.isEmpty()) items[i] = null;
        }
        return items;
    }
}
