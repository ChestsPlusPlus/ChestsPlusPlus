package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

/** Builds and registers the plugin's Brigadier command trees. */
public final class Commands {

    public static final String ROOT = "chestsplusplus";
    public static final List<String> ROOT_ALIASES = List.of("cpp", "c++");

    private final Supplier<@Nullable ChestsPlusPlus> plugin;
    private final String version;

    /**
     * @param plugin the plugin instance once it exists (commands are registered during bootstrap, before it is
     *     constructed)
     */
    public Commands(Supplier<@Nullable ChestsPlusPlus> plugin, String version) {
        this.plugin = plugin;
        this.version = version;
    }

    public void register(io.papermc.paper.command.brigadier.Commands registrar) {
        registrar.register(root(), "ChestsPlusPlus administration", ROOT_ALIASES);
    }

    /** The {@code /chestsplusplus} tree. Builds nodes only, so it can be inspected in unit tests. */
    public LiteralCommandNode<CommandSourceStack> root() {
        return literal(ROOT)
                .then(literal("version")
                        .requires(source -> source.getSender().hasPermission(Permissions.ADMIN_VERSION))
                        .executes(this::version))
                .then(literal("reload")
                        .requires(source -> source.getSender().hasPermission(Permissions.ADMIN_RELOAD))
                        .executes(this::reload))
                .build();
    }

    private int version(CommandContext<CommandSourceStack> context) {
        Services services = services(context);
        if (services == null) return 0;
        services.messages()
                .send(context.getSource().getSender(), Message.COMMAND_VERSION, Messages.text("version", version));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        ChestsPlusPlus instance = plugin.get();
        Services services = services(context);
        if (instance == null || services == null) return 0;
        try {
            instance.reload();
        } catch (IOException | RuntimeException e) {
            context.getSource()
                    .getSender()
                    .sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            return 0;
        }
        services.messages().send(context.getSource().getSender(), Message.COMMAND_RELOADED);
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Services services(CommandContext<CommandSourceStack> context) {
        ChestsPlusPlus instance = plugin.get();
        Services services = instance == null ? null : instance.services();
        if (services == null) {
            context.getSource()
                    .getSender()
                    .sendMessage(Component.text("ChestsPlusPlus is not enabled.", NamedTextColor.RED));
        }
        return services;
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return io.papermc.paper.command.brigadier.Commands.literal(name);
    }
}
