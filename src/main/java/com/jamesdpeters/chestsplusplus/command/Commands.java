package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Builds and registers the plugin's Brigadier command trees. */
public final class Commands {

    public static final String ROOT = "chestsplusplus";
    public static final List<String> ROOT_ALIASES = List.of("cpp", "c++");

    private Commands() {}

    public static void register(io.papermc.paper.command.brigadier.Commands registrar, String version) {
        registrar.register(root(version), "ChestsPlusPlus administration", ROOT_ALIASES);
    }

    /** The {@code /chestsplusplus} tree. Pure: builds nodes only, so it can be inspected in unit tests. */
    public static LiteralCommandNode<CommandSourceStack> root(String version) {
        return io.papermc.paper.command.brigadier.Commands.literal(ROOT)
                .then(io.papermc.paper.command.brigadier.Commands.literal("version")
                        .requires(source -> source.getSender().hasPermission(Permissions.ADMIN_VERSION))
                        .executes(context -> {
                            context.getSource()
                                    .getSender()
                                    .sendMessage(Component.text("ChestsPlusPlus v" + version, NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
