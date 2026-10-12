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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** The {@code /chestlink} and {@code /autocraft} trees; one builder for both group types. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class GroupCommands {

    private static final int TARGET_RANGE = 6;

    private final GroupType type;
    private final CommandBodies bodies;

    LiteralCommandNode<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(name)
                .executes(bodies.player((services, p, context) -> services.get(UiService.class).openHub(p, type, "", 0)))
                .then(literal("add").requires(permission(Permissions.create(type))).then(group().executes(bodies.player(this::add))))
                .then(literal("remove").requires(permission(Permissions.remove(type)))
                        .then(group().executes(withGroup((actions, p, g, context) -> actions.remove(p, g)))))
                .then(literal("open").requires(permission(Permissions.remote(type)))
                        .then(group().executes(withGroup((actions, p, g, context) -> actions.openRemote(p, g)))))
                .then(literal("menu").requires(permission(Permissions.menu(type)))
                        .executes(bodies.player((services, p, context) -> services.get(UiService.class).openHub(p, type, "", 0))))
                .then(literal("list").executes(bodies.player((services, p, context) -> services.get(GroupActions.class).list(p, type))))
                .then(literal("rename").then(group().then(argument("new", StringArgumentType.string()).executes(withGroup(this::rename)))))
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
                .then(literal("list").then(group().executes(withGroup((actions, p, g, context) -> actions.listMembers(p, g)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> sort() {
        RequiredArgumentBuilder<CommandSourceStack, String> mode = argument("mode", StringArgumentType.word()).suggests((context, builder) -> {
            for (SortMode sortMode : SortMode.values()) builder.suggest(sortMode.name().toLowerCase(Locale.ROOT));
            return builder.buildFuture();
        });
        return literal("sort").requires(permission(Permissions.CHESTLINK_SORT)).then(group().then(mode.executes(withGroup(this::sort))));
    }

    private void add(Services services, Player player, CommandContext<CommandSourceStack> context) {
        Block target = player.getTargetBlockExact(TARGET_RANGE);
        if (target == null) {
            services.send(player, Message.ERROR_INVALID_BLOCK, Messages.text("type", type.displayName()));
            return;
        }
        String group = StringArgumentType.getString(context, "group");
        services.get(LinkService.class).link(player, type, group, target, NodeListener.facingFor(player.getTargetBlockFace(TARGET_RANGE), player),
                false);
    }

    private void rename(GroupActions actions, Player player, StorageGroup group, CommandContext<CommandSourceStack> context) {
        actions.rename(player, group, StringArgumentType.getString(context, "new"));
    }

    private void addMember(GroupActions actions, Player player, StorageGroup group, CommandContext<CommandSourceStack> context) {
        actions.addMember(player, group, StringArgumentType.getString(context, "player"), () -> {});
    }

    private void removeMember(GroupActions actions, Player player, StorageGroup group, CommandContext<CommandSourceStack> context) {
        actions.removeMember(player, group, StringArgumentType.getString(context, "player"), () -> {});
    }

    private void setPublic(GroupActions actions, Player player, StorageGroup group, CommandContext<CommandSourceStack> context) {
        actions.setPublic(player, group, BoolArgumentType.getBool(context, "public"));
    }

    private void sort(GroupActions actions, Player player, StorageGroup group, CommandContext<CommandSourceStack> context) {
        SortMode mode;
        try {
            mode = SortMode.valueOf(StringArgumentType.getString(context, "mode").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Sort modes: off, name, amount_asc, amount_desc", NamedTextColor.RED));
            return;
        }
        if (group instanceof ChestLinkGroup chest) actions.sort(player, chest, mode);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> group() {
        return argument("group", new GroupArgument(type, bodies));
    }

    @FunctionalInterface
    interface GroupBody {
        void run(GroupActions actions, Player player, StorageGroup group, CommandContext<CommandSourceStack> context);
    }

    /** A player command body that first resolves the {@code group} argument (with access checks). */
    private Command<CommandSourceStack> withGroup(GroupBody body) {
        return bodies.player((services, p, context) -> {
            GroupActions actions = services.get(GroupActions.class);
            StorageGroup group = actions.find(p, type, StringArgumentType.getString(context, "group"));
            if (group != null) body.run(actions, p, group, context);
        });
    }
}
