package com.jamesdpeters.chestsplusplus.chestlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.LinkItem;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import com.jamesdpeters.chestsplusplus.testing.TileEntityWorld;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ChestLinkIntegrationTest extends PluginTestBase {

    private World world;
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void setUp() {
        TileEntityWorld tileWorld = new TileEntityWorld();
        tileWorld.setName("world");
        server.addWorld(tileWorld);
        world = tileWorld;
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        world.loadChunk(0, 0);
    }

    private Block chestAt(int x, int z) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(Material.CHEST);
        return block;
    }

    /** Places a wall sign on the north face of {@code target} and submits the given lines. */
    private SignChangeEvent sign(PlayerMock player, Block target, String header, String name) {
        Block sign = target.getRelative(BlockFace.NORTH);
        sign.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) sign.getBlockData();
        data.setFacing(BlockFace.NORTH);
        sign.setBlockData(data);
        SignChangeEvent event = new SignChangeEvent(sign, player,
                List.of(Component.text(header), Component.text(name), Component.empty(), Component.empty()), org.bukkit.block.sign.Side.FRONT);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private ChestLinkGroup group(PlayerMock owner, String name) {
        return (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, owner.getUniqueId(), name);
    }

    private void rightClick(PlayerMock player, Block block) {
        server.getPluginManager()
                .callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.NORTH, EquipmentSlot.HAND));
    }

    @Test
    void signCreatesGroupLinksBlockAndIsRemoved() {
        Block chest = chestAt(0, 0);
        ((Container) chest.getState(false)).getInventory().addItem(ItemStack.of(Material.DIAMOND, 5));

        SignChangeEvent event = sign(alice, chest, "[ChestLink]", "ores");
        server.getScheduler().performTicks(1);

        ChestLinkGroup group = group(alice, "ores");
        assertThat(group).isNotNull();
        assertThat(event.isCancelled()).isTrue();
        assertThat(chest.getRelative(BlockFace.NORTH).getType()).isEqualTo(Material.AIR);
        Node node = plugin.services().nodes().get(BlockPos.of(chest));
        assertThat(node).isNotNull();
        assertThat(node.facing()).isEqualTo(BlockFace.NORTH);
        // Existing contents move into the group; the physical chest is emptied.
        assertThat(group.inventory().contains(Material.DIAMOND, 5)).isTrue();
        assertThat(((Container) chest.getState(false)).getInventory().isEmpty()).isTrue();
        assertThat(plugin.services().groupStore().isDirty(group)).isTrue();
        assertThat(nextPlain(alice)).contains("Created ChestLink ores");
    }

    @Test
    void signOnProtectedChestIsRefused() {
        Block chest = chestAt(0, 0);
        ((Container) chest.getState(false)).getInventory().addItem(ItemStack.of(Material.DIAMOND, 5));
        denyInteract(chest);

        SignChangeEvent event = sign(alice, chest, "[ChestLink]", "loot");
        server.getScheduler().performTicks(1);

        assertThat(event.isCancelled()).isFalse();
        assertThat(group(alice, "loot")).isNull();
        assertThat(plugin.services().nodes().get(BlockPos.of(chest))).isNull();
        assertThat(((Container) chest.getState(false)).getInventory().contains(Material.DIAMOND, 5)).isTrue();
        assertThat(nextPlain(alice)).contains("You can't use that block here");
    }

    /** Mimics a container-lock plugin: sign placement is allowed, but using {@code locked} is denied. */
    private void denyInteract(Block locked) {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            void onInteract(PlayerInteractEvent event) {
                if (locked.equals(event.getClickedBlock())) event.setUseInteractedBlock(Event.Result.DENY);
            }
        }, MockBukkit.createMockPlugin());
    }

    private PlayerInteractEvent nameTag(PlayerMock player, Block block, ItemStack tag) {
        player.getInventory().setItemInMainHand(tag);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, tag, block, BlockFace.SOUTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static ItemStack namedTag(String name, int amount) {
        ItemStack tag = ItemStack.of(Material.NAME_TAG, amount);
        tag.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name));
        return tag;
    }

    @Test
    void namedTagLinksBlockAndIsUsedUp() {
        Block barrel = world.getBlockAt(0, 64, 0);
        barrel.setType(Material.BARREL);
        Directional data = (Directional) barrel.getBlockData();
        data.setFacing(BlockFace.EAST);
        barrel.setBlockData(data);
        ItemStack tag = namedTag("ores", 2);

        PlayerInteractEvent event = nameTag(alice, barrel, tag);

        ChestLinkGroup group = group(alice, "ores");
        assertThat(group).isNotNull();
        Node node = plugin.services().nodes().get(BlockPos.of(barrel));
        assertThat(node).isNotNull();
        // Clicked on the south side, but the link goes on the barrel's front.
        assertThat(node.facing()).isEqualTo(BlockFace.EAST);
        assertThat(event.useInteractedBlock()).isEqualTo(org.bukkit.event.Event.Result.DENY);
        assertThat(tag.getAmount()).isEqualTo(1);
        assertThat(nextPlain(alice)).contains("Created ChestLink ores");
    }

    @Test
    void namedTagIsKeptWhenConsumptionIsDisabled() {
        plugin.getConfig().set("linking.consume-name-tags", false);
        plugin.saveConfig();
        reloadPlugin();
        Block chest = chestAt(0, 0);
        ItemStack tag = namedTag("ores", 1);

        nameTag(alice, chest, tag);

        assertThat(plugin.services().nodes().get(BlockPos.of(chest))).isNotNull();
        assertThat(tag.getAmount()).isEqualTo(1);
    }

    @Test
    void unnamedOrBlankTagDoesNothing() {
        Block chest = chestAt(0, 0);

        nameTag(alice, chest, ItemStack.of(Material.NAME_TAG));
        nameTag(alice, chest, namedTag("   ", 1));

        assertThat(plugin.services().nodes().get(BlockPos.of(chest))).isNull();
        assertThat(plugin.services().groups().ownedBy(alice.getUniqueId(), GroupType.CHESTLINK)).isEmpty();
    }

    @Test
    void namedTagOnLinkedChestOpensIt() {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "first");
        ItemStack tag = namedTag("second", 1);

        nameTag(alice, chest, tag);

        assertThat(group(alice, "second")).isNull();
        assertThat(tag.getAmount()).isEqualTo(1);
        assertThat(alice.getOpenInventory().getTopInventory()).isSameAs(group(alice, "first").inventory());
    }

    @Test
    void secondChestSharesInventoryAndOpensIt() {
        sign(alice, chestAt(0, 0), "[ChestLink]", "shared");
        Block second = chestAt(5, 0);
        sign(alice, second, "[chestlink]", "SHARED");
        ChestLinkGroup group = group(alice, "shared");
        group.inventory().addItem(ItemStack.of(Material.EMERALD, 3));

        rightClick(alice, second);

        assertThat(plugin.services().nodes().count(group.id())).isEqualTo(2);
        assertThat(alice.getOpenInventory().getTopInventory()).isSameAs(group.inventory());
    }

    @Test
    void shiftClickIntoAnOpenChestLinkMarksItDirtyBeforeClose() throws InterruptedException {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "open");
        ChestLinkGroup group = group(alice, "open");
        plugin.services().persistence().flush();
        tickUntil(() -> !plugin.services().groupStore().isDirty(group));
        rightClick(alice, chest);
        InventoryView view = alice.getOpenInventory();

        server.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, view.getTopInventory().getSize(),
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));

        assertThat(alice.getOpenInventory().getTopInventory()).isSameAs(group.inventory());
        assertThat(plugin.services().groupStore().isDirty(group)).isTrue();
    }

    private void tickUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        assertThat(done.getAsBoolean()).as("condition reached within 10s").isTrue();
    }

    @Test
    void accessRequiresOwnerTrustMembershipOrPublic() {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "private");
        ChestLinkGroup group = group(alice, "private");

        rightClick(bob, chest);
        assertThat(bob.getOpenInventory().getTopInventory()).isNotSameAs(group.inventory());

        plugin.services().trust().trust(alice.getUniqueId(), bob.getUniqueId());
        rightClick(bob, chest);
        assertThat(bob.getOpenInventory().getTopInventory()).isSameAs(group.inventory());
    }

    @Test
    void linkingToAnotherPlayersGroupNeedsAccess() {
        sign(alice, chestAt(0, 0), "[ChestLink]", "alices");
        Block bobs = chestAt(3, 0);

        sign(bob, bobs, "[ChestLink]", "Alice:alices");
        assertThat(plugin.services().nodes().get(BlockPos.of(bobs))).isNull();

        group(alice, "alices").setPublic(true);
        sign(bob, bobs, "[ChestLink]", "Alice:alices");
        assertThat(plugin.services().nodes().get(BlockPos.of(bobs))).isNotNull();
    }

    @Test
    void limitsBlacklistAndInvalidNamesAreEnforced() {
        plugin.getConfig().set("limits.chestlink-default", 1);
        plugin.getConfig().set("chestlink.display.label", false);
        plugin.getConfig().set("worlds.blacklist", List.of("nether"));
        plugin.saveConfig();
        reloadPlugin();

        sign(alice, chestAt(0, 0), "[ChestLink]", "first");
        sign(alice, chestAt(2, 0), "[ChestLink]", "second");
        assertThat(group(alice, "second")).isNull();

        // Linking more blocks to an existing group doesn't count against the limit.
        sign(alice, chestAt(4, 0), "[ChestLink]", "first");
        assertThat(plugin.services().nodes().count(group(alice, "first").id())).isEqualTo(2);

        World nether = server.addSimpleWorld("nether");
        Block netherChest = nether.getBlockAt(0, 64, 0);
        netherChest.setType(Material.CHEST);
        sign(bob, netherChest, "[ChestLink]", "nope");
        assertThat(group(bob, "nope")).isNull();

        sign(bob, chestAt(6, 0), "[ChestLink]", "bad name!");
        assertThat(plugin.services().groups().size()).isEqualTo(1);
    }

    @Test
    void breakingLastNodeDropsItemsAndDeletesGroup() {
        Block a = chestAt(0, 0);
        Block b = chestAt(3, 0);
        sign(alice, a, "[ChestLink]", "g");
        sign(alice, b, "[ChestLink]", "g");
        ChestLinkGroup group = group(alice, "g");
        group.inventory().addItem(ItemStack.of(Material.IRON_INGOT, 10), ItemStack.of(Material.IRON_INGOT, 10));

        server.getPluginManager().callEvent(new BlockBreakEvent(a, alice));
        assertThat(group(alice, "g")).isNotNull();

        server.getPluginManager().callEvent(new BlockBreakEvent(b, alice));
        assertThat(group(alice, "g")).isNull();
        int dropped = world.getEntitiesByClass(Item.class).stream().mapToInt(item -> item.getItemStack().getAmount()).sum();
        assertThat(dropped).isEqualTo(20);
    }

    @Test
    void silkTouchDropsLinkItemThatRelinks() {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "portable");
        ChestLinkGroup group = group(alice, "portable");
        group.inventory().addItem(ItemStack.of(Material.GOLD_INGOT, 4));
        ItemStack linkItem = silkTouchBreak(chest);
        assertThat(group(alice, "portable")).isSameAs(group);
        LinkItem.Link link = plugin.services().get(LinkItem.class).read(linkItem);
        assertThat(link).isEqualTo(new LinkItem.Link(group.id(), GroupType.CHESTLINK));

        BlockPlaceEvent placeEvent = placeLinkItem(alice, linkItem);
        assertThat(plugin.services().nodes().at(placeEvent.getBlockPlaced()).groupId()).isEqualTo(group.id());
        assertThat(group.inventory().contains(Material.GOLD_INGOT, 4)).isTrue();
    }

    @Test
    void linkItemOfADeletedGroupPlacesAsANormalBlockAndNeverJoinsAnotherGroup() {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "portable");
        ChestLinkGroup deleted = group(alice, "portable");
        ItemStack linkItem = silkTouchBreak(chest);
        plugin.services().get(LinkService.class).removeGroup(deleted, chest.getLocation());
        Block bobsChest = chestAt(5, 5);
        sign(bob, bobsChest, "[ChestLink]", "mine");
        ChestLinkGroup bobs = group(bob, "mine");
        assertThat(bobs.id()).isNotEqualTo(deleted.id());
        assertThat(nextPlain(bob)).contains("Created ChestLink mine");

        BlockPlaceEvent event = placeLinkItem(bob, linkItem);

        assertThat(event.isCancelled()).isFalse();
        assertThat(plugin.services().nodes().at(event.getBlockPlaced())).isNull();
        assertThat(plugin.services().nodes().count(bobs.id())).isEqualTo(1);
        assertThat(nextPlain(bob)).contains("no longer exists");
    }

    @Test
    void linkItemPlacementRequiresCreatePermissionForPublicGroup() {
        ItemStack linkItem = publicLinkItem();
        bob.addAttachment(plugin).setPermission("chestsplusplus.chestlink.create", false);

        BlockPlaceEvent event = placeLinkItem(bob, linkItem);

        assertThat(event.isCancelled()).isTrue();
        assertThat(plugin.services().nodes().at(event.getBlockPlaced())).isNull();
    }

    @Test
    void linkItemPlacementAllowsDefaultCreatePermissionForPublicGroup() {
        ItemStack linkItem = publicLinkItem();

        BlockPlaceEvent event = placeLinkItem(bob, linkItem);

        assertThat(event.isCancelled()).isFalse();
        assertThat(plugin.services().nodes().at(event.getBlockPlaced()).groupId()).isEqualTo(group(alice, "portable").id());
    }

    private ItemStack publicLinkItem() {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "portable");
        plugin.services().get(LinkService.class).setPublic(group(alice, "portable"), true);
        return silkTouchBreak(chest);
    }

    private ItemStack silkTouchBreak(Block block) {
        ItemStack pickaxe = ItemStack.of(Material.DIAMOND_PICKAXE);
        pickaxe.addEnchantment(Enchantment.SILK_TOUCH, 1);
        alice.getInventory().setItemInMainHand(pickaxe);
        BlockBreakEvent event = new BlockBreakEvent(block, alice);
        server.getPluginManager().callEvent(event);
        assertThat(event.isDropItems()).isFalse();
        return world.getEntitiesByClass(Item.class).iterator().next().getItemStack();
    }

    private BlockPlaceEvent placeLinkItem(PlayerMock player, ItemStack linkItem) {
        Block placed = chestAt(9, 9);
        BlockPlaceEvent event = new BlockPlaceEvent(placed, placed.getState(), placed.getRelative(BlockFace.DOWN), linkItem, player, true,
                EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void explosionsSpareLinkedBlocks() {
        Block linked = chestAt(0, 0);
        Block plain = chestAt(1, 0);
        sign(alice, linked, "[ChestLink]", "g");
        List<Block> blocks = new ArrayList<>(List.of(linked, plain));

        server.getPluginManager().callEvent(new BlockExplodeEvent(plain, plain.getState(), blocks, 4f, ExplosionResult.DESTROY));

        assertThat(blocks).containsExactly(plain);
    }

    @Test
    void enableUnlinksStaleNodesInLoadedChunksBeforeSpawningDisplays() {
        Block replaced = chestAt(0, 0);
        Block intact = chestAt(4, 0);
        sign(alice, replaced, "[ChestLink]", "g");
        sign(alice, intact, "[ChestLink]", "g");

        server.getPluginManager().disablePlugin(plugin);
        replaced.setType(Material.FURNACE);
        server.getPluginManager().enablePlugin(plugin);

        assertThat(plugin.services().nodes().get(BlockPos.of(replaced))).isNull();
        assertThat(plugin.services().nodes().get(BlockPos.of(intact))).isNotNull();
        assertThat(plugin.services().get(DisplayService.class).count()).isEqualTo(1);
        assertThat(world.getEntitiesByClass(ItemDisplay.class)).hasSize(1);
        assertThat(group(alice, "g")).isNotNull();
    }

    @Test
    void staleNodesInUnloadedChunksAreUnlinkedWhenTheirChunkLoads() {
        sign(alice, chestAt(0, 0), "[ChestLink]", "g");
        LinkService links = plugin.services().get(LinkService.class);
        BlockPos far = new BlockPos(world.getUID(), 160, 64, 0);
        plugin.services().nodes().put(new Node(far, BlockFace.NORTH, group(alice, "g").id()));

        links.unlinkChangedBlocksInLoadedChunks();
        assertThat(plugin.services().nodes().get(far)).isNotNull();

        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(far.chunkX(), far.chunkZ()), false));
        assertThat(plugin.services().nodes().get(far)).isNull();
        assertThat(plugin.services().nodes().size()).isEqualTo(1);
    }

    @Test
    void hopperSearchSubstitutesGroupInventory() {
        Block chest = chestAt(0, 0);
        sign(alice, chest, "[ChestLink]", "g");
        Block hopper = world.getBlockAt(0, 65, 0);
        hopper.setType(Material.HOPPER);

        HopperInventorySearchEvent event = new HopperInventorySearchEvent(((Container) chest.getState(false)).getInventory(),
                HopperInventorySearchEvent.ContainerType.DESTINATION, hopper, chest);
        server.getPluginManager().callEvent(event);

        assertThat(event.getInventory()).isSameAs(group(alice, "g").inventory());
    }

    @Test
    void displaysSpawnForLoadedNodesAndFollowContents() {
        sign(alice, chestAt(0, 0), "[ChestLink]", "g");
        DisplayService displays = plugin.services().get(DisplayService.class);

        assertThat(displays.count()).isEqualTo(1);
        var itemDisplay = world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).iterator().next();
        assertThat(itemDisplay.isPersistent()).isFalse();
        assertThat(displays.isOurs(itemDisplay)).isTrue();

        ChestLinkGroup group = group(alice, "g");
        group.inventory().addItem(ItemStack.of(Material.COBBLESTONE, 64), ItemStack.of(Material.DIRT, 3));
        displays.requestUpdate(group);
        server.getScheduler().performTicks(10);
        // Empty (flat) -> cobblestone (block) moves the display, so it is respawned: fetch it again.
        var updated = world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class);
        assertThat(updated).hasSize(1);
        assertThat(updated.iterator().next().getItemStack().getType()).isEqualTo(Material.COBBLESTONE);
    }

    @Test
    void chestDisplaysSitInFrontOfTheLatch() {
        assertThat(com.jamesdpeters.chestsplusplus.display.DisplayLayout.shapeOf(ItemStack.of(Material.COBBLESTONE)))
                .isEqualTo(com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape.BLOCK);
        assertThat(com.jamesdpeters.chestsplusplus.display.DisplayLayout.shapeOf(ItemStack.of(Material.DIAMOND)))
                .isEqualTo(com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape.FLAT);
        assertThat(com.jamesdpeters.chestsplusplus.display.DisplayLayout.shapeOf(ItemStack.of(Material.TORCH)))
                .isEqualTo(com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape.FLAT);
        // Solid block, but its item is a flat 2D icon.
        assertThat(com.jamesdpeters.chestsplusplus.display.DisplayLayout.shapeOf(ItemStack.of(Material.HOPPER)))
                .isEqualTo(com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape.FLAT);
        assertThat(com.jamesdpeters.chestsplusplus.display.DisplayLayout.shapeOf(ItemStack.of(Material.OAK_LOG)))
                .isEqualTo(com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape.BLOCK);
        assertThat(com.jamesdpeters.chestsplusplus.display.DisplayLayout.shapeOf(ItemStack.of(Material.STONE_SLAB)))
                .isEqualTo(com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape.BLOCK);

        sign(alice, chestAt(0, 0), "[ChestLink]", "g"); // display on the north face (z = 0 side)
        ChestLinkGroup group = group(alice, "g");
        DisplayService displays = plugin.services().get(DisplayService.class);

        group.inventory().addItem(ItemStack.of(Material.DIAMOND, 10));
        displays.requestUpdate(group);
        server.getScheduler().performTicks(10);
        double flatZ = displayZ();

        group.inventory().clear();
        group.inventory().addItem(ItemStack.of(Material.COBBLESTONE, 10));
        displays.requestUpdate(group);
        server.getScheduler().performTicks(10);
        double blockZ = displayZ();

        // North face: the chest body front is at z = 1/16. Blocks are centred on it (half inset); flat items sit
        // further
        // out, around the latch (depth tuned in DisplayLayout).
        assertThat(blockZ).isCloseTo(1.0 / 16, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(flatZ).isLessThan(blockZ);
    }

    private double displayZ() {
        var displays = world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class);
        assertThat(displays).hasSize(1);
        return displays.iterator().next().getLocation().getZ();
    }

    private void reloadPlugin() {
        try {
            plugin.reload();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
