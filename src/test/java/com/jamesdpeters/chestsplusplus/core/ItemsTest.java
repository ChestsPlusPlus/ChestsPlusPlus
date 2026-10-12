package com.jamesdpeters.chestsplusplus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Runs under MockBukkit because ItemStacks need a server for their registries. */
class ItemsTest extends PluginTestBase {

    @Test
    void aRealStackIsPresent() {
        assertThat(Items.present(ItemStack.of(Material.STONE))).isTrue();
        assertThat(Items.present(ItemStack.of(Material.STONE, 64))).isTrue();
    }

    @Test
    void nullAirAndZeroAmountAreNotPresent() {
        assertThat(Items.present(null)).isFalse();
        assertThat(Items.present(ItemStack.of(Material.AIR))).isFalse();
        assertThat(Items.present(ItemStack.empty())).isFalse();
        assertThat(Items.present(ItemStack.of(Material.STONE).asQuantity(0))).isFalse();
    }
}
