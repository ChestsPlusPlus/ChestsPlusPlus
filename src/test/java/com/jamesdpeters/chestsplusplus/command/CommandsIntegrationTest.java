package com.jamesdpeters.chestsplusplus.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.CommandHostPlugin;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommandsIntegrationTest extends PluginTestBase {

    private PlayerMock player;

    @BeforeEach
    void setUp() {
        // Must happen before the first dispatch: MockBukkit fires LifecycleEvents.COMMANDS once, lazily.
        MockBukkit.loadSimple(CommandHostPlugin.class);
        player = server.addPlayer();
    }

    @ParameterizedTest
    @ValueSource(strings = {"chestsplusplus", "cpp", "c++"})
    void versionRepliesWithPermission(String root) {
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("chestsplusplus.admin.version", true);

        assertThat(server.dispatchCommand(player, root + " version")).isTrue();
        assertThat(player.nextMessage()).contains("ChestsPlusPlus v" + CommandHostPlugin.TEST_VERSION);
    }

    @Test
    void versionHiddenWithoutPermission() {
        server.dispatchCommand(player, "cpp version");

        assertThat(player.nextMessage()).doesNotContain("ChestsPlusPlus v");
    }
}
