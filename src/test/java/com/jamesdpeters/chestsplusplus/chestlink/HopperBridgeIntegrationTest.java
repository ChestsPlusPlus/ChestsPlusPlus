package com.jamesdpeters.chestsplusplus.chestlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.persistence.Persistence;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HopperBridgeIntegrationTest extends PluginTestBase {

    private World world;
    private PlayerMock alice;
    private Block chest;
    private ChestLinkGroup group;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        world.loadChunk(0, 0);
        world.loadChunk(0, -1);
        chest = place(Material.CHEST, 0);
        group = link(chest, "dst");
    }

    private ChestLinkGroup link(Block block, String name) {
        Block sign = block.getRelative(BlockFace.NORTH);
        sign.setType(Material.OAK_WALL_SIGN);
        WallSign data = (WallSign) sign.getBlockData();
        data.setFacing(BlockFace.NORTH);
        sign.setBlockData(data);
        server.getPluginManager()
                .callEvent(new SignChangeEvent(sign, alice,
                        List.of(Component.text("[ChestLink]"), Component.text(name), Component.empty(), Component.empty()), Side.FRONT));
        return (ChestLinkGroup) plugin.services().groups().find(GroupType.CHESTLINK, alice.getUniqueId(), name);
    }

    /** MockBukkit reports every block inventory's location as 0,5,0, so the linked chest has to sit there. */
    private Block place(Material type, int x) {
        Block block = world.getBlockAt(x, 5, 0);
        block.setType(type);
        return block;
    }

    private static Inventory inventoryOf(Block block) {
        return ((Container) block.getState(false)).getInventory();
    }

    private InventoryMoveItemEvent push(Inventory source, ItemStack item) {
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, item, inventoryOf(chest), true);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void fillGroup() {
        Inventory inventory = group.inventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.STONE, 64));
    }

    private Collection<Item> droppedItems() {
        return world.getEntitiesByClass(Item.class);
    }

    @Test
    void dropperPushIsMovedIntoTheGroupByHand() {
        Inventory dropper = inventoryOf(place(Material.DROPPER, 2));
        dropper.addItem(new ItemStack(Material.DIRT, 3));

        InventoryMoveItemEvent event = push(dropper, new ItemStack(Material.DIRT));

        assertThat(event.isCancelled()).isTrue();
        assertThat(group.inventory().contains(Material.DIRT, 1)).isTrue();
        assertThat(dropper.contains(Material.DIRT, 2)).isTrue();
    }

    @Test
    void dropperPushRefreshesTheDisplay() {
        Inventory dropper = inventoryOf(place(Material.DROPPER, 2));
        dropper.addItem(new ItemStack(Material.DIRT, 3));
        // Linking queues its own display update; let it run first so it can't show the pushed item.
        server.getScheduler().performTicks(10);

        push(dropper, new ItemStack(Material.DIRT));
        server.getScheduler().performTicks(10);

        assertThat(world.getEntitiesByClass(ItemDisplay.class)).singleElement()
                .satisfies(display -> assertThat(display.getItemStack().getType()).isEqualTo(Material.DIRT));
    }

    @Test
    void dropperKeepsItsItemWhenTheGroupIsFull() {
        Inventory dropper = inventoryOf(place(Material.DROPPER, 2));
        dropper.addItem(new ItemStack(Material.DIRT, 3));
        fillGroup();

        InventoryMoveItemEvent event = push(dropper, new ItemStack(Material.DIRT));
        server.getScheduler().performTicks(1);

        assertThat(event.isCancelled()).isTrue();
        assertThat(dropper.contains(Material.DIRT, 3)).isTrue();
        assertThat(group.inventory().contains(Material.DIRT)).isFalse();
        assertThat(droppedItems()).isEmpty();
    }

    /** A lock plugin that cancels late, after the dropper push has already been taken over. */
    static final class LateLock implements Listener {
        private final Inventory locked;

        LateLock(Inventory locked) {
            this.locked = locked;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        void onMove(InventoryMoveItemEvent event) {
            if (event.getDestination().equals(locked)) event.setCancelled(true);
        }
    }

    @Test
    void aLateLockOnTheLinkedChestStillStopsADropperPush() {
        Inventory dropper = inventoryOf(place(Material.DROPPER, 2));
        dropper.addItem(new ItemStack(Material.DIRT, 3));
        server.getPluginManager().registerEvents(new LateLock(inventoryOf(chest)), plugin);

        InventoryMoveItemEvent event = push(dropper, new ItemStack(Material.DIRT));

        assertThat(event.isCancelled()).isTrue();
        assertThat(dropper.contains(Material.DIRT, 3)).isTrue();
        assertThat(group.inventory().contains(Material.DIRT)).isFalse();
    }

    @Test
    void crafterPushGoesAheadAndIsAbsorbedNextTick() {
        Inventory crafter = inventoryOf(place(Material.CRAFTER, 2));
        crafter.setItem(0, new ItemStack(Material.OAK_LOG));
        ItemStack[] grid = crafter.getContents();

        InventoryMoveItemEvent event = push(crafter, new ItemStack(Material.OAK_PLANKS, 4));

        assertThat(event.isCancelled()).isFalse();
        assertThat(crafter.getContents()).isEqualTo(grid);
        assertThat(group.inventory().contains(Material.OAK_PLANKS)).isFalse();

        inventoryOf(chest).addItem(event.getItem());
        server.getScheduler().performTicks(1);

        assertThat(group.inventory().contains(Material.OAK_PLANKS, 4)).isTrue();
        assertThat(inventoryOf(chest).isEmpty()).isTrue();
        assertThat(plugin.services().groupStore().isDirty(group)).isTrue();
    }

    @Test
    void crafterOverflowIntoAFullGroupIsDroppedOnce() {
        Inventory crafter = inventoryOf(place(Material.CRAFTER, 2));
        fillGroup();

        for (int press = 0; press < 2; press++) inventoryOf(chest).addItem(push(crafter, new ItemStack(Material.OAK_PLANKS, 4)).getItem());
        server.getScheduler().performTicks(2);

        assertThat(droppedItems()).singleElement()
                .satisfies(item -> assertThat(item.getItemStack()).isEqualTo(new ItemStack(Material.OAK_PLANKS, 8)));
        assertThat(group.inventory().contains(Material.OAK_PLANKS)).isFalse();
        assertThat(Arrays.stream(inventoryOf(chest).getContents()).allMatch(stack -> stack == null || stack.isEmpty())).isTrue();
    }

    @Test
    void whileChestLinksAreDisabledNothingMovesInOrOut() {
        group.inventory().addItem(new ItemStack(Material.EMERALD, 2));
        Block hopper = world.getBlockAt(0, 4, 0);
        hopper.setType(Material.HOPPER);
        Inventory dropper = inventoryOf(place(Material.DROPPER, 2));
        dropper.addItem(new ItemStack(Material.DIRT, 3));
        Inventory crafter = inventoryOf(place(Material.CRAFTER, 4));
        reconfigure("features.chestlinks", false);

        for (ContainerType type : ContainerType.values()) {
            HopperInventorySearchEvent search = new HopperInventorySearchEvent(inventoryOf(chest), type, hopper, chest);
            server.getPluginManager().callEvent(search);
            assertThat(search.getInventory()).isNull();
        }
        assertThat(push(dropper, new ItemStack(Material.DIRT)).isCancelled()).isTrue();
        assertThat(push(crafter, new ItemStack(Material.OAK_PLANKS, 4)).isCancelled()).isTrue();
        server.getScheduler().performTicks(1);

        assertThat(dropper.contains(Material.DIRT, 3)).isTrue();
        assertThat(group.inventory().contains(Material.DIRT)).isFalse();
        assertThat(group.inventory().contains(Material.EMERALD, 2)).isTrue();
        assertThat(inventoryOf(chest).isEmpty()).isTrue();

        reconfigure("features.chestlinks", true);
        search(hopper, ContainerType.SOURCE);
    }

    @Test
    void idleHopperDoesNotSaveTheGroupButATransferWithoutAMoveEventDoes() throws InterruptedException {
        Persistence persistence = plugin.services().persistence();
        Block hopper = world.getBlockAt(0, 4, 0);
        hopper.setType(Material.HOPPER);
        persistence.flush();
        tickUntil(() -> persistence.pending() == 0);

        search(hopper, ContainerType.SOURCE);
        persistence.flush();
        tickUntil(() -> persistence.pending() == 0);

        search(hopper, ContainerType.SOURCE);
        persistence.flush();
        assertThat(persistence.pending()).isZero();

        search(hopper, ContainerType.DESTINATION);
        group.inventory().addItem(new ItemStack(Material.DIRT));
        persistence.flush();
        assertThat(persistence.pending()).isEqualTo(1);
    }

    private void search(Block hopper, ContainerType type) {
        HopperInventorySearchEvent event = new HopperInventorySearchEvent(inventoryOf(chest), type, hopper, chest);
        server.getPluginManager().callEvent(event);
        assertThat(event.getInventory()).isSameAs(group.inventory());
    }

    private void tickUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        assertThat(done.getAsBoolean()).as("condition reached within 10s").isTrue();
    }
}
