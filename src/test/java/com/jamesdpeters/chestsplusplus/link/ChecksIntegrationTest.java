package com.jamesdpeters.chestsplusplus.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Refusal;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.List;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ChecksIntegrationTest extends PluginTestBase {

    private Checks checks;
    private PlayerMock alice;
    private PlayerMock bob;
    private ChestLinkGroup group;

    @BeforeEach
    void setUp() {
        checks = plugin.services().get(Checks.class);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        group = new ChestLinkGroup(1, alice.getUniqueId(), "loot", 0);
    }

    /** The refusal as the player would read it, so the test sees the placeholders it carries. */
    private String plain(Refusal refusal) {
        return plugin.services().messages().plain(refusal.message(), refusal.placeholders());
    }

    private static @Nullable Message message(@Nullable Refusal refusal) {
        return refusal == null ? null : refusal.message();
    }

    @Test
    void enabledRefusesADisabledFeature() {
        assertThat(checks.enabled(GroupType.CHESTLINK)).isNull();

        reconfigure("features.chestlinks", false);

        assertThat(message(checks.enabled(GroupType.CHESTLINK))).isEqualTo(Message.ERROR_FEATURE_DISABLED);
        assertThat(checks.enabled(GroupType.AUTOCRAFT)).isNull();
    }

    @Test
    void permissionRefusesAMissingPermission() {
        assertThat(checks.permission(alice, Permissions.TRUST)).isNull();

        alice.addAttachment(plugin).setPermission(Permissions.TRUST, false);

        assertThat(message(checks.permission(alice, Permissions.TRUST))).isEqualTo(Message.ERROR_NO_PERMISSION);
    }

    @Test
    void allowedWorldRefusesABlacklistedWorld() {
        World nether = server.addSimpleWorld("nether");
        assertThat(checks.allowedWorld(nether)).isNull();

        reconfigure("worlds.blacklist", List.of("nether"));

        assertThat(message(checks.allowedWorld(nether))).isEqualTo(Message.ERROR_WORLD_BLACKLISTED);
    }

    @Test
    void useAndManageRefuseWithTheGroupName() {
        assertThat(checks.use(alice, group)).isNull();
        assertThat(checks.manage(alice, group)).isNull();

        Refusal use = checks.use(bob, group);
        Refusal manage = checks.manage(bob, group);

        assertThat(message(use)).isEqualTo(Message.ERROR_NO_ACCESS);
        assertThat(plain(use)).contains("loot");
        assertThat(message(manage)).isEqualTo(Message.ERROR_NOT_OWNER);
        assertThat(plain(manage)).contains("loot");
    }

    @Test
    void refusedSendsOnlyTheFirstRefusal() {
        Refusal first = new Refusal(Message.ERROR_NO_PERMISSION);

        boolean refused = checks.refused(alice, null, first, new Refusal(Message.ERROR_FEATURE_DISABLED));

        assertThat(refused).isTrue();
        assertThat(nextPlain(alice)).isEqualTo(plain(first));
        assertThat(nextPlain(alice)).isNull();
    }

    @Test
    void refusedSendsNothingWhenEveryCheckPasses() {
        assertThat(checks.refused(alice, null, null)).isFalse();
        assertThat(nextPlain(alice)).isNull();
    }
}
