package com.jamesdpeters.chestsplusplus.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.autocraft.RecipeEditorHolder;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.testing.CommandHostPlugin;
import com.jamesdpeters.chestsplusplus.testing.DeferredProfileServer;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import com.jamesdpeters.chestsplusplus.ui.UiService;
import com.jamesdpeters.chestsplusplus.ui.menu.PaginatedMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class GroupCommandsIntegrationTest extends PluginTestBase {

    private final DeferredProfileServer profiles = new DeferredProfileServer();
    private World world;
    private PlayerMock alice;
    private PlayerMock bob;

    @Override
    protected ServerMock createServer() {
        return MockBukkit.mock(profiles);
    }

    @BeforeEach
    void setUp() {
        MockBukkit.loadSimple(CommandHostPlugin.class);
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
    }

    private ChestLinkGroup create(PlayerMock owner, String name, int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.CHEST);
        LinkService links = plugin.services().get(LinkService.class);
        links.link(owner, GroupType.CHESTLINK, name, block, BlockFace.NORTH, true);
        drain(owner);
        return (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, owner.getUniqueId(), name);
    }

    private static List<String> drain(PlayerMock player) {
        List<String> out = new ArrayList<>();
        String line;
        while ((line = nextPlain(player)) != null) out.add(line);
        return out;
    }

    private List<String> run(PlayerMock player, String command) {
        server.dispatchCommand(player, command);
        return drain(player);
    }

    @Test
    void listShowsOwnAndTrustedGroupsWithReferences() {
        create(alice, "ores", 0);
        create(bob, "wood", 2);
        plugin.services().trust().trust(bob.getUniqueId(), alice.getUniqueId());

        List<String> lines = run(alice, "cl list");

        assertThat(lines).anyMatch(l -> l.contains("ores")).anyMatch(l -> l.contains("wood") && l.contains("Bob"));
    }

    @Test
    void renamePublicAndSortAreOwnerOnly() {
        ChestLinkGroup group = create(alice, "ores", 0);

        assertThat(run(bob, "cl rename Alice:ores mine")).anyMatch(l -> l.contains("don't have access"));
        group.setPublic(true);
        assertThat(run(bob, "cl rename Alice:ores mine")).anyMatch(l -> l.contains("Only the owner"));

        run(alice, "cl rename ores metals");
        assertThat(group.name()).isEqualTo("metals");
        assertThat(group.inventory().getViewers()).isEmpty();
        run(alice, "cl public metals false");
        assertThat(group.isPublic()).isFalse();
        run(alice, "cl sort metals amount_desc");
        assertThat(group.sortMode()).isEqualTo(SortMode.AMOUNT_DESC);
        assertThat(plugin.services().groupStore().isDirty(group)).isTrue();
    }

    @Test
    void quotedNamesCanHaveSpaces() {
        ChestLinkGroup group = create(alice, "ores", 0);
        group.setPublic(true);

        run(alice, "cl rename ores \"Iron Ore\"");
        assertThat(group.name()).isEqualTo("Iron Ore");
        run(bob, "cl open \"Alice:Iron Ore\"");
        assertThat(bob.getOpenInventory().getTopInventory()).isSameAs(group.inventory());
        assertThat(run(alice, "cl rename \"Iron Ore\" \"Iron  Ore\"")).anyMatch(l -> l.contains("isn't a valid name"));
    }

    @Test
    void actualListClickCommandsOpenNamesWithSpacesForBothOwnersAndTypes() {
        for (GroupType type : GroupType.values()) {
            for (PlayerMock owner : List.of(alice, bob)) {
                StorageGroup group;
                if (type == GroupType.CHESTLINK) group = create(owner, "Iron Ore", owner == alice ? 0 : 2);
                else {
                    group = new AutoCraftGroup(plugin.services().groups().nextId(), owner.getUniqueId(), "Iron Ore", 0);
                    plugin.services().groups().add(group);
                }
                group.setPublic(true);
            }
            server.dispatchCommand(alice, type == GroupType.CHESTLINK ? "cl list" : "ac list");
            List<String> commands = new ArrayList<>();
            Component message;
            while ((message = alice.nextComponentMessage()) != null) collectCommands(message, commands);
            assertThat(commands).hasSize(2);
            for (String command : commands) {
                StorageGroup expected = plugin.services().groups().find(type,
                        command.contains("Bob:") ? bob.getUniqueId() : alice.getUniqueId(), "Iron Ore");
                assertThat(command).contains("\"");
                assertThat(server.dispatchCommand(alice, command.substring(1))).isTrue();
                if (expected instanceof ChestLinkGroup chest) assertThat(alice.getOpenInventory().getTopInventory()).isSameAs(chest.inventory());
                else assertThat(((RecipeEditorHolder) alice.getOpenInventory().getTopInventory().getHolder()).group()).isSameAs(expected);
                alice.closeInventory();
                drain(alice);
            }
        }
    }

    private static void collectCommands(Component component, List<String> commands) {
        ClickEvent<?> click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) commands.add(((ClickEvent.Payload.Text) click.payload()).value());
        component.children().forEach(child -> collectCommands(child, commands));
    }

    @Test
    void membersAndTrustCommandsGrantAccess() {
        ChestLinkGroup group = create(alice, "ores", 0);

        run(alice, "cl members add ores Bob");
        assertThat(group.members()).containsExactly(bob.getUniqueId());
        assertThat(run(alice, "cl members list ores")).anyMatch(l -> l.contains("Bob"));
        run(alice, "cl members remove ores Bob");
        assertThat(group.members()).isEmpty();

        run(alice, "cpp trust add Bob");
        assertThat(plugin.services().trust().isTrusted(alice.getUniqueId(), bob.getUniqueId())).isTrue();
        assertThat(run(alice, "cpp trust list")).anyMatch(l -> l.contains("Bob"));
        run(bob, "cl open Alice:ores");
        assertThat(bob.getOpenInventory().getTopInventory()).isSameAs(group.inventory());
        run(alice, "cpp trust remove Bob");
        assertThat(plugin.services().trust().isTrusted(alice.getUniqueId(), bob.getUniqueId())).isFalse();
        assertThat(run(alice, "cpp trust add Alice")).anyMatch(l -> l.contains("yourself"));
    }

    private void finishLookup(String name, UUID id) {
        profiles.resolve(name, id);
        server.getScheduler().performOneTick();
    }

    @Test
    void memberLookupOfUncachedNameAddsMemberOnceItFinishes() {
        ChestLinkGroup group = create(alice, "ores", 0);
        UUID carol = UUID.randomUUID();

        assertThat(run(alice, "cl members add ores Carol")).anyMatch(l -> l.contains("Looking up"));
        assertThat(group.members()).isEmpty();
        finishLookup("Carol", carol);

        assertThat(group.members()).containsExactly(carol);
        assertThat(drain(alice)).anyMatch(l -> l.contains("Added Carol"));
    }

    @Test
    void memberAddFinishingAfterGroupRemovalChangesNothing() {
        ChestLinkGroup group = create(alice, "ores", 0);
        UUID carol = UUID.randomUUID();

        run(alice, "cl members add ores Carol");
        run(alice, "cl remove ores");
        finishLookup("Carol", carol);

        assertThat(group.members()).isEmpty();
        assertThat(plugin.services().groups().memberOf(carol)).isEmpty();
        assertThat(drain(alice)).anyMatch(l -> l.contains("No group named ores")).noneMatch(l -> l.contains("Added"));
    }

    @Test
    void memberRemoveFinishingAfterGroupRemovalChangesNothing() {
        ChestLinkGroup group = create(alice, "ores", 0);
        UUID carol = UUID.randomUUID();
        plugin.services().get(LinkService.class).addMember(group, carol);

        run(alice, "cl members remove ores Carol");
        run(alice, "cl remove ores");
        finishLookup("Carol", carol);

        assertThat(group.members()).containsExactly(carol);
        assertThat(drain(alice)).anyMatch(l -> l.contains("No group named ores")).noneMatch(l -> l.contains("Removed"));
    }

    @Test
    void removeDropsItemsAtPlayerAndUnlinks() {
        ChestLinkGroup group = create(alice, "ores", 0);
        group.inventory().addItem(ItemStack.of(Material.COAL, 12));

        run(alice, "cl remove ores");

        assertThat(plugin.services().groups().byId(group.id())).isNull();
        assertThat(plugin.services().nodes().get(BlockPos.of(world.getBlockAt(0, 64, 0)))).isNull();
        assertThat(world.getEntitiesByClass(Item.class)).anyMatch(i -> i.getItemStack().getType() == Material.COAL);
    }

    @Test
    void helpListsOnlyPermittedCommands() {
        List<String> lines = run(alice, "cpp help");

        assertThat(lines).anyMatch(l -> l.contains("chestlink add <group>"));
        assertThat(lines).anyMatch(l -> l.contains("chestlink members add <group> <player>"));
        assertThat(lines).noneMatch(l -> l.contains("reload"));
    }

    @Test
    void gridOpensGroupOnLeftClickAndReturnsAfterwards() {
        ChestLinkGroup group = create(alice, "ores", 0);
        UiService ui = plugin.services().get(UiService.class);

        PaginatedMenu menu = ui.openGrid(alice, GroupType.CHESTLINK);
        assertThat(alice.getOpenInventory().getTopInventory()).isSameAs(menu.getInventory());
        assertThat(menu.getInventory().getItem(0).getType()).isEqualTo(Material.CHEST);

        server.getPluginManager()
                .callEvent(new org.bukkit.event.inventory.InventoryClickEvent(alice.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0, org.bukkit.event.inventory.ClickType.LEFT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        assertThat(alice.getOpenInventory().getTopInventory()).isSameAs(group.inventory());
    }

    @Test
    void hubSearchFiltersByNameAndOwner() {
        create(alice, "ores", 0);
        create(alice, "wood", 2);
        UiService ui = plugin.services().get(UiService.class);

        assertThat(ui.hubGroups(alice, GroupType.CHESTLINK, "OR").groups()).extracting(g -> g.name()).containsExactly("ores");
        assertThat(ui.hubGroups(alice, GroupType.CHESTLINK, "alice").groups()).hasSize(2);
        assertThat(ui.hubGroups(bob, GroupType.CHESTLINK, "").groups()).isEmpty();
    }
}
