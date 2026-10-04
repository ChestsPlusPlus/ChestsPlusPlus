package com.jamesdpeters.chestsplusplus.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class AccessServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private final TrustService trust = new TrustService();
    private final GroupRegistry registry = new GroupRegistry();
    private final AccessService access = new AccessService(trust, registry);
    private final ChestLinkGroup group = new ChestLinkGroup(1, OWNER, "g", 0);

    @Test
    void ownerAndBypassOnlyByDefault() {
        assertThat(access.canAccess(OWNER, false, group)).isTrue();
        assertThat(access.canAccess(OTHER, false, group)).isFalse();
        assertThat(access.canAccess(OTHER, true, group)).isTrue();
        assertThat(access.canManage(OTHER, false, group)).isFalse();
        assertThat(access.canManage(OTHER, true, group)).isTrue();
    }

    @Test
    void publicMemberAndTrustGrantUseButNotManage() {
        group.setPublic(true);
        assertThat(access.canAccess(OTHER, false, group)).isTrue();
        group.setPublic(false);

        registry.add(group);
        registry.addMember(group, OTHER);
        assertThat(access.canAccess(OTHER, false, group)).isTrue();
        registry.removeMember(group, OTHER);
        assertThat(access.canAccess(OTHER, false, group)).isFalse();

        trust.trust(OWNER, OTHER);
        assertThat(access.canAccess(OTHER, false, group)).isTrue();
        assertThat(access.canManage(OTHER, false, group)).isFalse();
    }

    @Test
    void trustIndexesBothDirectionsAndNotifies() {
        List<UUID> changed = new ArrayList<>();
        trust.onChange(changed::add);

        assertThat(trust.trust(OWNER, OTHER)).isTrue();
        assertThat(trust.trust(OWNER, OTHER)).isFalse();
        assertThat(trust.trust(OWNER, OWNER)).isFalse();
        assertThat(trust.ownersTrusting(OTHER)).containsExactly(OWNER);
        assertThat(trust.untrust(OWNER, OTHER)).isTrue();
        assertThat(trust.ownersTrusting(OTHER)).isEmpty();
        assertThat(changed).containsExactly(OWNER, OWNER);
    }
}
