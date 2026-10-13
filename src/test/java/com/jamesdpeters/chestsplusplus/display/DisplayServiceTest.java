package com.jamesdpeters.chestsplusplus.display;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DisplayServiceTest extends PluginTestBase {

    @ParameterizedTest
    @EnumSource(GroupType.class)
    void disabledDisplaysDoNotCalculateTheirContents(GroupType type) {
        reconfigure(type.pick("chestlink", "autocraft") + ".display.enabled", false);

        CountingContent content = update(type);

        assertThat(content.items).isZero();
        assertThat(content.labels).isZero();
    }

    @ParameterizedTest
    @EnumSource(GroupType.class)
    void hiddenLabelsAreNotRenderedWhileItemsStillUpdate(GroupType type) {
        CountingContent content = update(type);

        assertThat(content.items).isPositive();
        assertThat(content.labels).isZero();
    }

    private CountingContent update(GroupType type) {
        var world = server.addSimpleWorld("display");
        world.loadChunk(0, 0);
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        UUID owner = UUID.randomUUID();
        StorageGroup group = type.pick(new ChestLinkGroup(1, owner, "display", 0), new AutoCraftGroup(1, owner, "display", 0));
        GroupRegistry groups = new GroupRegistry();
        groups.add(group);
        NodeIndex nodes = new NodeIndex();
        Node node = new Node(BlockPos.of(block), BlockFace.NORTH, group.id());
        nodes.put(node);
        CountingContent content = new CountingContent();
        DisplayService displays = new DisplayService(plugin, groups, nodes, services()::settings, _ -> content);
        displays.nodeAdded(node);
        content.items = 0;
        content.labels = 0;
        displays.requestUpdate(group);
        for (int tick = 0; tick < 8; tick++) displays.tick();
        return content;
    }

    private static final class CountingContent implements DisplayService.Content {
        private int items;
        private int labels;

        @Override
        public ItemStack item(StorageGroup group) {
            items++;
            return ItemStack.of(Material.STONE);
        }

        @Override
        public Component label(StorageGroup group) {
            labels++;
            return Component.text("display");
        }
    }
}
