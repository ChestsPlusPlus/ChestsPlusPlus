package com.jamesdpeters.chestsplusplus.chestlink.sort;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.Arrays;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Runs under MockBukkit because ItemStacks need a server for their registries. */
class SorterTest extends PluginTestBase {

    private static @Nullable ItemStack[] contents() {
        @Nullable ItemStack[] items = new ItemStack[54];
        items[3] = ItemStack.of(Material.STONE, 40);
        items[10] = ItemStack.of(Material.DIRT, 5);
        items[20] = ItemStack.of(Material.STONE, 40);
        items[30] = ItemStack.of(Material.DIAMOND_SWORD);
        items[31] = ItemStack.of(Material.DIAMOND_SWORD);
        return items;
    }

    private static int total(@Nullable ItemStack[] items, Material type) {
        return Arrays.stream(items).filter(Objects::nonNull).filter(i -> i.getType() == type).mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    void condensesAndOrdersByName() {
        @Nullable ItemStack[] sorted = Sorter.sort(contents(), SortMode.NAME, 54);

        assertThat(sorted[0]).isEqualTo(ItemStack.of(Material.DIAMOND_SWORD));
        assertThat(sorted[1]).isEqualTo(ItemStack.of(Material.DIAMOND_SWORD));
        assertThat(sorted[2]).isEqualTo(ItemStack.of(Material.DIRT, 5));
        assertThat(sorted[3]).isEqualTo(ItemStack.of(Material.STONE, 64));
        assertThat(sorted[4]).isEqualTo(ItemStack.of(Material.STONE, 16));
        assertThat(sorted[5]).isNull();
    }

    @Test
    void ordersByAmountWithoutLosingItems() {
        @Nullable ItemStack[] desc = Sorter.sort(contents(), SortMode.AMOUNT_DESC, 54);
        @Nullable ItemStack[] asc = Sorter.sort(contents(), SortMode.AMOUNT_ASC, 54);

        assertThat(desc[0].getType()).isEqualTo(Material.STONE);
        assertThat(asc[0].getType()).isEqualTo(Material.DIAMOND_SWORD);
        for (Material type : new Material[]{Material.STONE, Material.DIRT, Material.DIAMOND_SWORD}) {
            assertThat(total(desc, type)).isEqualTo(total(contents(), type));
            assertThat(total(asc, type)).isEqualTo(total(contents(), type));
        }
    }

    @Test
    void offLeavesContentsAlone() {
        @Nullable ItemStack[] items = contents();
        assertThat(Sorter.sort(items, SortMode.OFF, 54)).isSameAs(items);
    }
}
