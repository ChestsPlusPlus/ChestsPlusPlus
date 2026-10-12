package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Command bodies that only run once the plugin is enabled, and for players where they need one, so every command refuses alike. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class CommandBodies {

    private final Supplier<@Nullable Services> services;

    @FunctionalInterface
    interface EnabledBody {
        void run(Services services, CommandContext<CommandSourceStack> context);
    }

    @FunctionalInterface
    interface PlayerBody {
        void run(Services services, Player player, CommandContext<CommandSourceStack> context);
    }

    @FunctionalInterface
    interface PlayerSuggestions {
        void suggest(Services services, Player player, SuggestionsBuilder builder);
    }

    Command<CommandSourceStack> enabled(EnabledBody body) {
        return context -> {
            Services current = services(context);
            if (current == null) return 0;
            body.run(current, context);
            return Command.SINGLE_SUCCESS;
        };
    }

    Command<CommandSourceStack> player(PlayerBody body) {
        return context -> {
            if (!(context.getSource().getExecutor() instanceof Player player)) {
                Services current = services.get();
                if (current != null) current.send(context.getSource().getSender(), Message.ERROR_PLAYERS_ONLY);
                return 0;
            }
            Services current = services(context);
            if (current == null) return 0;
            body.run(current, player, context);
            return Command.SINGLE_SUCCESS;
        };
    }

    /** Suggestions for a player once the plugin is enabled. Suggestions refuse silently, with none. */
    <S> CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, PlayerSuggestions body) {
        Services current = services.get();
        if (current != null && context.getSource() instanceof CommandSourceStack source && source.getExecutor() instanceof Player player) {
            body.suggest(current, player, builder);
        }
        return builder.buildFuture();
    }

    /** The live services, or null after telling the sender the plugin is not enabled. */
    @Nullable
    Services services(CommandContext<CommandSourceStack> context) {
        Services current = services.get();
        if (current == null) context.getSource().getSender().sendMessage(Component.text("ChestsPlusPlus is not enabled.", NamedTextColor.RED));
        return current;
    }
}
