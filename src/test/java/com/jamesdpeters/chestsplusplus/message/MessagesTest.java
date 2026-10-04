package com.jamesdpeters.chestsplusplus.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class MessagesTest {

    private static InputStream bundled() {
        return Objects.requireNonNull(MessagesTest.class.getResourceAsStream("/messages.yml"));
    }

    @Test
    void everyKeyHasABundledDefault() throws Exception {
        Messages messages = Messages.load(bundled(), null);

        for (Message message : Message.values()) {
            assertThat(messages.plain(message)).as(message.key()).isNotNull();
        }
    }

    @Test
    void placeholdersAreEscapedAndPrefixResolves() throws Exception {
        Messages messages = Messages.load(bundled(), null);

        String text = messages.plain(Message.CHESTLINK_CREATED, Messages.text("group", "<red>evil"));

        assertThat(text).isEqualTo("[C++] Created ChestLink <red>evil.");
    }

    @Test
    void userFileOverridesSingleKeys(@TempDir Path dir) throws Exception {
        File override = dir.resolve("messages.yml").toFile();
        Files.writeString(override.toPath(), "chestlink:\n  created: \"made <group>\"\n");

        Messages messages = Messages.load(bundled(), override);

        assertThat(messages.plain(Message.CHESTLINK_CREATED, Messages.text("group", "g"))).isEqualTo("made g");
        assertThat(messages.plain(Message.CHESTLINK_REMOVED, Messages.text("group", "g"))).startsWith("[C++] Removed ChestLink g");
    }
}
