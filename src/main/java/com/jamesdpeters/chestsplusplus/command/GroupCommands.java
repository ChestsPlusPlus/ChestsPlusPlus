package com.jamesdpeters.chestsplusplus.command;

import static com.jamesdpeters.chestsplusplus.command.Commands.argument;
import static com.jamesdpeters.chestsplusplus.command.Commands.literal;
import static com.jamesdpeters.chestsplusplus.command.Commands.permission;
import static com.jamesdpeters.chestsplusplus.command.Commands.playerArgument;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.NodeListener;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The {@code /chestlink} and {@code /autocraft} trees; one builder for both group types. */
final class GroupCommands {

    private static final int TARGET_RANGE = 6;

    private final GroupType type;
    private final Supplier<@Nullable Services> services;

    GroupCommands(GroupType type, Supplier<@Nullable Services> services) {
        this.type = type;
        this.services = services;
    }

    LiteralCommandNode<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(name)
                .executes(player((context, p) -> ui().openHub(p, type, "", 0)))
                .then(literal("add").requires(permission(Permissions.create(type))).then(group().executes(player(this::add))))
                .then(literal("remove").requires(permission(Permissions.remove(type)))
                        .then(group().executes(withGroup((context, p, g) -> actions().remove(p, g)))))
                .then(literal("open").requires(permission(Permissions.remote(type)))
                        .then(group().executes(withGroup((context, p, g) -> actions().openRemote(p, g)))))
                .then(literal("menu").requires(permission(Permissions.menu(type))).executes(player((context, p) -> ui().openHub(p, type, "", 0))))
                .then(literal("list").executes(player((context, p) -> actions().list(p, type))))
                .then(literal("rename").then(group().then(argument("new", StringArgumentType.word()).executes(withGroup(this::rename)))))
                .then(literal("public").then(group().then(argument("public", BoolArgumentType.bool()).executes(withGroup(this::setPublic)))))
                .then(members());
        if (type == GroupType.CHESTLINK) root.then(sort());
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> members() {
        return literal("members")
                .requires(permission(Permissions.members(type)))
                .then(literal("add").then(group().then(playerArgument().executes(withGroup(this::addMember)))))
                .then(literal("remove").then(group().then(playerArgument().executes(withGroup(this::removeMember)))))
                .then(literal("list").then(group().executes(withGroup((context, p, g) -> actions().listMembers(p, g)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> sort() {
        RequiredArgumentBuilder<CommandSourceStack, String> mode = argument("mode", StringArgumentType.word()).suggests((context, builder) -> {
            for (SortMode sortMode : SortMode.values()) builder.suggest(sortMode.name().toLowerCase(Locale.ROOT));
            return builder.buildFuture();
        });
        return literal("sort").requires(permission(Permissions.CHESTLINK_SORT)).then(group().then(mode.executes(withGroup(this::sort))));
    }

    private void add(CommandContext<CommandSourceStack> context, Player player) {
        Block target = player.getTargetBlockExact(TARGET_RANGE);
        if (target == null) {
            requireServices().send(player, Message.ERROR_INVALID_BLOCK, Messages.text("type", type.displayName()));
            return;
        }
        String group = StringArgumentType.getString(context, "group");
        links().link(player, type, group, target, NodeListener.facingFor(player.getTargetBlockFace(TARGET_RANGE), player), false);
    }

    private void rename(CommandContext<CommandSourceStack> context, Player player, StorageGroup group) {
        actions().rename(player, group, StringArgumentType.getString(context, "new"));
    }

    private void addMember(CommandContext<CommandSourceStack> context, Player player, StorageGroup group) {
        actions().addMember(player, group, StringArgumentType.getString(context, "player"), () -> {});
    }

    private void removeMember(CommandContext<CommandSourceStack> context, Player player, StorageGroup group) {
        actions().removeMember(player, group, StringArgumentType.getString(context, "player"), () -> {});
    }

    private void setPublic(CommandContext<CommandSourceStack> context, Player player, StorageGroup group) {
        actions().setPublic(player, group, BoolArgumentType.getBool(context, "public"));
    }

    private void sort(CommandContext<CommandSourceStack> context, Player player, StorageGroup group) {
        SortMode mode;
        try {
            mode = SortMode.valueOf(StringArgumentType.getString(context, "mode").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Sort modes: off, name, amount_asc, amount_desc", NamedTextColor.RED));
            return;
        }
        if (group instanceof ChestLinkGroup chest) actions().sort(player, chest, mode);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> group() {
        return argument("group", new GroupArgument(type, services));
    }

    private GroupActions actions() {
        return requireServices().get(GroupActions.class);
    }

    private LinkService links() {
        return requireServices().get(LinkService.class);
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
            Services current = services.get();
            if (!(context.getSource().getExecutor() instanceof Player player)) {
                if (current != null) current.send(context.getSource().getSender(), Message.ERROR_PLAYERS_ONLY);
                return 0;
            }
            if (current == null) {
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
