package com.jamesdpeters.chestsplusplus.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Runs under MockBukkit because the order depends on owner names. */
class AccessibleGroupsIntegrationTest extends PluginTestBase {

    private long nextId = 1;

    @Test
    void ownGroupsFirstThenByOwnerNameThenGroupName() {
        PlayerMock me = server.addPlayer("Me");
        PlayerMock zed = server.addPlayer("zed");
        PlayerMock amy = server.addPlayer("Amy");
        add(zed.getUniqueId(), "a");
        add(amy.getUniqueId(), "b");
        add(me.getUniqueId(), "z");
        add(amy.getUniqueId(), "A");
        add(me.getUniqueId(), "y");

        assertThat(plugin.services().access().accessibleGroups(me.getUniqueId(), true, GroupType.CHESTLINK).groups()).map(StorageGroup::name)
                .containsExactly("y", "z", "A", "b", "a");
    }

    @Test
    void carriesEachOwnersResolvedNameAndCommandReferences() {
        PlayerMock me = server.addPlayer("Me");
        PlayerMock amy = server.addPlayer("Amy");
        add(amy.getUniqueId(), "b");
        add(amy.getUniqueId(), "c");
        add(me.getUniqueId(), "a");

        AccessibleGroups accessible = plugin.services().access().accessibleGroups(me.getUniqueId(), true, GroupType.CHESTLINK);

        assertThat(accessible.ownerNames()).containsOnly(entry(me.getUniqueId(), "Me"), entry(amy.getUniqueId(), "Amy"));
        assertThat(accessible.groups()).map(accessible::reference).containsExactly("a", "Amy:b", "Amy:c");
    }

    private void add(UUID owner, String name) {
        plugin.services().groups().add(new ChestLinkGroup(nextId++, owner, name, 0));
    }
}
