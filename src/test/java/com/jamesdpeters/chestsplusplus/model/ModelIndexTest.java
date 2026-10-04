package com.jamesdpeters.chestsplusplus.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class ModelIndexTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void registryFindsByCaseInsensitiveNameAndTracksMembers() {
        GroupRegistry registry = new GroupRegistry();
        ChestLinkGroup group = new ChestLinkGroup(registry.nextId(), ALICE, "Ores", 0);
        registry.add(group);
        registry.addMember(group, BOB);

        assertThat(registry.find(GroupType.CHESTLINK, ALICE, "ores")).isSameAs(group);
        assertThat(registry.find(GroupType.AUTOCRAFT, ALICE, "ores")).isNull();
        assertThat(registry.memberOf(BOB)).containsExactly(group);
        assertThat(registry.ownedBy(ALICE, GroupType.CHESTLINK)).containsExactly(group);

        registry.rename(group, "Metals");
        assertThat(registry.find(GroupType.CHESTLINK, ALICE, "ores")).isNull();
        assertThat(registry.find(GroupType.CHESTLINK, ALICE, "METALS")).isSameAs(group);

        registry.removeMember(group, BOB);
        assertThat(registry.memberOf(BOB)).isEmpty();

        registry.remove(group);
        assertThat(registry.byId(group.id())).isNull();
        assertThat(registry.ownedBy(ALICE, GroupType.CHESTLINK)).isEmpty();
    }

    @Test
    void registryRejectsDuplicateNamesAndAdvancesIds() {
        GroupRegistry registry = new GroupRegistry();
        registry.add(new ChestLinkGroup(41, ALICE, "a", 0));

        assertThat(registry.nextId()).isEqualTo(42);
        assertThatThrownBy(() -> registry.add(new ChestLinkGroup(50, ALICE, "A", 0))).isInstanceOf(IllegalStateException.class);
        registry.add(new AutoCraftGroup(51, ALICE, "a", 0));
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void nodeIndexByPositionChunkAndGroup() {
        NodeIndex index = new NodeIndex();
        BlockPos a = new BlockPos(WORLD, 1, 64, 1);
        BlockPos b = new BlockPos(WORLD, 2, 64, 1);
        BlockPos far = new BlockPos(WORLD, 100, 64, 100);
        index.put(new Node(a, BlockFace.NORTH, 1));
        index.put(new Node(b, BlockFace.NORTH, 1));
        index.put(new Node(far, BlockFace.EAST, 2));

        assertThat(index.get(a)).isNotNull();
        assertThat(index.get(WORLD, b.packed()).groupId()).isEqualTo(1);
        assertThat(index.inChunk(WORLD, a.chunkKey())).hasSize(2);
        assertThat(index.count(1)).isEqualTo(2);

        Node replaced = index.put(new Node(b, BlockFace.SOUTH, 2));
        assertThat(replaced.groupId()).isEqualTo(1);
        assertThat(index.count(1)).isEqualTo(1);
        assertThat(index.count(2)).isEqualTo(2);

        assertThat(index.removeGroup(2)).hasSize(2);
        assertThat(index.inChunk(WORLD, far.chunkKey())).isEmpty();
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void groupNameRules() {
        assertThat(GroupNames.isValid("ore_storage-2")).isTrue();
        assertThat(GroupNames.isValid("has space")).isFalse();
        assertThat(GroupNames.isValid("")).isFalse();
        assertThat(GroupNames.isValid("x".repeat(33))).isFalse();
    }
}
