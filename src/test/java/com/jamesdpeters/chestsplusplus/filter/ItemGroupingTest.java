package com.jamesdpeters.chestsplusplus.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class ItemGroupingTest {

    private final ItemGrouping grouping = new ItemGrouping(List.of(Set.of(Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG),
            Set.of(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS), Set.of(Material.DIAMOND))); // single-member groups are ignored

    @Test
    void materialsInASharedGroupAreSimilar() {
        assertThat(grouping.similar(Material.OAK_LOG, Material.SPRUCE_LOG)).isTrue();
        assertThat(grouping.similar(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS)).isTrue();
    }

    @Test
    void otherwiseOnlyEqualMaterialsAreSimilar() {
        assertThat(grouping.similar(Material.OAK_LOG, Material.WHEAT_SEEDS)).isFalse();
        assertThat(grouping.similar(Material.STONE, Material.STONE)).isTrue();
        assertThat(grouping.similar(Material.DIAMOND, Material.EMERALD)).isFalse();
        assertThat(grouping.groupCount(Material.DIAMOND)).isZero();
    }
}
