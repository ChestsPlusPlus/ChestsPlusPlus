package com.jamesdpeters.chestsplusplus.command;

import static com.jamesdpeters.chestsplusplus.command.Commands.argument;
import static com.jamesdpeters.chestsplusplus.command.Commands.literal;
import static com.jamesdpeters.chestsplusplus.command.Commands.permission;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.migration.V2Migration;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/** {@code /chestsplusplus migrate v2 ...}: the v2 import and the hopper-filter conversion. Works from the console. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MigrateCommands {

    private static final int DEFAULT_CLEANUP_RADIUS = 4;
    private static final int MAX_CLEANUP_RADIUS = 32;

    private final CommandBodies bodies;

    LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("migrate")
                .requires(permission(Permissions.ADMIN_MIGRATE))
                .then(literal("v2")
                        .executes(run((migration, context) -> migration.importFile(sender(context), false, null)))
                        .then(literal("file").then(fileArgument(false)))
                        .then(literal("confirm")
                                .executes(run((migration, context) -> migration.importFile(sender(context), true, null)))
                                .then(literal("file").then(fileArgument(true))))
                        .then(literal("status").executes(run((migration, context) -> migration.status(sender(context)))))
                        .then(literal("cleanup")
                                .executes(cleanup(context -> DEFAULT_CLEANUP_RADIUS))
                                .then(argument("radius", IntegerArgumentType.integer(0, MAX_CLEANUP_RADIUS))
                                        .executes(cleanup(context -> IntegerArgumentType.getInteger(context, "radius")))))
                        .then(filters()));
    }

    private LiteralArgumentBuilder<CommandSourceStack> filters() {
        return literal("filters")
                .then(literal("on-load").executes(run((migration, context) -> migration.filtersOnLoad(sender(context)))))
                .then(literal("convert-all")
                        .executes(run((migration, context) -> migration.convertAll(sender(context), null)))
                        .then(argument("world", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Bukkit.getWorlds().stream().map(World::getName).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(run((migration, context) -> migration.convertAll(sender(context),
                                        StringArgumentType.getString(context, "world"))))))
                .then(literal("cancel").executes(run((migration, context) -> migration.cancelConversion(sender(context)))))
                .then(literal("dismiss").executes(run((migration, context) -> migration.filtersDismiss(sender(context)))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> fileArgument(boolean apply) {
        return argument("file", StringArgumentType.greedyString())
                .executes(run((migration, context) -> migration.importFile(sender(context), apply, StringArgumentType.getString(context, "file"))));
    }

    private Command<CommandSourceStack> cleanup(ToIntFunction<CommandContext<CommandSourceStack>> radius) {
        return bodies.player((components, player, context) -> components.migration().v2().cleanupNear(player, radius.applyAsInt(context)));
    }

    private Command<CommandSourceStack> run(BiConsumer<V2Migration, CommandContext<CommandSourceStack>> body) {
        return bodies.enabled((components, context) -> body.accept(components.migration().v2(), context));
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender();
    }
}
