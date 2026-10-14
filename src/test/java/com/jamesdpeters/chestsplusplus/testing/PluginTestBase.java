package com.jamesdpeters.chestsplusplus.testing;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.Components;
import com.jamesdpeters.chestsplusplus.Features;
import com.jamesdpeters.chestsplusplus.Migration;
import com.jamesdpeters.chestsplusplus.Storage;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Base class for MockBukkit integration tests: a fresh mocked server with ChestsPlusPlus loaded for every
 * test, torn down afterwards. MockBukkit gives each loaded plugin a data folder in a temporary directory.
 */
@Tag(Tags.INTEGRATION)
@org.junit.jupiter.api.extension.ExtendWith(FailOnUnimplemented.class)
public abstract class PluginTestBase {

    protected ServerMock server;
    protected ChestsPlusPlus plugin;

    @BeforeEach
    void setUpServer() {
        server = createServer();
        plugin = MockBukkit.load(ChestsPlusPlus.class);
        // MockBukkit doesn't implement TextDisplay#setBillboard; labels are covered by E2E instead.
        plugin.getConfig().set("chestlink.display.label", false);
        plugin.getConfig().set("autocraft.display.label", false);
        plugin.getConfig().set("update-checker.enabled", false);
        plugin.getConfig().set("metrics.enabled", false);
        plugin.saveConfig();
        try {
            plugin.reload();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sets one config value and reloads, as an admin editing config.yml and running {@code /cpp reload} would. */
    protected void reconfigure(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        try {
            plugin.reload();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What the running plugin's enable built. */
    protected Components components() {
        return Objects.requireNonNull(plugin.components(), "ChestsPlusPlus is not enabled");
    }

    protected Services services() {
        return components().services();
    }

    protected Features features() {
        return components().features();
    }

    protected Storage storage() {
        return components().storage();
    }

    protected Migration migration() {
        return components().migration();
    }

    protected LinkService links() {
        return features().links();
    }

    /** A world named {@code name} whose chunks enumerate the tile entities the test creates, with chunk (0, 0) loaded. */
    protected TileEntityWorld tileWorld(String name) {
        TileEntityWorld world = new TileEntityWorld();
        world.setName(name);
        server.addWorld(world);
        world.loadChunk(0, 0);
        return world;
    }

    protected ServerMock createServer() {
        return MockBukkit.mock();
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    /** The next chat message the player received, as plain text (null if none). */
    protected static @org.jspecify.annotations.Nullable String nextPlain(org.mockbukkit.mockbukkit.entity.PlayerMock player) {
        net.kyori.adventure.text.Component message = player.nextComponentMessage();
        return message == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
    }

    /** For {@code @EnabledIf}: true when the test runtime has the 26.3 API (i.e. once mockbukkit-v26.3 is in use). */
    public static boolean api263Present() {
        try {
            Class.forName("org.bukkit.inventory.BrewingRecipe", false, PluginTestBase.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
