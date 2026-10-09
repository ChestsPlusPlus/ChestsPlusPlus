package com.jamesdpeters.chestsplusplus.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** No server runs here, so any {@code PlayerNames} lookup would throw: every name has to come from the resolved map. */
@Tag(Tags.UNIT)
class AccessibleGroupsTest {

    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    private final ChestLinkGroup mine = new ChestLinkGroup(1, VIEWER, "mine", 0);
    private final ChestLinkGroup theirs = new ChestLinkGroup(2, OWNER, "ores", 0);
    private final AccessibleGroups accessible = new AccessibleGroups(VIEWER, List.of(mine, theirs), Map.of(VIEWER, "Me", OWNER, "Zed"));

    @Test
    void ownerNameIsTheResolvedName() {
        assertThat(accessible.ownerName(mine)).isEqualTo("Me");
        assertThat(accessible.ownerName(theirs)).isEqualTo("Zed");
    }

    @Test
    void referenceIsTheNameForOwnGroupsAndOwnerPrefixedForOthers() {
        assertThat(accessible.reference(mine)).isEqualTo("mine");
        assertThat(accessible.reference(theirs)).isEqualTo("Zed:ores");
    }

    @Test
    void filterKeepsTheResolvedNames() {
        AccessibleGroups filtered = accessible.filter(g -> g.name().equals("ores"));

        assertThat(filtered.groups()).containsExactly(theirs);
        assertThat(filtered.reference(theirs)).isEqualTo("Zed:ores");
    }
}
