package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Builds and registers the plugin's Brigadier command trees. */
public final class Commands {

    public static final String ROOT = "chestsplusplus";
    public static final List<String> ROOT_ALIASES = List.of("cpp", "c++");
    public static final String CHESTLINK = "chestlink";
    public static final List<String> CHESTLINK_ALIASES = List.of("cl");
    public static final String AUTOCRAFT = "autocraft";
    public static final List<String> AUTOCRAFT_ALIASES = List.of("ac");

    private final Supplier<@Nullable ChestsPlusPlus> plugin;
    private final Supplier<@Nullable Services> services;
    private final String version;
    private final List<LiteralCommandNode<CommandSourceStack>> roots = new ArrayList<>();

    /** @param plugin the plugin instance once it exists (commands are registered during bootstrap, before it is constructed) */
    public Commands(Supplier<@Nullable ChestsPlusPlus> plugin, String version) {
        this.plugin = plugin;
        this.services = () -> {
            ChestsPlusPlus instance = plugin.get();
            return instance == null ? null : instance.services();
        };
        this.version = version;
    }

    public void register(io.papermc.paper.command.brigadier.Commands registrar) {
        registrar.register(root(), "ChestsPlusPlus administration", ROOT_ALIASES);
        registrar.register(chestLink(), "ChestLink commands", CHESTLINK_ALIASES);
        registrar.register(autoCraft(), "AutoCraft commands", AUTOCRAFT_ALIASES);
    }

    /** All trees; used by help and by the release guard test. */
    public List<LiteralCommandNode<CommandSourceStack>> trees() {
        if (roots.isEmpty()) roots.addAll(List.of(root(), chestLink(), autoCraft()));
        return roots;
    }

    public LiteralCommandNode<CommandSourceStack> chestLink() {
        return new GroupCommands(GroupType.CHESTLINK, services).build(CHESTLINK);
    }

    public LiteralCommandNode<CommandSourceStack> autoCraft() {
        return new GroupCommands(GroupType.AUTOCRAFT, services).build(AUTOCRAFT);
    }

    /** The {@code /chestsplusplus} tree. Builds nodes only, so it can be inspected in unit tests. */
    public LiteralCommandNode<CommandSourceStack> root() {
        return literal(ROOT)
                .executes(this::help)
                .then(literal("help").executes(this::help))
                .then(literal("version").requires(permission(Permissions.ADMIN_VERSION)).executes(this::version))
                .then(literal("reload").requires(permission(Permissions.ADMIN_RELOAD)).executes(this::reload))
                .then(literal("trust")
                        .requires(permission(Permissions.TRUST))
                        .then(literal("add").then(playerArgument().executes(trustBody(true))))
                        .then(literal("remove").then(playerArgument().executes(trustBody(false))))
                        .then(literal("list").executes(this::listTrust)))
                .build();
    }

    private Command<CommandSourceStack> trustBody(boolean add) {
        return context -> {
            if (!(context.getSource().getExecutor() instanceof Player p)) return playersOnly(context);
            Services current = services(context);
            if (current == null) return 0;
            String name = StringArgumentType.getString(context, "player");
            GroupActions actions = current.get(GroupActions.class);
            if (add) actions.trust(p, name, () -> {});
            else actions.untrust(p, name, () -> {});
            return Command.SINGLE_SUCCESS;
        };
    }

    private int listTrust(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getExecutor() instanceof Player p)) return playersOnly(context);
        Services current = services(context);
        if (current != null) current.get(GroupActions.class).listTrust(p);
        return Command.SINGLE_SUCCESS;
    }

    private int version(CommandContext<CommandSourceStack> context) {
        Services current = services(context);
        if (current == null) return 0;
        current.send(context.getSource().getSender(), Message.COMMAND_VERSION, Messages.text("version", version));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        ChestsPlusPlus instance = plugin.get();
        Services current = services(context);
        if (instance == null || current == null) return 0;
        try {
            instance.reload();
        } catch (IOException | RuntimeException e) {
            context.getSource().getSender().sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            return 0;
        }
        current.send(context.getSource().getSender(), Message.COMMAND_RELOADED);
        return Command.SINGLE_SUCCESS;
    }

    /** Generated help: every command path the sender can use. */
    private int help(CommandContext<CommandSourceStack> context) {
        Services current = services(context);
        if (current == null) return 0;
        CommandSourceStack source = context.getSource();
        current.send(source.getSender(), Message.COMMAND_HELP_HEADER);
        for (String usage : usages(source)) current.send(source.getSender(), Message.COMMAND_HELP_ENTRY, Messages.text("usage", usage));
        return Command.SINGLE_SUCCESS;
    }

    /** Executable command paths visible to {@code source}, e.g. {@code chestlink rename <group> <new>}. */
    public List<String> usages(CommandSourceStack source) {
        List<String> out = new ArrayList<>();
        for (LiteralCommandNode<CommandSourceStack> tree : trees()) collect(tree, tree.getLiteral(), source, out);
        return out;
    }

    private static void collect(CommandNode<CommandSourceStack> node, String path, CommandSourceStack source, List<String> out) {
        if (!node.canUse(source)) return;
        if (node.getCommand() != null) out.add(path);
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            String part = child instanceof LiteralCommandNode<?> ? child.getName() : "<" + child.getName() + ">";
            collect(child, path + " " + part, source, out);
        }
    }

    private @Nullable Services services(CommandContext<CommandSourceStack> context) {
        Services current = services.get();
        if (current == null) context.getSource().getSender().sendMessage(Component.text("ChestsPlusPlus is not enabled.", NamedTextColor.RED));
        return current;
    }

    private int playersOnly(CommandContext<CommandSourceStack> context) {
        Services current = services.get();
        if (current != null) current.send(context.getSource().getSender(), Message.ERROR_PLAYERS_ONLY);
        return 0;
    }

    static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return io.papermc.paper.command.brigadier.Commands.literal(name);
    }

    static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return io.papermc.paper.command.brigadier.Commands.argument(name, type);
    }

    static Predicate<CommandSourceStack> permission(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    /** A {@code player} name argument that suggests online players. */
    static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return argument("player", StringArgumentType.word()).suggests((context, builder) -> {
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        });
    }
}
