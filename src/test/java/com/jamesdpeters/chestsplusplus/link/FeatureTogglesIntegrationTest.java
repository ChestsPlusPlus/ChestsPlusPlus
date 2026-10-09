package com.jamesdpeters.chestsplusplus.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import com.jamesdpeters.chestsplusplus.testing.TileEntityWorld;
import com.jamesdpeters.chestsplusplus.ui.UiService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FeatureTogglesIntegrationTest extends PluginTestBase {

    private static final String DISABLED = "That feature is disabled on this server.";

    private World world;
    private PlayerMock alice;
    private Block chest;
    private ChestLinkGroup group;
    private GroupActions actions;

    @BeforeEach
    void setUp() {
        TileEntityWorld tileWorld = new TileEntityWorld();
        tileWorld.setName("world");
        server.addWorld(tileWorld);
        world = tileWorld;
        world.loadChunk(0, 0);
        alice = server.addPlayer("Alice");
        chest = world.getBlockAt(0, 64, 0);
        chest.setType(Material.CHEST);
        group = (ChestLinkGroup) plugin.services().get(LinkService.class).link(alice, GroupType.CHESTLINK, "loot", chest, BlockFace.NORTH, true);
        group.inventory().addItem(ItemStack.of(Material.DIAMOND, 5));
        actions = plugin.services().get(GroupActions.class);
        lastMessage();
    }

    private PlayerInteractEvent rightClick(Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** The last message Alice received since the previous call. */
    private @Nullable String lastMessage() {
        String last = null;
        for (String next = nextPlain(alice); next != null; next = nextPlain(alice)) last = next;
        return last;
    }

    private boolean groupOpen() {
        return alice.getOpenInventory().getTopInventory() == group.inventory();
    }

    @Test
    void clickingADisabledChestLinkRefusesWithoutOpeningTheEmptyChest() {
        reconfigure("features.chestlinks", false);

        PlayerInteractEvent event = rightClick(chest);

        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        assertThat(groupOpen()).isFalse();
        assertThat(lastMessage()).contains(DISABLED);
    }

    @Test
    void reloadClosesAnOpenChestLinkAndReEnablingRestoresIt() {
        rightClick(chest);
        assertThat(groupOpen()).isTrue();

        reconfigure("features.chestlinks", false);
        assertThat(groupOpen()).isFalse();

        reconfigure("features.chestlinks", true);
        rightClick(chest);
        assertThat(groupOpen()).isTrue();
        assertThat(group.inventory().contains(Material.DIAMOND, 5)).isTrue();
        assertThat(plugin.services().nodes().get(BlockPos.of(chest))).isNotNull();
    }

    @Test
    void groupActionsAndMenusRefuseWhileDisabled() {
        reconfigure("features.chestlinks", false);

        assertThat(actions.openRemote(alice, group)).isFalse();
        assertThat(lastMessage()).contains(DISABLED);
        assertThat(actions.rename(alice, group, "renamed")).isFalse();
        assertThat(actions.setPublic(alice, group, true)).isFalse();
        assertThat(actions.sort(alice, group, SortMode.NAME)).isFalse();
        assertThat(actions.remove(alice, group)).isFalse();
        actions.list(alice, GroupType.CHESTLINK);
        assertThat(lastMessage()).contains(DISABLED);
        plugin.services().get(UiService.class).openHub(alice, GroupType.CHESTLINK, "", 0);
        assertThat(lastMessage()).contains(DISABLED);

        assertThat(groupOpen()).isFalse();
        assertThat(plugin.services().groups().byId(group.id())).isSameAs(group);
        assertThat(group.name()).isEqualTo("loot");
        assertThat(group.isPublic()).isFalse();
        assertThat(group.sortMode()).isEqualTo(SortMode.OFF);
    }

    @Test
    void autoCraftBeingDisabledLeavesChestLinksWorking() {
        reconfigure("features.autocraft", false);

        assertThat(actions.openRemote(alice, group)).isTrue();
        assertThat(groupOpen()).isTrue();
    }

    @Test
    void breakingADisabledLinkedBlockIsRefusedAndKeepsTheGroup() {
        reconfigure("features.chestlinks", false);

        BlockBreakEvent event = new BlockBreakEvent(chest, alice);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(lastMessage()).contains(DISABLED);
        assertThat(plugin.services().groups().byId(group.id())).isSameAs(group);
        assertThat(plugin.services().nodes().get(BlockPos.of(chest))).isNotNull();
        assertThat(group.inventory().contains(Material.DIAMOND, 5)).isTrue();
        assertThat(world.getEntitiesByClass(Item.class)).isEmpty();
        assertThat(((Container) chest.getState(false)).getInventory().isEmpty()).isTrue();
    }
}
