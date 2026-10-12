package com.jamesdpeters.chestsplusplus.chestlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChestLinkDisplayTest extends PluginTestBase {

    private ChestLinkGroup group;

    @BeforeEach
    void setUp() {
        group = new ChestLinkGroup(1, UUID.randomUUID(), "display", 0);
        group.attachInventory(server.createInventory(null, ChestLinkGroup.SIZE));
    }

    @Test
    void emptyInventoryHasNoDisplayItem() {
        assertThat(ChestLinkService.mostCommon(group)).isNull();
    }

    @Test
    void countsSplitStacksByMaterialWithoutChangingTheirContents() {
        ItemStack named = ItemStack.of(Material.COAL, 8);
        named.editMeta(meta -> meta.displayName(Component.text("Lump")));
        group.inventory().setItem(0, named);
        group.inventory().setItem(1, ItemStack.of(Material.DIRT, 12));
        group.inventory().setItem(2, ItemStack.of(Material.COAL, 6));

        assertThat(ChestLinkService.mostCommon(group)).isEqualTo(ItemStack.of(Material.COAL));
        assertThat(group.inventory().getItem(0)).isEqualTo(named);
        assertThat(group.inventory().getItem(2)).isEqualTo(ItemStack.of(Material.COAL, 6));
    }

    @Test
    void aTiedTotalKeepsTheMaterialThatReachedItFirst() {
        group.inventory().setItem(0, ItemStack.of(Material.COAL, 10));
        group.inventory().setItem(1, ItemStack.of(Material.DIRT, 20));
        group.inventory().setItem(2, ItemStack.of(Material.COAL, 10));

        assertThat(ChestLinkService.mostCommon(group)).isEqualTo(ItemStack.of(Material.DIRT));
    }
}
