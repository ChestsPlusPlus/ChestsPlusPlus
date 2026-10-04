package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.NodeListener;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.ui.UiService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The {@code /chestlink} and {@code /autocraft} trees (plan §5.10); one builder for both group types. */
final class GroupCommands {

    private static final int TARGET_RANGE = 6;

    private final GroupType type;
    private final Supplier<@Nullable Services> services;

    GroupCommands(GroupType type, Supplier<@Nullable Services> services) {
        this.type = type;
        this.services = services;
    }

    LiteralCommandNode<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).executes(player((context, p) -> ui().openHub(p, type, "", 0)))
                .then(Commands.literal("add").requires(perm(Permissions.create(type))).then(group().executes(player(this::add))))
                .then(Commands.literal("remove").requires(perm(Permissions.remove(type)))
                        .then(group().executes(withGroup((context, p, g) -> actions().remove(p, g)))))
                .then(Commands.literal("open").requires(perm(Permissions.remote(type)))
                        .then(group().executes(withGroup((context, p, g) -> actions().openRemote(p, g)))))
                .then(Commands.literal("menu").requires(perm(Permissions.menu(type))).executes(player((context, p) -> ui().openHub(p, type, "", 0))))
                .then(Commands.literal("list").executes(player((context, p) -> actions().list(p, type))))
                .then(Commands.literal("rename")
                        .then(group().then(io.papermc.paper.command.brigadier.Commands.argument("new", StringArgumentType.word())
                                .executes(withGroup((context, p, g) -> actions().rename(p, g, StringArgumentType.getString(context, "new")))))))
                .then(Commands.literal("public")
                        .then(group().then(io.papermc.paper.command.brigadier.Commands.argument("public", BoolArgumentType.bool())
                                .executes(withGroup((context, p, g) -> actions().setPublic(p, g, BoolArgumentType.getBool(context, "public")))))))
                .then(Commands.literal("members").requires(perm(Permissions.members(type))).then(Commands.literal("add")
                        .then(group().then(playerArg().executes(
                                withGroup((context, p, g) -> actions().addMember(p, g, StringArgumentType.getString(context, "player"), null))))))
                        .then(Commands.literal("remove")
                                .then(group().then(playerArg().executes(withGroup(
                                        (context, p, g) -> actions().removeMember(p, g, StringArgumentType.getString(context, "player"), null))))))
                        .then(Commands.literal("list").then(group().executes(withGroup((context, p, g) -> actions().listMembers(p, g))))));
        if (type == GroupType.CHESTLINK) {
            root.then(Commands.literal("sort").requires(perm(Permissions.CHESTLINK_SORT)).then(group()
                    .then(io.papermc.paper.command.brigadier.Commands.argument("mode", StringArgumentType.word()).suggests((context, builder) -> {
                        for (SortMode mode : SortMode.values()) {
                            builder.suggest(mode.name().toLowerCase(Locale.ROOT));
                        }
                        return builder.buildFuture();
                    }).executes(withGroup(this::sort)))));
        }
        return root.build();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void add(CommandContext<CommandSourceStack> context, Player player) {
        Services current = services.get();
        if (current == null) return;
        Block target = player.getTargetBlockExact(TARGET_RANGE);
        BlockFace face = player.getTargetBlockFace(TARGET_RANGE);
        if (target == null) {
            current.messages().send(player, Message.ERROR_INVALID_BLOCK,
                    com.jamesdpeters.chestsplusplus.message.Messages.text("type", type.displayName()));
            return;
        }
        current.get(LinkService.class).link(player, type, StringArgumentType.getString(context, "group"), target,
                NodeListener.facingFor(face, player), false);
    }

    private void sort(CommandContext<CommandSourceStack> context, Player player, StorageGroup group) {
        String raw = StringArgumentType.getString(context, "mode").toUpperCase(Locale.ROOT);
        SortMode mode;
        try {
            mode = SortMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Sort modes: off, name, amount_asc, amount_desc", NamedTextColor.RED));
            return;
        }
        if (group instanceof ChestLinkGroup chest) actions().sort(player, chest, mode);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private RequiredArgumentBuilder<CommandSourceStack, String> group() {
        return io.papermc.paper.command.brigadier.Commands.argument("group", new GroupArgument(type, services));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArg() {
        return io.papermc.paper.command.brigadier.Commands.argument("player", StringArgumentType.word()).suggests((context, builder) -> {
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static java.util.function.Predicate<CommandSourceStack> perm(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    private GroupActions actions() {
        return requireServices().get(GroupActions.class);
    }

    private UiService ui() {
        return requireServices().get(UiService.class);
    }

    private Services requireServices() {
        Services current = services.get();
        if (current == null) throw new IllegalStateException("ChestsPlusPlus is not enabled");
        return current;
    }

    /** A command body for players only, after the plugin is enabled. */
    private Command<CommandSourceStack> player(BiConsumer<CommandContext<CommandSourceStack>, Player> body) {
        return context -> {
            if (!(context.getSource().getExecutor() instanceof Player player)) {
                Services current = services.get();
                if (current != null) current.messages().send(context.getSource().getSender(), Message.ERROR_PLAYERS_ONLY);
                return 0;
            }
            if (services.get() == null) {
                player.sendMessage(Component.text("ChestsPlusPlus is not enabled.", NamedTextColor.RED));
                return 0;
            }
            body.accept(context, player);
            return Command.SINGLE_SUCCESS;
        };
    }

    @FunctionalInterface
    interface GroupBody {
        void run(CommandContext<CommandSourceStack> context, Player player, StorageGroup group);
    }

    /** A player command body that first resolves the {@code group} argument (with access checks). */
    private Command<CommandSourceStack> withGroup(GroupBody body) {
        return player((context, p) -> {
            StorageGroup group = actions().find(p, type, StringArgumentType.getString(context, "group"));
            if (group != null) body.run(context, p, group);
        });
    }
}
