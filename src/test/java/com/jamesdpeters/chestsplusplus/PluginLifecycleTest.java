package com.jamesdpeters.chestsplusplus;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class PluginLifecycleTest extends PluginTestBase {

    @Test
    void enablesCleanly() {
        assertThat(plugin.isEnabled()).isTrue();
        System.out.println("[probe] dataFolder=" + plugin.getDataFolder().getAbsolutePath());
        System.out.println("[probe] version=" + plugin.getPluginMeta().getVersion() + " main="
                + plugin.getPluginMeta().getMainClass());
    }

    @Test
    void disablesCleanly() {
        server.getPluginManager().disablePlugin(plugin);

        assertThat(plugin.isEnabled()).isFalse();
    }

    /** Placeholder for 26.3-only coverage: skipped (and reported as skipped) until mockbukkit-v26.3 exists. */
    @Test
    @EnabledIf("com.jamesdpeters.chestsplusplus.testing.PluginTestBase#api263Present")
    void runsOn263Api() {
        assertThat(api263Present()).isTrue();
    }
}
