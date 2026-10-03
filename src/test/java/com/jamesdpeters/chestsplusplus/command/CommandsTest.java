package com.jamesdpeters.chestsplusplus.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class CommandsTest {

    @Test
    void rootHasVersionLiteral() {
        var root = Commands.root("3.0.0-test");

        assertThat(root.getLiteral()).isEqualTo("chestsplusplus");
        assertThat(root.getChild("version")).isNotNull();
    }

    /** Release guard (plan §10.3): debug/test entry points exist only in the test-harness plugin. */
    @Test
    void treeHasNoDebugOrTestLiterals() {
        List<String> names = new ArrayList<>();
        collect(Commands.root("3.0.0-test"), names);

        assertThat(names).isNotEmpty().noneMatch(name -> name.contains("debug") || name.contains("test"));
    }

    private static void collect(CommandNode<CommandSourceStack> node, List<String> names) {
        names.add(node.getName().toLowerCase(java.util.Locale.ROOT));
        node.getChildren().forEach(child -> collect(child, names));
    }
}
