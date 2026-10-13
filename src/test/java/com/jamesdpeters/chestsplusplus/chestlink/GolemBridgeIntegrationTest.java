package com.jamesdpeters.chestsplusplus.chestlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import com.jamesdpeters.chestsplusplus.testing.WallSigns;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import java.util.UUID;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.CopperGolem;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.CopperGolemMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class GolemBridgeIntegrationTest extends PluginTestBase {

    private World world;
    private PlayerMock alice;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        world.loadChunk(0, 0);
    }

    private ChestLinkGroup link(Block block, String name) {
        WallSigns.write(alice, block, "[ChestLink]", name);
        return (ChestLinkGroup) services().groups().find(GroupType.CHESTLINK, alice.getUniqueId(), name);
    }

    private Block place(Material type, int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(type);
        return block;
    }

    /** MockBukkit doesn't implement the golem state. */
    private static final class StatefulGolem extends CopperGolemMock {
        private State state = State.IDLE;

        StatefulGolem(ServerMock server) {
            super(server, UUID.randomUUID());
        }

        @Override
        public State getGolemState() {
            return state;
        }

        @Override
        public void setGolemState(State state) {
            this.state = state;
        }
    }

    private CopperGolem golemBeside(Block block, CopperGolem.State state, ItemStack held) {
        return golemAt(block.getLocation().clone().add(0.5, 0, 1.5), state, held);
    }

    private CopperGolem golemAt(Location location, CopperGolem.State state, ItemStack held) {
        StatefulGolem golem = new StatefulGolem(server);
        golem.setLocation(location);
        server.registerEntity(golem);
        golem.setGolemState(state);
        golem.getEquipment().setItemInMainHand(held);
        return golem;
    }

    private boolean validate(CopperGolem golem, Block block) {
        ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(golem, block);
        server.getPluginManager().callEvent(event);
        return event.isAllowed();
    }

    private void gameEvent(GameEvent type, Block block) {
        server.getPluginManager().callEvent(new GenericGameEvent(type, block.getLocation(), null, 16, false));
    }

    @Test
    void golemsAreTurnedAwayWhileChestLinksAreDisabled() {
        Block chest = place(Material.COPPER_CHEST, 0);
        ChestLinkGroup group = link(chest, "src");
        group.inventory().addItem(new ItemStack(Material.COBBLESTONE, 3));
        CopperGolem golem = golemBeside(chest, CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());
        reconfigure("features.chestlinks", false);

        assertThat(validate(golem, chest)).isFalse();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(golem.getEquipment().getItemInMainHand().isEmpty()).isTrue();
        assertThat(group.inventory().contains(Material.COBBLESTONE, 3)).isTrue();
    }

    @Test
    void golemsOnlyHeadForGroupsWithItemsToTake() {
        Block chest = place(Material.COPPER_CHEST, 0);
        ChestLinkGroup group = link(chest, "src");
        CopperGolem golem = golemBeside(chest, CopperGolem.State.IDLE, ItemStack.empty());

        assertThat(validate(golem, chest)).isFalse();
        group.inventory().addItem(new ItemStack(Material.COBBLESTONE, 3));
        assertThat(validate(golem, chest)).isTrue();
    }

    @Test
    void aLockThatRefusedFirstStaysRefused() {
        Block chest = place(Material.COPPER_CHEST, 0);
        link(chest, "src").inventory().addItem(new ItemStack(Material.COBBLESTONE, 3));
        CopperGolem golem = golemBeside(chest, CopperGolem.State.IDLE, ItemStack.empty());
        server.getPluginManager().registerEvent(ItemTransportingEntityValidateTargetEvent.class, new Listener() {}, EventPriority.LOW,
                (listener, event) -> {
                    if (event instanceof ItemTransportingEntityValidateTargetEvent validate && validate.getBlock().equals(chest))
                        validate.setAllowed(false);
                }, plugin);

        assertThat(validate(golem, chest)).isFalse();
    }

    @Test
    void deliveriesFollowVanillasMatchingRule() {
        Block chest = place(Material.CHEST, 0);
        ChestLinkGroup group = link(chest, "dst");
        CopperGolem golem = golemBeside(chest, CopperGolem.State.IDLE, new ItemStack(Material.DIRT, 16));

        assertThat(validate(golem, chest)).as("empty group").isTrue();
        group.inventory().addItem(new ItemStack(Material.COBBLESTONE, 1));
        assertThat(validate(golem, chest)).as("group without dirt").isFalse();
        golem.getEquipment().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 16));
        assertThat(validate(golem, chest)).as("group with cobblestone").isTrue();
    }

    @Test
    void fullGroupsTurnDeliveriesAway() {
        Inventory inventory = link(place(Material.CHEST, 0), "dst").inventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));

        assertThat(GolemBridge.accepts(inventory, new ItemStack(Material.COBBLESTONE))).isFalse();
    }

    @Test
    void openingALinkedCopperChestHandsTheGolemALoad() {
        Block chest = place(Material.COPPER_CHEST, 0);
        ChestLinkGroup group = link(chest, "src");
        group.inventory().addItem(new ItemStack(Material.COBBLESTONE, 40));
        CopperGolem golem = golemBeside(chest, CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());

        assertThat(validate(golem, chest)).isTrue();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(golem.getEquipment().getItemInMainHand()).isEqualTo(new ItemStack(Material.COBBLESTONE, GolemBridge.LOAD));
        assertThat(ChestLinkService.itemCount(group)).isEqualTo(40 - GolemBridge.LOAD);
    }

    @Test
    void openingALinkedChestTakesTheGolemsLoad() {
        Block chest = place(Material.CHEST, 0);
        ChestLinkGroup group = link(chest, "dst");
        CopperGolem golem = golemBeside(chest, CopperGolem.State.DROPPING_ITEM, new ItemStack(Material.DIRT, 16));

        assertThat(validate(golem, chest)).isTrue();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(golem.getEquipment().getItemInMainHand().isEmpty()).isTrue();
        assertThat(group.inventory().contains(Material.DIRT, 16)).isTrue();
    }

    @Test
    void aGolemWhoseTargetWasRevokedGetsNothing() {
        Block chest = place(Material.COPPER_CHEST, 0);
        ChestLinkGroup group = link(chest, "src");
        group.inventory().addItem(new ItemStack(Material.DIAMOND, 32));
        CopperGolem golem = golemBeside(chest, CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());
        assertThat(validate(golem, chest)).isTrue();
        server.getPluginManager().registerEvent(ItemTransportingEntityValidateTargetEvent.class, new Listener() {}, EventPriority.HIGHEST,
                (listener, event) -> {
                    if (event instanceof ItemTransportingEntityValidateTargetEvent validate && validate.getBlock().equals(chest))
                        validate.setAllowed(false);
                }, plugin);

        assertThat(validate(golem, chest)).isFalse();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(golem.getEquipment().getItemInMainHand().isEmpty()).isTrue();
        assertThat(group.inventory().contains(Material.DIAMOND, 32)).isTrue();
    }

    @Test
    void rejectingAnotherCandidatePreservesTheAllowedTarget() {
        Block chest = place(Material.COPPER_CHEST, 0);
        Block rejected = place(Material.COPPER_CHEST, 2);
        ChestLinkGroup group = link(chest, "src");
        group.inventory().addItem(new ItemStack(Material.DIAMOND, 32));
        link(rejected, "empty");
        CopperGolem golem = golemBeside(chest, CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());

        assertThat(validate(golem, chest)).isTrue();
        assertThat(validate(golem, rejected)).isFalse();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(golem.getEquipment().getItemInMainHand()).isEqualTo(new ItemStack(Material.DIAMOND, GolemBridge.LOAD));
    }

    @Test
    void aNearerGolemTheBlockRefusedGetsNothing() {
        Block chest = place(Material.COPPER_CHEST, 0);
        ChestLinkGroup group = link(chest, "src");
        group.inventory().addItem(new ItemStack(Material.DIAMOND, 32));
        CopperGolem opener = golemAt(new Location(world, -0.8, 64, 0.5), CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());
        CopperGolem refused = golemAt(new Location(world, 1.3, 64, 1.2), CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());
        server.getPluginManager().registerEvent(ItemTransportingEntityValidateTargetEvent.class, new Listener() {}, EventPriority.LOW,
                (listener, event) -> {
                    if (event instanceof ItemTransportingEntityValidateTargetEvent validate && validate.getEntity().equals(refused))
                        validate.setAllowed(false);
                }, plugin);

        assertThat(validate(opener, chest)).isTrue();
        assertThat(validate(refused, chest)).isFalse();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(refused.getEquipment().getItemInMainHand().isEmpty()).isTrue();
        assertThat(opener.getEquipment().getItemInMainHand()).isEqualTo(new ItemStack(Material.DIAMOND, GolemBridge.LOAD));
    }

    @Test
    void aGolemDeliveringToTheNextChestKeepsItsLoad() {
        Block diamonds = place(Material.CHEST, 0);
        Block iron = place(Material.CHEST, 1);
        ChestLinkGroup group = link(diamonds, "diamonds");
        group.inventory().addItem(new ItemStack(Material.DIAMOND, 1));
        CopperGolem opener = golemAt(new Location(world, -0.8, 64, 0.5), CopperGolem.State.DROPPING_ITEM, new ItemStack(Material.DIAMOND, 16));
        CopperGolem neighbour = golemAt(new Location(world, 1.3, 64, 1.2), CopperGolem.State.DROPPING_ITEM, new ItemStack(Material.IRON_INGOT, 16));

        assertThat(validate(opener, diamonds)).isTrue();
        assertThat(validate(neighbour, iron)).isTrue();
        gameEvent(GameEvent.CONTAINER_OPEN, diamonds);

        assertThat(neighbour.getEquipment().getItemInMainHand()).isEqualTo(new ItemStack(Material.IRON_INGOT, 16));
        assertThat(group.inventory().contains(Material.IRON_INGOT)).isFalse();
        assertThat(group.inventory().contains(Material.DIAMOND, 17)).isTrue();
    }

    @Test
    void aGolemThatChoseANearerUnlinkedChestIsLeftAlone() {
        Block linked = place(Material.COPPER_CHEST, 0);
        Block unlinked = place(Material.COPPER_CHEST, 1);
        link(linked, "src").inventory().addItem(new ItemStack(Material.COBBLESTONE, 40));
        CopperGolem golem = golemBeside(linked, CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());

        assertThat(validate(golem, linked)).isTrue();
        assertThat(validate(golem, unlinked)).isTrue();
        gameEvent(GameEvent.CONTAINER_OPEN, linked);

        assertThat(golem.getEquipment().getItemInMainHand().isEmpty()).isTrue();
    }

    @Test
    void idleGolemsNearbyAreLeftAlone() {
        Block chest = place(Material.COPPER_CHEST, 0);
        link(chest, "src").inventory().addItem(new ItemStack(Material.COBBLESTONE, 40));
        CopperGolem golem = golemBeside(chest, CopperGolem.State.IDLE, ItemStack.empty());

        gameEvent(GameEvent.CONTAINER_OPEN, chest);

        assertThat(golem.getEquipment().getItemInMainHand().isEmpty()).isTrue();
    }

    @Test
    void closingALinkedBlockAbsorbsWhateverLandedInIt() {
        Block chest = place(Material.CHEST, 0);
        ChestLinkGroup group = link(chest, "dst");
        Inventory physical = ((Container) chest.getState(false)).getInventory();
        physical.addItem(new ItemStack(Material.DIRT, 5));

        gameEvent(GameEvent.CONTAINER_CLOSE, chest);

        assertThat(physical.isEmpty()).isTrue();
        assertThat(group.inventory().contains(Material.DIRT, 5)).isTrue();
    }

    @Test
    void disabledFeatureTurnsGolemsAwayAndHandsNothingOver() {
        Block chest = place(Material.COPPER_CHEST, 0);
        link(chest, "src").inventory().addItem(new ItemStack(Material.COBBLESTONE, 40));
        reconfigure("features.copper-golems", false);
        CopperGolem golem = golemBeside(chest, CopperGolem.State.GETTING_NO_ITEM, ItemStack.empty());

        assertThat(validate(golem, chest)).isFalse();
        gameEvent(GameEvent.CONTAINER_OPEN, chest);
        assertThat(golem.getEquipment().getItemInMainHand().isEmpty()).isTrue();
    }

    @Test
    void unlinkedBlocksAreUntouched() {
        Block chest = place(Material.COPPER_CHEST, 0);
        CopperGolem golem = golemBeside(chest, CopperGolem.State.IDLE, ItemStack.empty());

        assertThat(validate(golem, chest)).isTrue();
    }
}
